const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {PGlite}=require('@electric-sql/pglite');
const root=path.resolve(__dirname,'..');
const migration=fs.readFileSync(path.join(root,'supabase/migrations/20261021135000_pos_item_sales_report.sql'),'utf8');
const client=fs.readFileSync(path.join(root,'merchant/pos-table-v2.js'),'utf8');
const css=fs.readFileSync(path.join(root,'merchant/pos-table-v2.css'),'utf8');
(async()=>{
  const db=new PGlite();
  await db.exec(`create role anon; create role authenticated; create schema auth;
    create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    create table public.orders(id uuid primary key,shop_id uuid,sales_channel text,payment_status text,status text,paid_at timestamptz,created_at timestamptz default now(),subtotal numeric default 0,discount_amount numeric default 0);
    create table public.order_items(id uuid primary key,order_id uuid references public.orders(id),product_id uuid,item_name text,quantity numeric,total_price numeric);
    create function public.pos_is_owner() returns boolean language sql stable as $$ select coalesce(current_setting('qg.test.owner',true),'false')='true' $$;
    create function public.pos_my_shop() returns uuid language sql stable as $$ select nullif(current_setting('qg.test.shop',true),'')::uuid $$;`);
  await db.exec(migration);
  const shop='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',other='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
  const set=(owner,id=shop)=>db.query(`select set_config('qg.test.owner',$1,false),set_config('qg.test.shop',$2,false)`,[String(owner),id]);
  await db.exec(`insert into public.orders(id,shop_id,sales_channel,payment_status,status,paid_at,created_at,subtotal,discount_amount) values
    ('10000000-0000-4000-8000-000000000001','${shop}','POS','PAID','completed',now(),now(),100,10),
    ('10000000-0000-4000-8000-000000000002','${shop}','POS','PAID','completed',now(),now(),50,0),
    ('10000000-0000-4000-8000-000000000003','${shop}','POS','REFUNDED','cancelled',now(),now(),25,0),
    ('10000000-0000-4000-8000-000000000004','${shop}','QUEUEGO_DELIVERY','PAID','completed',now(),now(),900,0),
    ('10000000-0000-4000-8000-000000000005','${other}','POS','PAID','completed',now(),now(),700,0),
    ('10000000-0000-4000-8000-000000000006','${shop}','POS','PAID','completed',now()-interval '3 days',now()-interval '3 days',30,0);
    insert into public.order_items values
    ('20000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000001','ชาไทย',2,100),
    ('20000000-0000-4000-8000-000000000002','10000000-0000-4000-8000-000000000002','30000000-0000-4000-8000-000000000001','ชาไทย',1,50),
    ('20000000-0000-4000-8000-000000000003','10000000-0000-4000-8000-000000000003','30000000-0000-4000-8000-000000000002','คืนแล้ว',1,25),
    ('20000000-0000-4000-8000-000000000004','10000000-0000-4000-8000-000000000004','30000000-0000-4000-8000-000000000003','Delivery',1,900),
    ('20000000-0000-4000-8000-000000000005','10000000-0000-4000-8000-000000000005','30000000-0000-4000-8000-000000000004','ร้านอื่น',1,700),
    ('20000000-0000-4000-8000-000000000006','10000000-0000-4000-8000-000000000006',null,'เก่า',3,30)`);
  await set(false);await assert.rejects(()=>db.query(`select public.pos_owner_item_sales(1)`),/owner only/);
  await set(true);let data=(await db.query(`select public.pos_owner_item_sales(1) as report`)).rows[0].report;
  assert.equal(data.days,1);assert.equal(data.item_count,1);assert.equal(data.items[0].item_name,'ชาไทย');assert.equal(Number(data.items[0].quantity),3);assert.equal(Number(data.items[0].sales),140);assert.equal(data.items[0].bills,2);
  data=(await db.query(`select public.pos_owner_item_sales(7) as report`)).rows[0].report;assert.equal(data.item_count,2);
  data=(await db.query(`select public.pos_owner_item_sales(0) as report`)).rows[0].report;assert.equal(data.days,1);
  await set(true,other);data=(await db.query(`select public.pos_owner_item_sales(365) as report`)).rows[0].report;assert.equal(data.items.length,1);assert.equal(data.items[0].item_name,'ร้านอื่น');assert.equal(Number(data.items[0].sales),700);
  const grants=(await db.query(`select has_function_privilege('anon','public.pos_owner_item_sales(integer)','execute') anon,has_function_privilege('authenticated','public.pos_owner_item_sales(integer)','execute') auth`)).rows[0];assert.equal(grants.anon,false);assert.equal(grants.auth,true);
  assert.match(client,/rpc\('pos_owner_item_sales'/);assert.match(client,/qgPosReportCsv/);assert.match(client,/โหลดรายงานไม่สำเร็จ/);assert.match(css,/\.qg-pos-report-table-wrap/);assert.match(css,/@media\(max-width:480px\)/);
  await db.close();console.log(JSON.stringify({checks:18,failures:0,scope:'POS owner-only shop-scoped paid item report, date range, exclusion of refunded and delivery transactions, permissions and report UI'}));
})().catch(e=>{console.error(e);process.exit(1)});
