'use strict';
const fs=require('fs'),assert=require('node:assert/strict'),{PGlite}=require('@electric-sql/pglite');
(async()=>{
 const db=new PGlite();
 await db.exec(`create role anon;create role authenticated;create schema auth;
 create function auth.uid() returns uuid language sql as $$select '10000000-0000-4000-8000-000000000001'::uuid$$;
 create function get_my_user_id() returns uuid language sql as $$select auth.uid()$$;
 create function get_my_role() returns text language sql as $$select 'shop'::text$$;
 create table shop_profiles(id uuid,user_id uuid,public_category text,archived_at timestamptz);
 insert into shop_profiles values('20000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','grocery',null);
 create table products(shop_id uuid,metadata jsonb,category text);
 create table grocery_source_products(source text,source_product_id text,source_rank int,source_category text,sku text,barcode text,brand text,title_th text,title_en text,deepest_category text,image_url text,unit_size text,unit_type text,unit_factor numeric,active bool);
 create function qg_grocery_source_search(uuid,text,integer) returns jsonb language sql as $$select '[]'::jsonb$$;
 insert into grocery_source_products(source,source_product_id,source_rank,source_category,brand,title_th,deepest_category,active)
 select 'makro-pro',n::text,n,'dry-grocery','fixture','บะหมี่ fixture '||n,case when n<=60 then 'dry-grocery/instant-meals/instant-noodles' else 'dry-grocery/canned-packaged-food/canned-fish' end,true from generate_series(1,120)n;`);
 await db.exec(fs.readFileSync('supabase/migrations/20261016001648_grocery_source_categories_filter.sql','utf8'));
 const shop='20000000-0000-4000-8000-000000000001';
 const cats=(await db.query('select qg_grocery_source_categories($1) c',[shop])).rows[0].c;
 assert.equal(cats.length,2);assert.equal(cats.reduce((s,x)=>s+x.n,0),120);
 const page=async(cat,offset=0)=>(await db.query('select qg_grocery_source_search($1,$2,$3,$4) p',[shop,'',offset,cat])).rows[0].p;
 const first=await page('ปลากระป๋อง');assert.equal(first.length,51);assert(first.every(x=>x.queuego_category==='ปลากระป๋อง'));assert.equal((await page('ปลากระป๋อง',50)).length,10);
 assert.equal((await db.query('select qg_grocery_source_search($1,$2,$3) p',[shop,'',0])).rows[0].p.length,51);
 assert.equal((await page('หมวดที่ไม่มี')).length,0);
 await assert.rejects(db.query('select qg_grocery_source_categories($1)',['20000000-0000-4000-8000-000000000002']),/own grocery shop/);
 for(const [path,label] of [['dry-grocery/cooking-oil-vinegar/rice-bran-oil','น้ำมันพืช'],['dry-grocery/pasta-noodles/noodles','เส้นและพาสต้า'],['dry-grocery/grains-rice-cereal/cereal-granola-muesli-oats','ซีเรียลและข้าวโอ๊ต'],['health-beauty/personal-care/bar-soap','สบู่และผลิตภัณฑ์อาบน้ำ']])assert.equal((await db.query('select qg_grocery_source_category_label($1) c',[path])).rows[0].c,label);
 await db.exec('set role anon');await assert.rejects(db.query('select qg_grocery_source_categories($1)',[shop]),/permission denied/);
 await db.close();console.log('Grocery source categories: 13 checks passed; category filtering before pagination and owner/anonymous isolation');
})().catch(e=>{console.error(e);process.exit(1)});
