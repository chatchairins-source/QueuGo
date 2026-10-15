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
  'user-agent':'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/154 Mobile Safari/537.36 QueueGoCatalogResearch/1.1',
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
function walk(obj,path='$',out=[]){
  if(out.length>5000)return out;
  if(Array.isArray(obj)){
    if(obj.length>=15 && obj.length<=25) out.push({path,type:'array',length:obj.length,sample:obj.slice(0,2)});
    obj.forEach((v,i)=>walk(v,path+'['+i+']',out));
  }else if(obj&&typeof obj==='object'){
    for(const [k,v] of Object.entries(obj)){
      if(/total|count|page|offset|cursor|limit|product|item|search|category/i.test(k)){
        let sample=v;
        try{ sample=JSON.stringify(v).slice(0,800); }catch{}
        out.push({path:path+'.'+k,type:typeof v,sample});
      }
      walk(v,path+'.'+k,out);
    }
  }
  return out;
}
async function get(url){
  const r=await fetch(url,{headers,redirect:'follow'});
  const html=await r.text();
  return {url:r.url,status:r.status,bytes:Buffer.byteLength(html),html,...extract(html,r.url)};
}
(async()=>{
  fs.mkdirSync('artifacts/makro-probe',{recursive:true});
  const candidates=[
    base,
    base+'?page=2',
    base+'?page=3',
    base+'?offset=20',
    base+'?offset=40',
    base+'?pageNumber=2',
    base+'?currentPage=2'
  ];
  const pages=[];
  for(const url of candidates){
    const p=await get(url);
    pages.push({requested:url,resolved:p.url,status:p.status,bytes:p.bytes,productLinks:p.productLinks,gtins:p.gtins,productImages:p.productImages});
    console.log('PAGE_TEST',JSON.stringify({
      requested:url,status:p.status,bytes:p.bytes,count:p.productLinks.length,
      gtins:p.gtins.slice(0,5),first:p.productLinks[0],last:p.productLinks.at(-1)
    }));
    if(url===base){
      fs.writeFileSync('artifacts/makro-probe/page.html',p.html);
      const hints=walk(p.nextData).filter(x=>{
        const s=JSON.stringify(x);
        return /total|count|page|offset|cursor|limit|product|item|search|category/i.test(s);
      });
      fs.writeFileSync('artifacts/makro-probe/next-hints.json',JSON.stringify(hints,null,2));
      console.log('NEXT_HINTS',JSON.stringify(hints.slice(0,120),null,2));
      const jsUrls=uniq([...p.html.matchAll(/<script[^>]+src=["']([^"']+)["']/gi)].map(m=>absolute(m[1],p.url)).filter(Boolean));
      for(const jsUrl of jsUrls.filter(u=>/pages\/c\//.test(u)).slice(0,3)){
        const jr=await fetch(jsUrl,{headers:{'user-agent':headers['user-agent']}});
        const js=await jr.text();
        const apiHints=uniq([...js.matchAll(/https?:\\?\/\\?\/[^"'\x60\\\s]+|\/[A-Za-z0-9_?=&{}\[\].:-]{8,}/g)]
          .map(m=>m[0].replace(/\\\//g,'/'))
          .filter(s=>/search|product|catalog|category|plp|graphql|api|page|offset|cursor/i.test(s))).slice(0,300);
        fs.writeFileSync('artifacts/makro-probe/category-page.js',js);
        fs.writeFileSync('artifacts/makro-probe/api-hints.json',JSON.stringify(apiHints,null,2));
        console.log('CATEGORY_JS',jsUrl,'bytes',Buffer.byteLength(js),'API_HINTS',JSON.stringify(apiHints.slice(0,120),null,2));
      }
    }
    await new Promise(r=>setTimeout(r,500));
  }
  const first=new Set(pages[0].gtins);
  for(const p of pages.slice(1)){
    const overlap=p.gtins.filter(x=>first.has(x)).length;
    console.log('OVERLAP_WITH_FIRST',p.requested,overlap,'of',p.gtins.length);
  }
  fs.writeFileSync('artifacts/makro-probe/probe.json',JSON.stringify({base,pages},null,2));
})().catch(err=>{console.error(err);process.exit(1);});
