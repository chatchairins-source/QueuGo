-- Patch the existing authoritative Delivery transition without replacing its
-- Market Trip, audit, notification, stock restoration or financial hooks.
do $migration$
declare v_definition text;
 v_old text := '(''pending'',''accepted'',''searching_rider'',''rider_assigned'',''preparing'',''ready'')';
 v_new text := '(''pending'',''accepted'',''searching_rider'',''rider_assigned'',''assigned'')';
begin
 select pg_get_functiondef('public.queuego_shop_delivery_transition(uuid,text,text,uuid)'::regprocedure) into v_definition;
 if position('cannot cancel after preparation started' in v_definition)>0 then return; end if;
 if position(v_old in v_definition)=0 or position('if p_action=''cancel'' then' in v_definition)=0 then
   raise exception 'unrecognized Delivery cancellation definition; review before migration';
 end if;
 v_definition:=replace(v_definition,v_old,v_new);
 v_definition:=replace(v_definition,'if p_action=''cancel'' then',
   'if p_action=''cancel'' then
    if v_order.preparing_at is not null or v_order.ready_at is not null then
      raise exception ''cannot cancel after preparation started'';
    end if;');
 execute v_definition;
end $migration$;
-- Existing row lock serializes cancellation with preparation; existing owner
-- authorization, cash-advance rejection and function ACLs remain authoritative.
