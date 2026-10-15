#!/usr/bin/env node
'use strict';

const fs=require('fs');
const file=process.argv[2]&&!process.argv[2].startsWith('--')?process.argv[2]:'artifacts/makro-catalog/makro-grocery-branded.json';
const apply=process.argv.includes('--apply');
const ALLOWED_ROOTS=new Set(['dry-grocery','health-beauty/personal-care']);
const ROOT_URLS={
  'dry-grocery':'https://www.makro.pro/th/c/dry-grocery',
  'health-beauty/personal-care':'https://www.makro.pro/th/c/health-beauty/personal-care'
};

async function main(){
  const data=JSON.parse(fs.readFileSync(file,'utf8'));
  if(data?.count!==10000||data?.target_count!==10000||!Array.isArray(data.items)||data.items.length!==10000){
    throw Error('exact 10000-row Makro source snapshot required');
  }
  if(!data.retrieved_at)throw Error('retrieved_at required');
  const roots=data?.filters?.roots;
  if(!Array.isArray(roots)||roots.some(x=>!ALLOWED_ROOTS.has(x))||!roots.includes('dry-grocery')){
    throw Error('unexpected Makro source roots');
  }

  const rows=data.items.map((x,i)=>({
    source_product_id:String(x.source_product_id||''),
    source_rank:Number.isInteger(x.source_rank)?x.source_rank:i,
    source_category:x.source_category,
    source_url:x.source_url,
    sku:x.sku||null,
    barcode:x.barcode||null,
    brand:x.brand,
    title_th:x.title_th,
    title_en:x.title_en||null,
    deepest_category:x.deepest_category||x.source_category,
    image_url:x.image_url||null,
    unit_size:x.unit_size||null,
    unit_type:x.unit_type||null,
    unit_factor:x.unit_factor??null,
    retrieved_at:x.retrieved_at||data.retrieved_at
  }));

  const bad=rows.find(r=>
    !r.source_product_id||!r.brand||!r.title_th||
    !ALLOWED_ROOTS.has(r.source_category)||
    r.source_url!==ROOT_URLS[r.source_category]||
    !(r.deepest_category===r.source_category||String(r.deepest_category).startsWith(r.source_category+'/'))
  );
  if(bad)throw Error('invalid Makro grocery source row in snapshot');
  console.log(JSON.stringify({file,rows:rows.length,retrieved_at:data.retrieved_at,roots:[...new Set(rows.map(x=>x.source_category))],apply}));

  if(!apply)return;

  const url=process.env.QG_SUPABASE_URL;
  const token=process.env.QG_CATALOG_ADMIN_TOKEN;
  const key=process.env.QG_SUPABASE_PUBLISHABLE_KEY;
  if(!url||!token||!key)throw Error('QG_SUPABASE_URL, QG_CATALOG_ADMIN_TOKEN and QG_SUPABASE_PUBLISHABLE_KEY are required');

  const call=async(name,body)=>{
    const r=await fetch(url+'/rest/v1/rpc/'+name,{
      method:'POST',
      headers:{apikey:key,Authorization:'Bearer '+token,'Content-Type':'application/json'},
      body:JSON.stringify(body),
      signal:AbortSignal.timeout(60000)
    });
    const text=await r.text();
    if(!r.ok)throw Error(name+' failed: '+text);
    return text?JSON.parse(text):null;
  };

  let processed=0;
  for(let i=0;i<rows.length;i+=200){
    const result=await call('qg_admin_grocery_source_import',{p_rows:rows.slice(i,i+200)});
    processed+=Number(result?.processed||0);
    console.log(JSON.stringify({batch:i/200+1,processed,total:rows.length}));
  }
  if(processed!==10000)throw Error('source import count mismatch '+processed+'/10000');

  const finalized=await call('qg_admin_grocery_source_finalize',{
    p_retrieved_at:data.retrieved_at,
    p_target:10000
  });
  if(Number(finalized?.active)!==10000)throw Error('source activation mismatch');
  console.log(JSON.stringify({done:true,processed,finalized}));
}

main().catch(e=>{console.error(e.stack||e);process.exit(1)});
