'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),{PGlite}=require('@electric-sql/pglite');
const source=fs.readFileSync('QueueGo-Laundry-Shop-Transport.sql','utf8');
const start=source.indexOf('create or replace function public.queuego_laundry_shop_contacts(');
const end=source.indexOf('\nCREATE OR REPLACE FUNCTION public.queuego_place_laundry_order_v2',start);
assert(start>=0&&end>start);
const migration=source.slice(start,end);
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
 const db=new PGlite();let checks=0;const eq=(a,b,m)=>{assert.deepEqual(a,b,m);checks++;};
 try{
 await db.exec(`create role anon;create role authenticated;create role service_role;create schema auth;
 create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('test.uid',true),'')::uuid$$;
 create table users(id uuid primary key,auth_user_id uuid,role text,status text,name text,phone text);
 create table shop_profiles(id uuid primary key,user_id uuid,status text,archived_at timestamptz);
 create table laundry_hubs(id uuid primary key,shop_id uuid);
 create table laundry_orders(id uuid primary key,hub_id uuid,customer_id uuid,status text);
 alter table users enable row level security;alter table laundry_orders enable row level security;
 create function public.get_my_user_id() returns uuid language sql stable security definer set search_path=public as
 $$select id from public.users where auth_user_id=auth.uid() and status not in ('deleted','suspended') limit 1$$;
 create function public.get_my_role() returns text language sql stable security definer set search_path=public as
 $$select case when role='admin' and status<>'active' then null else role end from public.users where auth_user_id=auth.uid() and status not in ('deleted','suspended') limit 1$$;
 grant usage on schema public,auth to anon,authenticated;
 insert into users values('${id(1)}','${id(101)}','shop','active','Shop A',null),
 ('${id(2)}','${id(102)}','shop','active','Shop B',null),
 ('${id(3)}','${id(103)}','customer','active','Test customer','test-phone');
 insert into shop_profiles values('${id(11)}','${id(1)}','active',null),('${id(12)}','${id(2)}','active',null);
 insert into laundry_hubs values('${id(21)}','${id(11)}'),('${id(22)}','${id(12)}');
 insert into laundry_orders values('${id(31)}','${id(21)}','${id(3)}','washing'),
 ('${id(32)}','${id(21)}','${id(3)}','completed'),('${id(33)}','${id(21)}','${id(3)}','cancelled'),
 ('${id(34)}','${id(22)}','${id(3)}','washing');`);
 await db.exec(migration);
 const as=async n=>{await db.exec('reset role');await db.query("select set_config('test.uid',$1,false)",[n?id(n):'']);await db.exec('set role authenticated');};
 const contacts=async hub=>(await db.query('select * from public.queuego_laundry_shop_contacts($1)',[hub])).rows;
 await as(101);
 eq(await contacts(id(21)),[{order_id:id(31),customer_name:'Test customer',customer_phone:'test-phone'}],'only owner active order contact');
 eq(await contacts(id(22)),[],'other shop hub denied');eq(await contacts(null),[],'null hub denied');
 await assert.rejects(db.query('select * from public.users'),/permission denied/);checks++;
 await as(102);eq(await contacts(id(21)),[],'other shop cannot enumerate first shop');
 await as(103);eq(await contacts(id(21)),[],'customer cannot read shop contacts');
 await as(null);eq(await contacts(id(21)),[],'missing subject denied');
 await db.exec(`reset role;update users set status='suspended' where id='${id(1)}'`);
 await as(101);eq(await contacts(id(21)),[],'suspended shop user denied');
 await db.exec(`reset role;update users set status='active' where id='${id(1)}';update shop_profiles set archived_at=now() where id='${id(11)}'`);
 await as(101);eq(await contacts(id(21)),[],'archived shop denied');
 await db.exec(`reset role;update shop_profiles set archived_at=null,status='suspended' where id='${id(11)}'`);
 await as(101);eq(await contacts(id(21)),[],'inactive shop denied');
 await db.exec('reset role');await db.exec(migration);
 eq((await db.query("select has_function_privilege('anon','public.queuego_laundry_shop_contacts(uuid)','EXECUTE') v")).rows[0].v,false,'anon grant absent after replay');
 await db.exec('set role anon');await assert.rejects(contacts(id(21)),/permission denied/);checks++;
 console.log(JSON.stringify({checks,failures:0,scope:'isolated original Laundry contacts RPC ownership and terminal-state privacy'}));
 }finally{await db.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
