#!/usr/bin/env node
'use strict';
const endpoint='https://search.maknet.siammakro.cloud/search/api/v1/indexes/products/search';
const size=Number(process.argv[2]||100);
const pages=[1,2];
(async()=>{
 for(const page of pages){
  const body={
   q:'*',size,
   filters:{category:['dry-grocery'],isSalesCustomer:false,countryCode:'TH',lang:'th',allowRestrictedProduct:true,allowAlcohol:true},
   sortBy:'RELEVANCE',autocorrect:true,page,autocategorise:true,includeCurations:false,source:'web'
  };
  // Match Makro's public web request shape. No account token is sent.
  const r=await fetch(endpoint,{method:'POST',headers:{'content-type':'application/json','accept':'application/json','accept-language':'th-TH','x-device-platform':'ios'},body:JSON.stringify(body)});
  const text=await r.text();
  console.log('DIRECT_STATUS',page,r.status,text.slice(0,300));
  if(!r.ok)process.exit(2);
  const j=JSON.parse(text);
  console.log('DIRECT_PAGE',JSON.stringify({page:j.page,found:j.found,size:j.size,hitCount:j.hits?.length,first:j.hits?.[0]?.document?.productId,last:j.hits?.at(-1)?.document?.productId},null,2));
 }
})().catch(e=>{console.error(e);process.exit(1)});
