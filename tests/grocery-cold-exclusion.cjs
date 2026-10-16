'use strict';
const fs=require('fs'),assert=require('node:assert/strict'),{PGlite}=require('@electric-sql/pglite');
(async()=>{
 const db=new PGlite();
 await db.exec(`create role anon;create role authenticated;
 create table grocery_source_products(title_th text,title_en text,deepest_category text,active boolean,updated_at timestamptz);
 insert into grocery_source_products values
 ('มาม่า บะหมี่กึ่งสำเร็จรูป',null,'dry-grocery/instant-meals',true,now()),
 ('แป้งวาฟเฟิลแช่แข็ง',null,'dry-grocery/flour/flour',true,now()),
 ('วาฟเฟิล',null,'dry-grocery/dessert/frozen-desserts',true,now()),
 ('ชีสแช่เย็น',null,'dry-grocery/dairy-products/cheese',true,now()),
 ('Dough','Frozen dough','dry-grocery/flour',true,now());`);
 await db.exec(fs.readFileSync('supabase/migrations/20261016000036_grocery_source_cold_exclusion.sql','utf8'));
 assert.equal((await db.query('select count(*)::int n from grocery_source_products where active')).rows[0].n,1);
 assert.equal((await db.query('select count(*)::int n from grocery_source_products')).rows[0].n,5);
 await assert.rejects(db.exec('update grocery_source_products set active=true'),/grocery_source_no_active_cold/);
 assert.equal((await db.query('select count(*)::int n from grocery_source_products where active')).rows[0].n,1);
 await db.close();console.log('Grocery cold exclusion: 4 checks passed; isolated database only');
})().catch(e=>{console.error(e);process.exit(1)});
