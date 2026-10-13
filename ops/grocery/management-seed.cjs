'use strict';
// Trusted DBA bootstrap only. Never expose this SQL generator through the web app.
// Reuses the tested import body without impersonating an authenticated Admin.
const fs=require('fs'),N=require('../../catalog/normalize');
const rows=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
const checked=N.batch(rows);
if(checked.rejects.length)throw Error('Rejected catalog records');
const start=Number(process.argv[3]||0),limit=Number(process.argv[4]||100);
if(!Number.isInteger(start)||start<0||!Number.isInteger(limit)||limit<1||limit>200)throw Error('Invalid batch');
const batch=checked.items.slice(start,start+limit);
if(!batch.length)throw Error('Empty batch');
const migration=fs.readFileSync('supabase/migrations/20261012170449_grocery_master_catalog.sql','utf8');
let body=migration.split('create function public.qg_grocery_import(p_rows jsonb)')[1].split('as $$')[1].split('$$;')[0];
body=body.replace("declare r jsonb;",'declare p_rows jsonb := '+"'"+JSON.stringify(batch).replace(/'/g,"''")+"'::jsonb; r jsonb;");
body=body.replace(" if public.is_active_admin() is distinct from true then raise exception 'active admin required' using errcode='42501';end if;",'');
body=body.replace("return jsonb_build_object('processed',v_n,'activation','Admin must verify and activate new products');","raise notice 'processed % inactive products',v_n;");
console.log(JSON.stringify({query:'do $bootstrap$\n'+body+'\n$bootstrap$; select count(*) as master_products from public.grocery_master_products;',rows:batch.length,start}));
