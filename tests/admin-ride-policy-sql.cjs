'use strict';
const fs=require('fs'),assert=require('node:assert/strict'),{PGlite}=require('@electric-sql/pglite');
(async()=>{
 const db=new PGlite(),migration=fs.readFileSync('supabase/migrations/20261021110000_admin_ride_policy_controls.sql','utf8');let checks=0;
 const eq=(a,b)=>{assert.deepEqual(a,b);checks++},ok=value=>{assert(value);checks++};
 const rejected=async(sql,params,pattern)=>{await assert.rejects(()=>db.query(sql,params),pattern);checks++};
 await db.exec(`
  create role anon; create role authenticated; create role service_role;
  create schema auth; create schema qg_private;
  create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
  create table public.users(id uuid primary key,auth_user_id uuid unique,role text not null,status text not null);
  create table public.queuego_platform_rules(id uuid primary key default gen_random_uuid(),rule_key text not null,value jsonb not null,effective_from timestamptz not null,note text,created_by uuid);
  create table public.audit_logs(id uuid primary key default gen_random_uuid(),user_id uuid,action text,entity_type text,description text,metadata jsonb not null default '{}'::jsonb,created_at timestamptz not null default now());
  create function qg_private.assert_live_session() returns void language plpgsql as $$begin if auth.uid() is null then raise exception 'active session required';end if;end$$;
  create function public.queuego_rule_numeric(p_key text,p_default numeric,p_at timestamptz) returns numeric language sql stable as $$select coalesce((select (value#>>'{}')::numeric from public.queuego_platform_rules where rule_key=p_key and effective_from<=p_at order by effective_from desc limit 1),p_default)$$;
  create function public.queuego_feature_enabled(p_key text,p_at timestamptz default now()) returns boolean language sql stable as $$select coalesce((select (value#>>'{}')::boolean from public.queuego_platform_rules where rule_key='feature.'||p_key and effective_from<=p_at order by effective_from desc limit 1),false)$$;
  create function public.queuego_ride_config() returns jsonb language sql stable as $$select jsonb_build_object(
   'enabled',public.queuego_feature_enabled('ride',now()),
   'configured',public.queuego_rule_numeric('ride.base_fare',-1,now())>0
     and public.queuego_rule_numeric('ride.minimum_fare',-1,now())>0
     and public.queuego_rule_numeric('ride.long_distance_threshold_km',-1,now())>public.queuego_rule_numeric('ride.included_km',-1,now())
     and public.queuego_rule_numeric('ride.platform_fee_percent',-1,now())>=0,
   'base_fare',public.queuego_rule_numeric('ride.base_fare',-1,now()),
   'included_km',public.queuego_rule_numeric('ride.included_km',-1,now()),
   'per_km',public.queuego_rule_numeric('ride.per_km',-1,now()),
   'minimum_fare',public.queuego_rule_numeric('ride.minimum_fare',-1,now()),
   'long_distance_threshold_km',public.queuego_rule_numeric('ride.long_distance_threshold_km',-1,now()),
   'long_distance_per_km',public.queuego_rule_numeric('ride.long_distance_per_km',-1,now()),
   'platform_fee_percent',public.queuego_rule_numeric('ride.platform_fee_percent',-1,now())
  )$$;
  insert into public.users values
   ('00000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','admin','active'),
   ('00000000-0000-4000-8000-000000000002','10000000-0000-4000-8000-000000000002','customer','active');
  insert into public.queuego_platform_rules(rule_key,value,effective_from,note) values
   ('ride.base_fare','25',now()-interval '1 day','existing'),
   ('ride.included_km','2',now()-interval '1 day','existing'),
   ('ride.per_km','5',now()-interval '1 day','existing'),
   ('ride.minimum_fare','25',now()-interval '1 day','existing'),
   ('ride.long_distance_threshold_km','5',now()-interval '1 day','existing'),
   ('ride.long_distance_per_km','10',now()-interval '1 day','existing'),
   ('ride.platform_fee_percent','0',now()-interval '1 day','existing'),
   ('feature.ride','true',now()-interval '1 day','existing');
 `);
 await db.exec(migration);checks++;
 const rcp="public.queuego_admin_configure_ride(boolean,jsonb)";
 eq((await db.query("select has_function_privilege('anon',$1,'execute') anon,has_function_privilege('authenticated',$1,'execute') auth",[rcp])).rows[0],{anon:false,auth:true});
 const base={base_fare:25,included_km:2,per_km:5,minimum_fare:25,long_distance_threshold_km:5,long_distance_per_km:10,platform_fee_percent:0};
 await db.query("set request.jwt.claim.sub='10000000-0000-4000-8000-000000000002'");await db.query('set role authenticated');
 await rejected('select public.queuego_admin_configure_ride(true,$1::jsonb)',[JSON.stringify(base)],/active admin/);
 await db.query('reset role');await db.query("set request.jwt.claim.sub=''");await db.query('set role anon');
 await rejected('select public.queuego_admin_configure_ride(true,$1::jsonb)',[JSON.stringify(base)],/permission denied/);
 await db.query('reset role');await db.query("set request.jwt.claim.sub='10000000-0000-4000-8000-000000000001'");await db.query('set role authenticated');
 const full={...base,per_km:8};
 const saved=(await db.query('select public.queuego_admin_configure_ride(false,$1::jsonb) result',[JSON.stringify(full)])).rows[0].result;
 eq(saved.enabled,false);eq(saved.per_km,8);eq(saved.configured,true);checks+=3;
 await db.query('reset role');
 const logged=(await db.query("select action,metadata->>'enabled' enabled,metadata->'pricing' pricing from public.audit_logs")).rows[0];
 eq(logged.action,'ride_policy_update');eq(logged.enabled,'false');eq(logged.pricing,full);checks+=3;
 await db.query('set role authenticated');
 await db.query('reset role');await db.query("insert into public.queuego_platform_rules(rule_key,value,effective_from,note) values('ride.per_km','12',now()+interval '30 days','scheduled')");await db.query('set role authenticated');
 await rejected('select public.queuego_admin_configure_ride(true,$1::jsonb)',[JSON.stringify({...full,platform_fee_percent:1})],/existing Rider credit integration/);
 await db.query('reset role');await db.exec('create function qg_private.rider_credit_order_authorized(uuid,uuid,numeric) returns boolean language sql as $$select true$$');await db.query('set role authenticated');
 const enabled={...full,platform_fee_percent:1};
 const charged=(await db.query('select public.queuego_admin_configure_ride(true,$1::jsonb) result',[JSON.stringify(enabled)])).rows[0].result;
 eq(charged.enabled,true);eq(charged.per_km,8);eq(charged.configured,true);checks+=3;
 await db.query('reset role');
 eq((await db.query("select count(*)::int n from public.queuego_platform_rules where rule_key='ride.per_km' and note='scheduled'")).rows[0].n,1);checks++;
 await db.query('set role authenticated');
 await rejected('select public.queuego_admin_configure_ride(true,$1::jsonb)',[JSON.stringify({...base,long_distance_threshold_km:2})],/long-distance threshold/);
 await rejected('select public.queuego_admin_configure_ride(true,$1::jsonb)',[JSON.stringify({...base,platform_fee_percent:100})],/invalid Ride pricing/);
 const incomplete={...base};delete incomplete.long_distance_threshold_km;
 await rejected('select public.queuego_admin_configure_ride(true,$1::jsonb)',[JSON.stringify(incomplete)],/complete explicit Ride pricing/);
 await rejected('select public.queuego_admin_configure_ride(true,$1::jsonb)',[JSON.stringify({...base,platform_fee_percent:101})],/invalid Ride pricing/);
 await db.query('reset role');
 ok(!/delete\s+from\s+public\.queuego_platform_rules/i.test(migration));
 console.log(JSON.stringify({checks,failures:0,scope:'isolated PGlite Admin Ride policy RPC, full seven-field pricing, feature toggle, actor/anon denial, audit, future policy preservation and credit fee guard; no Production mutation'}));
 await db.close();
})().catch(e=>{console.error(e);process.exit(1)});
