const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 const db=new PGlite();let checks=0;
 await db.exec(require('./fixtures/rider-dispatch-schema.cjs')());
 await db.exec(
   "create function is_active_admin() returns boolean language sql as $$"+
   "select exists(select 1 from users where auth_user_id=auth.uid() and role='admin' and status='active')"+
   "$$;"+
   "insert into users(id,auth_user_id,role,status,name) values('"+id(70)+"','"+id(170)+"','admin','active','Admin');"
 );
 for(const suffix of [
   '_rider_continuous_dispatch_phase1.sql',
   '_rider_dynamic_stop_plan_phase1.sql',
   '_merchant_readiness_dispatch_eta_funding.sql',
   '_rider_cancellation_funding_settlement.sql'
 ]){
   const name=fs.readdirSync('supabase/migrations').find(n=>n.endsWith(suffix));
   if(!name)throw Error('missing migration '+suffix);
   await db.exec(fs.readFileSync('supabase/migrations/'+name,'utf8'));
 }
 const q=(s,p)=>db.query(s,p),as=uid=>q("select set_config('test.uid',$1,false)",[id(uid)]);
 const eq=(a,b)=>{assert.deepEqual(a,b);checks++};

 await q("insert into orders(id,order_number,status,rider_id,customer_id,shop_id,delivery_fee,total_amount,rider_delivery_payout) values($1,'QT-900','completed',$2,$3,$4,20,120,25)",[id(900),id(11),id(60),id(50)]);
 await q("insert into deliveries(order_id,rider_id,status,delivery_fee,delivered_at) values($1,$2,'completed',20,now())",[id(900),id(11)]);
 await q("insert into route_bundles(id,rider_id,primary_order_id,status) values($1,$2,$3,'CANCELLED')",[id(500),id(11),id(900)]);
 await q("insert into qg_private.rider_cancellation_funding(id,bundle_id,order_id,rider_id,amount,replacement_target_amount,protected_route_payout,status) values($1,$2,$3,$4,5,7,32,'reserved')",[id(800),id(500),id(900),id(11)]);

 await assert.rejects(
   q("update qg_private.rider_cancellation_funding set status='settled',settled_at=now(),settled_by=$1,settlement_reference='DIRECT' where id=$2",[id(70),id(800)]),
   /authoritative RPC/
 );checks++;

 await as(101);
 await assert.rejects(q("select qg_admin_rider_cancellation_funding_queue('reserved')"),/admin required/);checks++;
 await assert.rejects(q("select qg_admin_settle_rider_cancellation_funding($1,'BANK-001',null)",[id(800)]),/admin required/);checks++;

 let ledger=(await q("select qg_rider_cash_ledger((now() at time zone 'Asia/Bangkok')::date,(now() at time zone 'Asia/Bangkok')::date) l")).rows[0].l;
 let row=ledger.find(x=>x.order_id===id(900));
 eq(row.platform_cancellation_funding,5);
 eq(row.platform_cancellation_funding_cash,0);
 eq(row.platform_cancellation_funding_status,'reserved');

 let summary=(await q('select qg_rider_period_summary(1) s')).rows[0].s;
 eq(summary.income,25);
 eq(summary.cash_realized_income,20);
 eq(summary.pending_platform_funding,5);
 eq(summary.settled_platform_funding,0);

 await as(170);
 let queue=(await q("select qg_admin_rider_cancellation_funding_queue('reserved') q")).rows[0].q;
 eq(queue.length,1);eq(queue[0].amount,5);eq(queue[0].replacement_target_amount,7);

 let settled=(await q("select qg_admin_settle_rider_cancellation_funding($1,'BANK-001','paid externally') r",[id(800)])).rows[0].r;
 eq(settled.status,'settled');eq(settled.amount,5);eq(settled.replayed,false);
 let replay=(await q("select qg_admin_settle_rider_cancellation_funding($1,'BANK-001','retry') r",[id(800)])).rows[0].r;
 eq(replay.replayed,true);
 await assert.rejects(
   q("select qg_admin_settle_rider_cancellation_funding($1,'BANK-002',null)",[id(800)]),
   /already settled/
 );checks++;
 await assert.rejects(
   q("update qg_private.rider_cancellation_funding set amount=6 where id=$1",[id(800)]),
   /immutable/
 );checks++;
 for(const sql of [
   "update qg_private.rider_cancellation_funding set replacement_target_amount=8 where id=$1",
   "update qg_private.rider_cancellation_funding set protected_route_payout=33 where id=$1",
   "update qg_private.rider_cancellation_funding set replacement_order_id=$2 where id=$1",
   "update qg_private.rider_cancellation_funding set reason='changed' where id=$1",
   "update qg_private.rider_cancellation_funding set settlement_note='changed' where id=$1",
   "update qg_private.rider_cancellation_funding set created_at=created_at+interval '1 second' where id=$1",
   "update qg_private.rider_cancellation_funding set updated_at=updated_at+interval '1 second' where id=$1"
 ]){
   const params=sql.includes('$2')?[id(800),id(901)]:[id(800)];
   await assert.rejects(q(sql,params),/immutable/);checks++;
 }

 const funding=(await q("select * from qg_private.rider_cancellation_funding where id=$1",[id(800)])).rows[0];
 eq(funding.status,'settled');eq(funding.settled_by,id(70));eq(funding.settlement_reference,'BANK-001');
 assert(funding.settled_at);checks++;
 eq((await q("select count(*)::int n from audit_logs where entity_id=$1 and action='rider_cancellation_funding_settle'",[id(800)])).rows[0].n,1);
 const reservedAfter=(await q("select qg_admin_rider_cancellation_funding_queue('reserved') q")).rows[0].q;
 const settledAfter=(await q("select qg_admin_rider_cancellation_funding_queue('settled') q")).rows[0].q;
 eq(reservedAfter.length,0);eq(settledAfter.length,1);
 eq(settledAfter[0].funding_id,id(800));eq(settledAfter[0].settlement_reference,'BANK-001');
 assert(settledAfter[0].settled_at);checks++;

 await as(101);
 ledger=(await q('select qg_rider_cash_ledger(current_date,current_date) l')).rows[0].l;
 row=ledger.find(x=>x.order_id===id(900));
 eq(row.platform_cancellation_funding_cash,5);
 eq(row.platform_cancellation_funding_status,'settled');
 eq(row.platform_cancellation_funding_reference,'BANK-001');
 assert(row.platform_cancellation_funding_settled_at);checks++;

 summary=(await q('select qg_rider_period_summary(1) s')).rows[0].s;
 eq(summary.income,25);eq(summary.cash_realized_income,25);
 eq(summary.pending_platform_funding,0);eq(summary.settled_platform_funding,5);

 console.log(JSON.stringify({checks,failures:0,scope:'isolated Rider cancellation funding settlement: admin auth, immutable proof, idempotent settlement, Rider reserved-vs-settled cash visibility; no real payment executed'}));
 await db.close();
})().catch(e=>{console.error(e);process.exit(1)});