#!/usr/bin/env node
'use strict';

const base = process.argv[2] || 'https://www.makro.pro/th/c/dry-grocery';
const fs = require('fs');

function uniq(xs){ return [...new Set(xs)]; }
function decode(s){
  return String(s)
    .replace(/&amp;/g,'&').replace(/&quot;/g,'"')
    .replace(/&#x27;|&#39;/g,"'").replace(/&lt;/g,'<').replace(/&gt;/g,'>');
}
function absolute(href, origin=base){
  try { return new URL(decode(href), origin).href; } catch { return null; }
}
const headers={
  'user-agent':'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/154 Mobile Safari/537.36 QueueGoCatalogResearch/1.2',
  'accept-language':'th-TH,th;q=0.9,en;q=0.7',
  'accept':'text/html,application/xhtml+xml'
};
function cleanProductUrl(u){
  try { const x=new URL(u); x.search=''; return x.href; } catch { return u; }
}
function extract(html,url){
  const hrefs=uniq([...html.matchAll(/href=["']([^"']+)["']/gi)]
    .map(m=>absolute(m[1],url)).filter(Boolean));
  const productLinks=uniq(hrefs.filter(h=>/\/p\//.test(h)).map(cleanProductUrl));
  const gtins=productLinks.map(h=>{
    const m=h.match(/-(\d{8,18})(?:$|[?#])/);
    return m?m[1]:null;
  }).filter(Boolean);
  const productImages=uniq([...html.matchAll(/https?:\\?\/\\?\/images\.mango-prod\.siammakro\.cloud\/product-images\/[^"'<>\\\s]+/gi)]
    .map(m=>m[0].replace(/\\\//g,'/').replace(/&amp;/g,'&'))
    .map(u=>u.split('?')[0]));
  const next=html.match(/<script[^>]+id=["']__NEXT_DATA__["'][^>]*>([\s\S]*?)<\/script>/i);
  let nextData=null; try{ if(next) nextData=JSON.parse(next[1]); }catch{}
  return {hrefs,productLinks,gtins,productImages,nextData};
}
function compactSearchSummary(nextData){
  const r=nextData?.props?.pageProps?.initialSearchResult||null;
  if(!r)return null;
  return {
    found:r.found,out_of:r.out_of,page:r.page,request_params:r.request_params,
    hits:(r.hits||[]).slice(0,3).map(h=>({
      id:h.document?.id,productId:h.document?.productId,sku:h.document?.sku,
      brand:h.document?.brand,title:h.document?.title,categories:h.document?.categories,
      images:(h.document?.images||[]).slice(0,2)
    }))
  };
}
async function get(url){
  const r=await fetch(url,{headers,redirect:'follow'});
  const html=await r.text();
  return {url:r.url,status:r.status,bytes:Buffer.byteLength(html),html,...extract(html,r.url)};
}
(async()=>{
  fs.mkdirSync('artifacts/makro-probe',{recursive:true});
  const p=await get(base);
  if(p.status!==200)throw new Error('Makro category fetch failed '+p.status);
  fs.writeFileSync('artifacts/makro-probe/page.html',p.html);
  fs.writeFileSync('artifacts/makro-probe/initial-search.json',JSON.stringify(p.nextData?.props?.pageProps?.initialSearchResult||null,null,2));
  console.log('INITIAL_SEARCH',JSON.stringify(compactSearchSummary(p.nextData),null,2));

  const scripts=uniq([...p.html.matchAll(/<script[^>]+src=["']([^"']+)["']/gi)]
    .map(m=>absolute(m[1],p.url)).filter(Boolean));
  const needles=[
    'products-agg-prod-v58','multi_search','typesense','per_page','cheapestPriceCollectionId',
    'searchParams','filter_by','facet_by','x-typesense-api-key','collections/products',
    'apiKey','searchClient','searchEndpoint','searchHost','searchUrl'
  ];
  const matches=[];
  for(const u of scripts){
    const r=await fetch(u,{headers:{'user-agent':headers['user-agent']},redirect:'follow'});
    if(!r.ok)continue;
    const js=await r.text();
    for(const needle of needles){
      let start=0,count=0;
      for(;;){
        const i=js.toLowerCase().indexOf(needle.toLowerCase(),start);
        if(i<0||count>=20)break;
        matches.push({script:u,needle,index:i,context:js.slice(Math.max(0,i-900),i+2200)});
        start=i+needle.length; count++;
      }
    }
    console.log('SCANNED_SCRIPT',u,'bytes',Buffer.byteLength(js),'matches',matches.filter(x=>x.script===u).length);
    await new Promise(r=>setTimeout(r,100));
  }
  fs.writeFileSync('artifacts/makro-probe/js-search-matches.json',JSON.stringify(matches,null,2));
  console.log('JS_MATCHES',JSON.stringify(matches.slice(0,100),null,2));

  const urls=uniq(matches.flatMap(m=>[...m.context.matchAll(/https?:\\?\/\\?\/[^"'\x60\\\s]+/g)].map(x=>x[0].replace(/\\\//g,'/'))));
  console.log('DISCOVERED_URLS',JSON.stringify(urls.slice(0,200),null,2));
  fs.writeFileSync('artifacts/makro-probe/discovered-urls.json',JSON.stringify(urls,null,2));
})().catch(err=>{console.error(err);process.exit(1);});
