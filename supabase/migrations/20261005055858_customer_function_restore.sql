-- Restore Customer readers over existing records without exposing private profiles.
CREATE OR REPLACE FUNCTION public.qg_customer_order_context(p_order_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE v_user uuid; o public.orders%rowtype; r public.rider_profiles%rowtype; s public.shop_profiles%rowtype;
BEGIN
 SELECT id INTO v_user FROM public.users WHERE auth_user_id=auth.uid() AND role='customer' AND status='active';
 IF v_user IS NULL THEN RAISE EXCEPTION 'active customer required'; END IF;
 SELECT * INTO o FROM public.orders WHERE id=p_order_id AND customer_id=v_user;
 IF NOT FOUND THEN RAISE EXCEPTION 'owned order required'; END IF;
 SELECT * INTO s FROM public.shop_profiles WHERE id=o.shop_id;
 SELECT * INTO r FROM public.rider_profiles WHERE id=o.rider_id;
 RETURN jsonb_build_object('shop',jsonb_build_object('id',s.id,'name',s.shop_name,'phone',s.phone),
  'rider',CASE WHEN r.id IS NULL THEN NULL ELSE jsonb_build_object('name',r.rider_name,'phone',r.phone,
    'vehicle_type',r.vehicle_type,'vehicle_plate',r.vehicle_plate,
    'photo',coalesce(r.metadata->>'profileImage',r.metadata->>'profile_image',r.metadata->>'avatarUrl'),
    'latitude',CASE WHEN o.status NOT IN ('completed','cancelled') THEN r.latitude END,
    'longitude',CASE WHEN o.status NOT IN ('completed','cancelled') THEN r.longitude END,
    'updated_at',CASE WHEN o.status NOT IN ('completed','cancelled') THEN r.updated_at END) END);
END $$;
REVOKE ALL ON FUNCTION public.qg_customer_order_context(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.qg_customer_order_context(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.qg_public_shop_reviews(p_shop_id uuid)
RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$
 SELECT jsonb_build_object('count',count(*),'average',coalesce(avg(r.rating),0),'items',
  coalesce((SELECT jsonb_agg(x) FROM (SELECT left(coalesce(v.customer_name,'ลูกค้า'),1)||'…' AS customer_name,
    v.rating,v.food_rating,v.rider_rating,v.comment,v.updated_at FROM public.reviews v
    JOIN public.orders vo ON vo.id=v.order_id AND vo.customer_id=v.customer_id AND vo.shop_id=v.shop_id AND vo.status='completed'
    WHERE v.shop_id=p_shop_id ORDER BY v.created_at DESC LIMIT 20) x),'[]'::jsonb))
 FROM public.reviews r JOIN public.orders o ON o.id=r.order_id AND o.customer_id=r.customer_id AND o.shop_id=r.shop_id AND o.status='completed'
 WHERE r.shop_id=p_shop_id;
$$;
REVOKE ALL ON FUNCTION public.qg_public_shop_reviews(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.qg_public_shop_reviews(uuid) TO anon,authenticated;

CREATE OR REPLACE FUNCTION public.qg_customer_save_review(p_order_id uuid,p_rating smallint,p_food_rating smallint,p_rider_rating smallint,p_comment text)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE u public.users%rowtype; o public.orders%rowtype; v_id uuid;
BEGIN
 SELECT * INTO u FROM public.users WHERE auth_user_id=auth.uid() AND role='customer' AND status='active';
 IF NOT FOUND THEN RAISE EXCEPTION 'active customer required'; END IF;
 SELECT * INTO o FROM public.orders WHERE id=p_order_id AND customer_id=u.id AND status='completed' FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'owned completed order required'; END IF;
 IF p_rating IS NULL OR p_rating NOT BETWEEN 1 AND 5 OR (p_food_rating IS NOT NULL AND p_food_rating NOT BETWEEN 1 AND 5)
  OR (p_rider_rating IS NOT NULL AND p_rider_rating NOT BETWEEN 1 AND 5) OR length(coalesce(p_comment,''))>1000 THEN RAISE EXCEPTION 'invalid review'; END IF;
 INSERT INTO public.reviews(order_id,shop_id,customer_id,customer_name,rating,food_rating,rider_rating,comment)
 VALUES(o.id,o.shop_id,u.id,u.name,p_rating,p_food_rating,p_rider_rating,p_comment)
 ON CONFLICT(order_id) DO UPDATE SET rating=excluded.rating,food_rating=excluded.food_rating,rider_rating=excluded.rider_rating,comment=excluded.comment,updated_at=now()
 WHERE reviews.customer_id=u.id AND reviews.shop_id=o.shop_id RETURNING id INTO v_id;
 IF v_id IS NULL THEN RAISE EXCEPTION 'review ownership mismatch'; END IF;
 RETURN v_id;
END $$;
REVOKE ALL ON FUNCTION public.qg_customer_save_review(uuid,smallint,smallint,smallint,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.qg_customer_save_review(uuid,smallint,smallint,smallint,text) TO authenticated;
-- Correct the old ambiguous column references; keep direct access ownership-bound.
ALTER POLICY qg_reviews_create ON public.reviews WITH CHECK (
 customer_id=public.get_my_user_id() AND EXISTS(SELECT 1 FROM public.orders o WHERE o.id=reviews.order_id AND o.customer_id=reviews.customer_id AND o.shop_id=reviews.shop_id AND o.status='completed'));
ALTER POLICY qg_reviews_update ON public.reviews USING(customer_id=public.get_my_user_id()) WITH CHECK (
 customer_id=public.get_my_user_id() AND EXISTS(SELECT 1 FROM public.orders o WHERE o.id=reviews.order_id AND o.customer_id=reviews.customer_id AND o.shop_id=reviews.shop_id AND o.status='completed'));

CREATE OR REPLACE FUNCTION public.qg_public_promotions()
RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path=public,pg_temp AS $$
 SELECT coalesce(jsonb_agg(x),'[]'::jsonb) FROM (SELECT p.id,p.shop_id,s.shop_name,s.public_cover,
 coalesce(p.metadata->>'title','โปรโมชั่นจากร้าน') AS title,coalesce(p.metadata->>'description','') AS description
 FROM public.promotions p JOIN public.shop_profiles s ON s.id=p.shop_id
 WHERE p.status='active' AND s.status='active' AND s.delivery_enabled IS NOT FALSE
 AND (p.activated_at IS NULL OR p.period_days IS NULL OR p.activated_at + make_interval(days=>p.period_days)>now())
 ORDER BY p.activated_at DESC NULLS LAST LIMIT 50) x;
$$;
REVOKE ALL ON FUNCTION public.qg_public_promotions() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.qg_public_promotions() TO anon,authenticated;

-- Reuse the existing chat notification trigger; notify the customer on rider replies.
CREATE OR REPLACE FUNCTION public.qg_notify_rider_chat()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE v_rider_user uuid; v_customer uuid;
BEGIN
 SELECT r.user_id,o.customer_id INTO v_rider_user,v_customer FROM public.orders o JOIN public.rider_profiles r ON r.id=o.rider_id WHERE o.id=NEW.order_id AND o.status<>'cancelled';
 IF v_rider_user IS NOT NULL AND NEW.sender_id<>v_rider_user THEN
  INSERT INTO public.notifications(user_id,title,message,type,reference_id) VALUES(v_rider_user,'ข้อความใหม่ในออเดอร์',CASE WHEN NEW.message LIKE '__IMG__%' THEN 'มีรูปภาพใหม่ แตะเพื่อเปิดแชต' ELSE left(NEW.message,180) END,'order',NEW.order_id);
 ELSIF v_rider_user IS NOT NULL AND NEW.sender_id=v_rider_user THEN
  INSERT INTO public.notifications(user_id,title,message,type,reference_id) VALUES(v_customer,'ข้อความใหม่จาก Rider',CASE WHEN NEW.message LIKE '__IMG__%' THEN 'Rider ส่งรูปภาพ แตะเพื่อเปิดออเดอร์' ELSE left(NEW.message,180) END,'order',NEW.order_id);
 END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.qg_notify_rider_chat() FROM PUBLIC,anon,authenticated;

-- The previous permissive participant policy must not bypass the existing post-job gate.
ALTER POLICY order_chat_messages_insert ON public.order_chat_messages WITH CHECK (
 sender_id=public.get_my_user_id() AND public.qg_chat_postjob_allowed(order_id,sender_id));
