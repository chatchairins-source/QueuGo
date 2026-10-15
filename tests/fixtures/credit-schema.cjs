const fs=require('node:fs');
module.exports=({retire=true}={})=>require('./passenger-ride-schema.cjs')()+`
 create or replace function queuego_feature_enabled(k text,t timestamptz) returns boolean language sql as $$select coalesce((select (value#>>'{}')::boolean from queuego_platform_rules where rule_key='feature.'||k and effective_from<=t order by effective_from desc limit 1),false)$$;
 create table settlements(id uuid primary key,shop_id uuid,date_key date,status text,amount numeric,metadata jsonb);
 create function admin_review_gp(uuid,boolean,text default null) returns text language sql as $$select 'completed'::text$$;
 create function report_shop_gp(p_date date,p_slip_path text) returns numeric language sql as $$select sum(o.gp_amount) from orders o cross join (select null::uuid v_shop) s where o.shop_id=v_shop$$;
 create function merchant_revenue_days(date,date) returns numeric language sql as $$select sum(coalesce(o.gp_amount,round(o.subtotal*coalesce(o.gp_rate,10)/100,2))) gp from orders o$$;
`+fs.readFileSync('supabase/migrations/20261015075733_credit_rider_merchant_omise.sql','utf8')+(retire?fs.readFileSync('supabase/migrations/20261015084536_retire_legacy_gp_collection.sql','utf8'):'');
