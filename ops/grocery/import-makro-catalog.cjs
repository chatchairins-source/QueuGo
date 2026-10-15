'use strict';
const fs=require('fs');
async function main(){
  const file=process.argv[2]||'catalog/makro-dry-branded-20261015.json';
  const data=JSON.parse(fs.readFileSync(file,'utf8'));
  if(!Array.isArray(data.items)||data.items.length<8000)throw Error('Makro catalog missing or unexpectedly small');
  const rows=data.items.map(x=>({
    source_product_id:x.source_product_id,
    barcode:x.barcode,
    brand:x.brand,
    title_th:x.title_th,
    title_en:x.title_en,
    image_url:x.image_url,
    deepest_category:x.deepest_category||x.categories?.at(-1)||'dry-grocery',
    retrieved_at:x.retrieved_at||data.retrieved_at
  }));
  console.log(JSON.stringify({rows:rows.length,source_found:data.source_found,excluded:data.excluded}));
  if(!process.argv.includes('--apply'))return;
  const url=process.env.QG_SUPABASE_URL,token=process.env.QG_CATALOG_ADMIN_TOKEN,key=process.env.QG_SUPABASE_PUBLISHABLE_KEY;
  if(!url||!token||!key)throw Error('Admin access token and publishable key required; never commit credentials');
  let processed=0,created=0,linked=0;
  for(let i=0;i<rows.length;i+=200){
    const r=await fetch(url+'/rest/v1/rpc/qg_grocery_import_makro',{
      method:'POST',
      headers:{apikey:key,Authorization:'Bearer '+token,'Content-Type':'application/json'},
      body:JSON.stringify({p_rows:rows.slice(i,i+200)}),
      signal:AbortSignal.timeout(60000)
    });
    const text=await r.text();
    if(!r.ok)throw Error('Makro batch '+i+' failed: '+text);
    const result=JSON.parse(text);
    processed+=Number(result.processed||0);created+=Number(result.created||0);linked+=Number(result.linked||0);
    console.log(JSON.stringify({batch:i/200+1,processed,created,linked}));
  }
  console.log(JSON.stringify({done:true,processed,created,linked}));
}
main().catch(e=>{console.error(e.stack||e);process.exit(1)});
