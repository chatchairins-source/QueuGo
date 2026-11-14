-- Read-only Production function baseline captured before the additive POS edit ledger.
-- Reference backup only: this file is not an automatically applied migration.

CREATE OR REPLACE FUNCTION public.pos_create_bill_once(p_request uuid, p_type text, p_table uuid, p_product uuid, p_note text DEFAULT ''::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_shop uuid; v_existing public.pos_request_keys%rowtype; v_order uuid;
begin
  v_shop:=public.pos_my_shop();
  if auth.uid() is null or v_shop is null or not public.pos_allowed('receive_order') or p_request is null then
    raise exception 'POS access denied';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(p_request::text,0));
  select * into v_existing from public.pos_request_keys where request_id=p_request;
  if found then
    if v_existing.shop_id<>v_shop or v_existing.actor_id<>auth.uid() then raise exception 'request key belongs to another user'; end if;
    return v_existing.order_id;
  end if;
  v_order:=public.pos_edit_bill(null,p_type,p_table,p_product,1,p_note);
  insert into public.pos_request_keys(request_id,shop_id,actor_id,order_id)
    values(p_request,v_shop,auth.uid(),v_order);
  return v_order;
end $function$
;

CREATE OR REPLACE FUNCTION public.pos_edit_bill(p_order uuid, p_type text, p_table uuid, p_product uuid, p_quantity integer, p_note text DEFAULT ''::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_shop uuid;
  v_order public.orders%rowtype;
  v_product public.products%rowtype;
  v_price numeric;
  v_id uuid;
  v_count integer;
begin
  if not public.pos_allowed('receive_order') then raise exception 'receive order permission denied'; end if;
  v_shop:=public.pos_my_shop();
  if v_shop is null then raise exception 'POS access denied'; end if;
  if p_type not in ('DINE_IN','TAKEAWAY') or p_quantity not between -99 and 99 or length(coalesce(p_note,''))>500 then
    raise exception 'invalid bill input';
  end if;

  if p_order is null then
    if p_type='DINE_IN' and p_table is not null
       and not exists(select 1 from public.pos_tables where id=p_table and shop_id=v_shop and active)
      then raise exception 'table not found';
    end if;
    if p_type='TAKEAWAY' and p_table is not null then raise exception 'takeaway has no table'; end if;
    if p_quantity<=0 or p_product is null then raise exception 'bill needs a product'; end if;

    perform set_config('queuego.pos_rpc','on',true);
    insert into public.orders(
      order_number,shop_id,order_type,sales_channel,table_id,staff_id,
      status,kitchen_status,payment_status,subtotal,total_amount,delivery_fee,gp_rate,gp_amount
    )
    values(
      public.qg_next_order_number(),v_shop,p_type,'POS',p_table,auth.uid(),
      'pending','NEW','UNPAID',0,0,0,0,0
    )
    returning id into v_id;
  else
    select * into v_order
    from public.orders
    where id=p_order and shop_id=v_shop and sales_channel='POS'
    for update;

    if not found or v_order.payment_status<>'UNPAID' or v_order.status='cancelled' then
      raise exception 'bill not editable';
    end if;
    if v_order.kitchen_status not in ('NEW','SENT_TO_KITCHEN','COOKING','READY','SERVED') then
      raise exception 'invalid kitchen state';
    end if;

    v_id:=p_order;
    if p_type<>v_order.order_type or p_table is distinct from v_order.table_id then
      raise exception 'bill identity cannot change';
    end if;
    perform set_config('queuego.pos_rpc','on',true);
  end if;

  if p_product is not null then
    select * into v_product
    from public.products
    where id=p_product and shop_id=v_shop and pos_available and available;
    if not found then raise exception 'product unavailable'; end if;
    if v_order.kitchen_status is not null and v_order.kitchen_status<>'NEW' and p_quantity<0 then
      raise exception 'sent items cannot be reduced';
    end if;

    v_price:=coalesce(v_product.pos_price,v_product.price);
    if p_quantity>0 then
      insert into public.order_items(order_id,product_id,item_name,description,quantity,unit_price,total_price)
      values(v_id,p_product,v_product.name,nullif(trim(p_note),''),p_quantity,v_price,p_quantity*v_price);
    elsif p_quantity<0 then
      if v_order.kitchen_status<>'NEW' then raise exception 'sent items cannot be reduced'; end if;

      delete from public.order_items i
      where i.id=(
        select id from public.order_items
        where order_id=v_id and product_id=p_product
          and coalesce(description,'')=trim(p_note)
        order by created_at,id limit 1 for update
      )
      and i.quantity=-p_quantity;

      if not found then
        update public.order_items i
        set quantity=i.quantity+p_quantity,
            total_price=(i.quantity+p_quantity)*i.unit_price
        where i.id=(
          select id from public.order_items
          where order_id=v_id and product_id=p_product
            and coalesce(description,'')=trim(p_note)
          order by created_at,id limit 1 for update
        )
        and i.quantity+p_quantity>0;
        if not found then raise exception 'cannot reduce this line'; end if;
      end if;
    end if;
  end if;

  select count(*),coalesce(sum(total_price),0)
    into v_count,v_price
  from public.order_items
  where order_id=v_id;

  update public.orders
  set subtotal=v_price,
      discount_amount=least(discount_amount,v_price),
      total_amount=v_price-least(discount_amount,v_price),
      kitchen_status=case
        when p_order is not null and p_quantity>0 and kitchen_status<>'NEW' then 'SENT_TO_KITCHEN'
        else kitchen_status
      end,
      updated_at=now()
  where id=v_id;

  return v_id;
end
$function$
;


-- Captured active-session predicate used by the new wrapper.
CREATE OR REPLACE FUNCTION public.check_active_session(p_session_id uuid)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
    uid uuid;
    current_session uuid;
BEGIN

    uid := auth.uid();

    IF uid IS NULL THEN
        RETURN false;
    END IF;

    SELECT session_id
    INTO current_session
    FROM public.user_active_sessions
    WHERE user_id = uid
      AND revoked_at IS NULL;

    IF current_session IS NULL THEN
        RETURN false;
    END IF;

    RETURN current_session = p_session_id;

END;
$function$
;
