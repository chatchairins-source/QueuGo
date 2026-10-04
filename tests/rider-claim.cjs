const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {PGlite}=require('@electric-sql/pglite');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 const db=new PGlite();let checks=0;
 await db.exec(`CREATE SCHEMA auth;
 CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql AS $$SELECT nullif(current_setting('test.uid',true),'')::uuid$$;
 CREATE TABLE users(id uuid PRIMARY KEY,auth_user_id uuid,role text,status text);
 CREATE TABLE rider_profiles(id uuid PRIMARY KEY,user_id uuid,status text,metadata jsonb,vehicle_type text,vehicle_status text,vehicle_verified_at timestamptz,vehicle_capacity_kg numeric);
 CREATE TABLE orders(id uuid PRIMARY KEY,market_order_id uuid,rider_id uuid,customer_id uuid,shop_id uuid,order_type text,status text,fulfillment_vertical text,rider_assigned_at timestamptz,updated_at timestamptz,note text,weight numeric);
 CREATE TABLE deliveries(order_id uuid,rider_id uuid,status text,updated_at timestamptz);
 CREATE TABLE shop_profiles(id uuid,user_id uuid);
 CREATE TABLE audit_logs(user_id uuid,action text,entity_type text,entity_id uuid,description text,metadata jsonb);
 CREATE TABLE notifications(user_id uuid,title text,message text,type text,reference_id uuid);
 CREATE FUNCTION market_order_weight(p_id uuid) RETURNS numeric LANGUAGE sql AS $$SELECT weight FROM orders WHERE id=p_id$$;
 INSERT INTO users VALUES('${id(1)}','${id(101)}','rider','active'),('${id(2)}','${id(102)}','rider','active'),('${id(3)}','${id(103)}','customer','active');
 INSERT INTO rider_profiles VALUES('${id(11)}','${id(1)}','active','{"online":true,"available":true}','motorcycle','active',now(),20),('${id(12)}','${id(2)}','active','{"online":true,"available":true}','motorcycle','active',now(),20);
 INSERT INTO shop_profiles VALUES('${id(50)}','${id(51)}');`);
 await db.exec(fs.readFileSync(path.resolve(__dirname,'../QueueGo-Rider-Claim-Serialization-Migration.sql'),'utf8'));
 async function as(n){await db.query(`SELECT set_config('test.uid','${id(n)}',false)`)}
 async function seed(n,group=null,weight=1){await db.query('INSERT INTO orders VALUES($1,$2,NULL,$3,$4,$5,$6,$7,NULL,NULL,NULL,$8)',[id(n),group&&id(group),id(60),id(50),'shopping','searching_rider',group?'market':'food',weight]);await db.query('INSERT INTO deliveries VALUES($1,NULL,$2,NULL)',[id(n),'pending'])}
 async function reject(fn,n,pattern){await assert.rejects(db.query(`SELECT ${fn}($1)`,[id(n)]),pattern);checks++}
 async function status(n){return (await db.query('SELECT * FROM orders WHERE id=$1',[id(n)])).rows[0]}
 async function finish(n){await db.query("UPDATE orders SET status='completed' WHERE id=$1",[id(n)])}
 await seed(20);await as(103);await reject('rider_claim_order',20,/active online rider/);assert.equal((await status(20)).rider_id,null);checks++;
 await as(101);await db.query("UPDATE rider_profiles SET metadata='{}' WHERE id=$1",[id(11)]);await reject('rider_claim_order',20,/active online rider/);await db.query("UPDATE rider_profiles SET metadata='{"+'"online":true,"available":true'+"}' WHERE id=$1",[id(11)]);
 assert.equal((await db.query('SELECT rider_claim_order($1) result',[id(20)])).rows[0].result,'rider_assigned');checks++;
 assert.equal((await status(20)).rider_id,id(11));checks++;
 assert.equal((await db.query('SELECT rider_id FROM deliveries WHERE order_id=$1',[id(20)])).rows[0].rider_id,id(11));checks++;
 const firstNotices=(await db.query('SELECT count(*)::int n FROM notifications')).rows[0].n;assert.equal(firstNotices,2);checks++;
 await seed(21);await reject('rider_claim_order',21,/finish current order/);assert.equal((await status(21)).status,'searching_rider');checks++;
 await as(102);await reject('rider_claim_order',20,/no longer available/);assert.equal((await db.query('SELECT count(*)::int n FROM notifications')).rows[0].n,firstNotices);checks++;
 await as(101);await finish(20);await seed(30,1000,15);await seed(31,1000,10);await reject('market_rider_claim_group',30,/capacity insufficient/);assert.equal((await status(30)).rider_id,null);checks++;
 await db.query('UPDATE orders SET weight=5 WHERE market_order_id=$1',[id(1000)]);
 assert.equal((await db.query('SELECT market_rider_claim_group($1) result',[id(30)])).rows[0].result,id(1000));checks++;
 assert.equal((await db.query("SELECT count(*)::int n FROM orders WHERE market_order_id=$1 AND rider_id=$2 AND status='rider_assigned'",[id(1000),id(11)])).rows[0].n,2);checks++;
 await reject('rider_claim_order',21,/finish current order/);await seed(40,1001);await reject('market_rider_claim_group',40,/finish current order/);
 await as(102);await reject('market_rider_claim_group',31,/all shops must accept|already claimed/);
 await finish(30);await finish(31);await as(101);await db.query('SELECT rider_claim_order($1)',[id(21)]);checks++;
 await reject('market_rider_claim_group',40,/finish current order/);assert.equal((await status(40)).rider_id,null);checks++;
 console.log(JSON.stringify({checks,failures:0,scope:'isolated SQL ownership, atomic group assignment, rollback and sequential exclusivity; simultaneous connections not tested'},null,2));await db.close();
})().catch(e=>{console.error(e);process.exit(1)});
