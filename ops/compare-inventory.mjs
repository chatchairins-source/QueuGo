import { readFile } from 'node:fs/promises';

const sourcePath=process.argv[2];
const restoredPath=process.argv[3];
if(!sourcePath||!restoredPath)throw new Error('Usage: compare-inventory <source.json> <restored.json>');
const source=JSON.parse((await readFile(sourcePath,'utf8')).trim());
const restored=JSON.parse((await readFile(restoredPath,'utf8')).trim());
const critical=[
  'auth.users','public.users','public.shop_profiles','public.rider_profiles',
  'public.orders','public.order_items','public.deliveries','public.notifications',
  'public.products','public.markets','public.market_orders','public.laundry_orders',
  'public.qg_pickup_proofs','public.qg_delivery_proofs',
  'public.qg_ugc_terms_acceptances','public.qg_user_blocks','public.qg_ugc_reports',
  'storage.buckets','storage.objects'
];
const failures=[];
for(const key of critical){
  const a=Number(source[key]??-1),b=Number(restored[key]??-1);
  if(a<0||b<0)failures.push(key+':missing');
  else if(b>a)failures.push(key+':restored count exceeds source snapshot');
  else if(a-b>2)failures.push(key+':source/restored drift too large ('+a+' vs '+b+')');
}
if(failures.length)throw new Error('Restore inventory mismatch: '+failures.join(', '));
console.log(JSON.stringify({verified:critical.length,source,restored}));
