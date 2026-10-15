const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 const db=new PGlite();let checks=0;const q=(s,p=[])=>db.query(s,p),eq=(a,b)=>{assert.deepEqual(a,b);checks++},reject=async(s,p,re)=>{await assert.rejects(q(s,p),re);checks++},as=n=>q("select set_config('test.uid',$1,false)",[id(n)]);
 await db.exec(require('./fixtures/credit-schema.cjs')());
 const flag=async(k,v)=>q("insert into queuego_platform_rules(rule_key,value) values($1,$2::jsonb) on conflict(rule_key,effective_from) do update set value=excluded.value",['feature.'+k,JSON.stringify(v)]);
 const balance=async(u=1,k='rider')=>(await q('select * from qg_private.credit_accounts where user_id=$1 and kind=$2',[id(u),k])).rows[0];
 await as(101);eq((await q("select queuego_credit_summary('rider') s")).rows[0].s.enabled,false);
 await reject("select queuego_credit_begin_topup($1,'rider',10000)",[id(300)],/not enabled/);
 await flag('credit_rider',true);await flag('credit_merchant',true);
 await q("select queuego_credit_begin_topup($1,'rider',10000)",[id(300)]);
 await q("select queuego_credit_begin_topup($1,'rider',10000)",[id(300)]);
 await reject("select queuego_credit_begin_topup($1,'rider',11000)",[id(300)],/mismatch/);
 await as(102);await reject("select queuego_credit_begin_topup($1,'rider',10000)",[id(300)],/mismatch/);
 await as(151);await reject("select queuego_credit_summary('rider')",[],/owner/);
 const paid=async(n=300,amount=10000,live=true,status='successful',charge='chrg_isolated300')=>q('select queuego_credit_apply_charge($1,$2,$3,$4,$5,$6,$7,null,null)',[id(n),charge,amount,'thb',live,status,status==='successful']);
 await reject('select queuego_credit_apply_charge($1,$2,10000,\'thb\',false,\'successful\',true,null,null)',[id(300),'chrg_isolated300'],/mismatch/);
 await reject('select queuego_credit_apply_charge($1,$2,9999,\'thb\',true,\'successful\',true,null,null)',[id(300),'chrg_isolated300'],/mismatch/);
 await paid();await paid();eq((await balance()).cash_minor,10000);
 await paid(300,10000,true,'pending');eq((await balance()).cash_minor,10000);
 await reject('select queuego_credit_apply_charge($1,$2,10000,\'thb\',true,\'successful\',true,null,null)',[id(300),'chrg_other'],/another/);
 eq((await q("select count(*)::int n from qg_private.credit_entries where event_type='topup'")).rows[0].n,1);
 await q("insert into orders(id,order_number,status) values($1,'QT-credit-1','pending'),($2,'QT-credit-2','pending')",[id(301),id(302)]);
 await q("select qg_private.credit_hold($1,'rider',$2,7000)",[id(1),id(301)]);
 await q("select qg_private.credit_hold($1,'rider',$2,7000)",[id(1),id(301)]);
 await reject("select qg_private.credit_hold($1,'rider',$2,4000)",[id(1),id(302)],/insufficient/);
 eq((await balance()).held_cash_minor,7000);
 await q("select qg_private.credit_finish($1,'rider',false)",[id(301)]);eq((await balance()).held_cash_minor,0);eq((await balance()).cash_minor,10000);
 await q("select qg_private.credit_hold($1,'rider',$2,6000)",[id(1),id(301)]);
 await q("select qg_private.credit_finish($1,'rider',true)",[id(301)]);await q("select qg_private.credit_finish($1,'rider',true)",[id(301)]);
 eq((await balance()).cash_minor,4000);eq((await balance()).held_cash_minor,0);
 // Privileges: no app may call trusted confirmation, private ledger or another account.
 for(const fn of ['queuego_credit_apply_charge(uuid,text,bigint,text,boolean,text,boolean,text,timestamptz)','qg_private.credit_hold(uuid,text,uuid,bigint)','qg_private.credit_finish(uuid,text,boolean)','qg_private.rider_credit_order_authorized(uuid,uuid,numeric)'])eq((await q("select has_function_privilege('authenticated',$1,'execute') v",[fn])).rows[0].v,false);
 await q('set role authenticated');await reject('select * from qg_private.credit_accounts',[],/permission/);await q('reset role');
 await reject("update qg_private.credit_entries set cash_delta=999",[],/immutable/);
 await as(101);await reject('select queuego_admin_credit_accounts()',[],/admin/);
 const entry=(await q("select id from qg_private.credit_entries where event_type='capture' limit 1")).rows[0].id;
 await reject('select queuego_admin_reverse_credit($1,$2)',[entry,'Refund reason'],/admin/);
 await as(170);await q('select queuego_admin_reverse_credit($1,$2)',[entry,'Verified cancelled charge']);await q('select queuego_admin_reverse_credit($1,$2)',[entry,'Verified cancelled charge']);eq((await balance()).cash_minor,10000);
 // New-store promotional credit is separate, once only, never cash.
 await flag('credit_merchant_promo',true);
 await q('insert into shop_profiles(id,user_id) values($1,$2)',[id(350),id(51)]);
 eq((await balance(51,'merchant')).promo_minor,100000);eq((await balance(51,'merchant')).cash_minor,0);
 await q('insert into shop_profiles(id,user_id) values($1,$2)',[id(351),id(51)]);eq((await balance(51,'merchant')).promo_minor,100000);
 await q("insert into orders(id,order_number,shop_id,status,gp_amount) values($1,'QT-GP',$2,'pending',10)",[id(360),id(50)]);
 eq((await q('select credit_gp_required v from orders where id=$1',[id(360)])).rows[0].v,true);
 await q("update orders set status='preparing' where id=$1",[id(360)]);eq((await balance(51,'merchant')).held_promo_minor,1000);
 await q('update orders set gp_amount=12 where id=$1',[id(360)]);eq((await balance(51,'merchant')).held_promo_minor,1200);
 await q("update orders set status='picked_up' where id=$1",[id(360)]);eq((await balance(51,'merchant')).promo_minor,98800);eq((await balance(51,'merchant')).held_promo_minor,0);
 await q("update orders set status='in_progress' where id=$1",[id(360)]);eq((await balance(51,'merchant')).promo_minor,98800);
 await q("update orders set status='cancelled' where id=$1",[id(360)]);eq((await balance(51,'merchant')).promo_minor,98800);
 await reject('update orders set credit_gp_required=false where id=$1',[id(360)],/immutable/);
 // Credit is the only GP collection path: OFF refuses new fee-bearing checkout.
 await flag('credit_merchant',false);
 await reject("insert into orders(id,order_number,shop_id,status,gp_amount) values($1,'QT-legacy',$2,'pending',10)",[id(361),id(50)],/credit is not enabled/);
 await flag('credit_merchant',true);
 eq(Number((await q('select merchant_revenue_days(current_date,current_date) n')).rows[0].n),12);
 // The durable request stays visible even after more than ten newer payment requests.
 await q("insert into qg_private.credit_topups(id,user_id,kind,amount_minor,created_at) select gen_random_uuid(),$1,'rider',2000,now()+n*interval '1 second' from generate_series(1,12)n",[id(1)]);
 await as(101);const recovered=(await q("select queuego_credit_summary('rider',$1) s",[id(300)])).rows[0].s;
 eq(recovered.topups.some(t=>t.id===id(300)),true);
 // Passenger compliance requeue must release the actual Rider credit hold, and the
 // same Order must be able to reserve a fresh generation after reassignment.
 await flag('ride',true);
 await q("insert into qg_private.rider_document_expiries(rider_id,document_key,expires_on,recorded_by) values($1,'public_driver_license',current_date+30,$2)",[id(11),id(70)]);
 await q("insert into orders(id,order_number,customer_id,order_type,fulfillment_vertical,status,subtotal,delivery_fee,total_amount,ride_distance_km,ride_duration_minutes,ride_platform_fee,rider_delivery_payout,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,'QT-credit-requeue',$2,'service','ride','searching_rider',0,50,50,3,8,10,40,15,103,15.01,103)",[id(799),id(60)]);
 await q("insert into deliveries(order_id,status,delivery_fee) values($1,'pending',50)",[id(799)]);await q("insert into payments(order_id,amount,status,payment_method) values($1,50,'pending','cash')",[id(799)]);
 await q('select qg_dispatch_rider_offers()');await as(101);await q('select rider_claim_order($1)',[id(799)]);eq((await balance()).held_cash_minor,1000);
 await q("select qg_private.ride_order_action($1,'arrive_pickup')",[id(799)]);
 await q("update qg_private.rider_document_expiries set expires_on=current_date-1 where rider_id=$1 and document_key='public_driver_license'",[id(11)]);
 const firstRequeue=(await q("select qg_private.ride_order_action($1,'start') r",[id(799)])).rows[0].r;
 eq(firstRequeue.status,'searching_rider');eq(firstRequeue.requeued,true);eq((await balance()).held_cash_minor,0);
 eq((await q("select state,generation from qg_private.credit_holds where order_id=$1 and kind='rider'",[id(799)])).rows[0],{state:'released',generation:1});
 await q("update qg_private.rider_document_expiries set expires_on=current_date+30 where rider_id=$1 and document_key='public_driver_license'",[id(11)]);
 await q('delete from qg_rider_order_offers');await q('select qg_dispatch_rider_offers()');await q('select rider_claim_order($1)',[id(799)]);
 eq((await balance()).held_cash_minor,1000);
 eq((await q("select state,generation from qg_private.credit_holds where order_id=$1 and kind='rider'",[id(799)])).rows[0],{state:'held',generation:2});
 await q("select qg_private.ride_order_action($1,'arrive_pickup')",[id(799)]);
 await q("update qg_private.rider_document_expiries set expires_on=current_date-1 where rider_id=$1 and document_key='public_driver_license'",[id(11)]);
 await q("select qg_private.ride_order_action($1,'start')",[id(799)]);eq((await balance()).held_cash_minor,0);
 eq((await q("select state,generation from qg_private.credit_holds where order_id=$1 and kind='rider'",[id(799)])).rows[0],{state:'released',generation:2});
 await as(170);await q("update orders set status='cancelled' where id=$1",[id(799)]);
 await q("update qg_private.rider_document_expiries set expires_on=current_date+30 where rider_id=$1 and document_key='public_driver_license'",[id(11)]);
 // Losing the real Rider-credit authorization after claim + arrive_pickup must also
 // release the hold and requeue before the passenger boards.
 await q("insert into orders(id,order_number,customer_id,order_type,fulfillment_vertical,status,subtotal,delivery_fee,total_amount,ride_distance_km,ride_duration_minutes,ride_platform_fee,rider_delivery_payout,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,'QT-credit-loss',$2,'service','ride','searching_rider',0,50,50,3,8,10,40,15,103,15.01,103)",[id(798),id(60)]);
 await q("insert into deliveries(order_id,status,delivery_fee) values($1,'pending',50)",[id(798)]);await q("insert into payments(order_id,amount,status,payment_method) values($1,50,'pending','cash')",[id(798)]);
 await q('delete from qg_rider_order_offers');await q('select qg_dispatch_rider_offers()');await as(101);await q('select rider_claim_order($1)',[id(798)]);eq((await balance()).held_cash_minor,1000);
 await q("select qg_private.ride_order_action($1,'arrive_pickup')",[id(798)]);
 await flag('credit_rider',false);
 const lostCredit=(await q("select qg_private.ride_order_action($1,'start') r",[id(798)])).rows[0].r;
 eq(lostCredit.status,'searching_rider');eq(lostCredit.requeued,true);eq((await balance()).held_cash_minor,0);
 eq((await q("select state,generation from qg_private.credit_holds where order_id=$1 and kind='rider'",[id(798)])).rows[0],{state:'released',generation:1});
 await as(170);await q("update orders set status='cancelled' where id=$1",[id(798)]);await flag('credit_rider',true);
 // Real passenger claim/start/complete must reserve and capture the same fee.
 await q("insert into orders(id,order_number,customer_id,order_type,fulfillment_vertical,status,subtotal,delivery_fee,total_amount,ride_distance_km,ride_duration_minutes,ride_platform_fee,rider_delivery_payout,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude) values($1,'QT-credit-ride',$2,'service','ride','searching_rider',0,50,50,3,8,10,40,15,103,15.01,103)",[id(800),id(60)]);
 await q("insert into deliveries(order_id,status,delivery_fee) values($1,'pending',50)",[id(800)]);await q("insert into payments(order_id,amount,status,payment_method) values($1,50,'pending','cash')",[id(800)]);
 await q('select qg_dispatch_rider_offers()');await as(101);await q('select rider_claim_order($1)',[id(800)]);eq((await balance()).held_cash_minor,1000);
 for(const action of ['arrive_pickup','start','arrive_dropoff','complete'])await q('select qg_private.ride_order_action($1,$2)',[id(800),action]);
 eq((await balance()).cash_minor,9000);eq((await balance()).held_cash_minor,0);await q("select qg_private.ride_order_action($1,'complete')",[id(800)]);eq((await balance()).cash_minor,9000);
 // Cached balance must reconcile exactly to the immutable ledger, including reservations.
 eq((await q(`select count(*)::int n from qg_private.credit_accounts a left join lateral(select coalesce(sum(cash_delta),0) cash,coalesce(sum(promo_delta),0) promo,coalesce(sum(held_cash_delta),0) hc,coalesce(sum(held_promo_delta),0) hp from qg_private.credit_entries where user_id=a.user_id and kind=a.kind)e on true where (a.cash_minor,a.promo_minor,a.held_cash_minor,a.held_promo_minor) is distinct from(e.cash,e.promo,e.hc,e.hp)`)).rows[0].n,0);
 await db.close();console.log('Credit ledger: '+checks+' checks PASS');
})().catch(e=>{console.error(e.message,e.stack);process.exitCode=1});
