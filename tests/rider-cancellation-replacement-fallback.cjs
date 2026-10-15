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
   '_rider_cancellation_funding_settlement.sql',
   '_rider_cancellation_replacement_fallback.sql'
 ]){
   const name=fs.readdirSync('supabase/migrations').find(n=>n.endsWith(suffix));
   if(!name)throw Error('missing migration '+suffix);
   await db.exec(fs.readFileSync('supabase/migrations/'+name,'utf8'));
 }
 const q=(s,p)=>db.query(s,p),as=uid=>q("select set_config('test.uid',$1,false)",[id(uid)]);
 const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 async function order(n,status='in_progress',fee=20,payout=25,next=null){
   await q("insert into orders(id,order_number,status,rider_id,customer_id,shop_id,delivery_fee,total_amount,rider_delivery_payout,next_after_order_id) values($1,$2,$3,$4,$5,$6,$7::numeric,100::numeric+$7::numeric,$8::numeric,$9)",
     [id(n),'QT-'+n,status,id(11),id(60),id(50),fee,payout,next&&id(next)]);
   await q("insert into deliveries(order_id,rider_id,status,delivery_fee) values($1,$2,'assigned',$3)",[id(n),id(11),fee]);
 }
 async function funding(fid,bid,protectedOrder,amount,target,replacement=null){
   await q("insert into route_bundles(id,rider_id,primary_order_id,status) values($1,$2,$3,'CANCELLED')",[id(bid),id(11),id(protectedOrder)]);
   await q("insert into qg_private.rider_cancellation_funding(id,bundle_id,order_id,rider_id,amount,replacement_target_amount,protected_route_payout,replacement_order_id,status) values($1,$2,$3,$4,$5,$6,$7,$8,'reserved')",
     [id(fid),id(bid),id(protectedOrder),id(11),amount,target,25+target,replacement&&id(replacement)]);
 }

 // A cancelled replacement while the protected route is still live reopens the SAME NEXT slot.
 await order(910,'in_progress',20,25);
 await order(911,'rider_assigned',7,7,910);
 await funding(810,510,910,5,7,911);
 await q("update orders set status='cancelled' where id=$1",[id(911)]);
 let f=(await q("select * from qg_private.rider_cancellation_funding where id=$1",[id(810)])).rows[0];
 eq(f.replacement_order_id,null);eq(f.replacement_finalized_at,null);eq(Number(f.amount),5);
 eq((await q("select count(*)::int n from audit_logs where entity_id=$1 and action='rider_cancellation_replacement_reopen'",[id(810)])).rows[0].n,1);

 // Settlement is forbidden while the replacement protection target is unresolved.
 await as(170);
 await assert.rejects(
   q("select qg_admin_settle_rider_cancellation_funding($1,'BANK-EARLY',null)",[id(810)]),
   /replacement protection is unresolved/
 );checks++;

 // The ordinary existing NEXT assignment fields link a second replacement; no second dispatcher.
 await as(101);
 await order(912,'searching_rider',4,4);
 await q("update orders set rider_id=$1,status='rider_assigned',next_after_order_id=$2 where id=$3",[id(11),id(910),id(912)]);
 f=(await q("select * from qg_private.rider_cancellation_funding where id=$1",[id(810)])).rows[0];
 eq(f.replacement_order_id,id(912));

 // Completing the protected route keeps the already-secured replacement linked.
 await q("update orders set status='completed' where id=$1",[id(910)]);
 f=(await q("select * from qg_private.rider_cancellation_funding where id=$1",[id(810)])).rows[0];
 eq(f.replacement_order_id,id(912));eq(f.replacement_finalized_at,null);
 eq((await q("select next_after_order_id from orders where id=$1",[id(912)])).rows[0].next_after_order_id,null);

 // Replacement earns 4 against target 7: QueueGo owes only the remaining 3,
 // added to the pre-existing customer-discount funding gap of 5.
 await q("update orders set status='completed' where id=$1",[id(912)]);
 f=(await q("select * from qg_private.rider_cancellation_funding where id=$1",[id(810)])).rows[0];
 eq(Number(f.replacement_covered_amount),4);eq(Number(f.amount),8);
 assert(f.replacement_finalized_at);checks++;eq(f.status,'reserved');

 // No replacement by protected completion: the full lost target becomes QueueGo funding.
 await order(920,'in_progress',23,25);
 await funding(820,520,920,2,6,null);
 await q("update orders set status='completed' where id=$1",[id(920)]);
 f=(await q("select * from qg_private.rider_cancellation_funding where id=$1",[id(820)])).rows[0];
 eq(Number(f.amount),8);eq(Number(f.replacement_covered_amount),0);assert(f.replacement_finalized_at);checks++;

 // Once fallback is finalized, a late NEXT assignment cannot attach itself to the
 // already-funded obligation even if it references the completed predecessor.
 await order(921,'searching_rider',9,9);
 await q("update orders set rider_id=$1,status='rider_assigned',next_after_order_id=$2 where id=$3",[id(11),id(920),id(921)]);
 f=(await q("select * from qg_private.rider_cancellation_funding where id=$1",[id(820)])).rows[0];
 eq(f.replacement_order_id,null);

 // A replacement that fully covers the target leaves no platform cash obligation and is voided.
 await order(930,'in_progress',25,25);
 await order(931,'rider_assigned',10,10,930);
 await funding(830,530,930,0,7,931);
 await q("update orders set status='completed' where id=$1",[id(930)]);
 await q("update orders set status='completed' where id=$1",[id(931)]);
 f=(await q("select * from qg_private.rider_cancellation_funding where id=$1",[id(830)])).rows[0];
 eq(Number(f.replacement_covered_amount),7);eq(Number(f.amount),0);eq(f.status,'void');assert(f.replacement_finalized_at);checks++;

 // If the linked replacement dies only after the protected route has ended,
 // there is no chained refill window left, so the full target becomes due.
 await order(940,'in_progress',24,25);
 await order(941,'rider_assigned',5,5,940);
 await funding(840,540,940,1,5,941);
 await q("update orders set status='completed' where id=$1",[id(940)]);
 await q("update orders set status='cancelled' where id=$1",[id(941)]);
 f=(await q("select * from qg_private.rider_cancellation_funding where id=$1",[id(840)])).rows[0];
 eq(Number(f.amount),6);eq(Number(f.replacement_covered_amount),0);assert(f.replacement_finalized_at);checks++;

 console.log(JSON.stringify({checks,failures:0,scope:'isolated cancellation replacement lifecycle: reopen same NEXT, unresolved settlement block, partial/full coverage and no-refill QueueGo shortfall; no Production data or real payment'}));
 await db.close();
})().catch(e=>{console.error(e);process.exit(1)});