#!/usr/bin/env node
'use strict';

const target = process.argv[2] || 'https://www.makro.pro/th/c/dry-grocery';

function uniq(xs){ return [...new Set(xs)]; }
function decode(s){
  return String(s)
    .replace(/&amp;/g,'&')
    .replace(/&quot;/g,'"')
    .replace(/&#x27;|&#39;/g,"'")
    .replace(/&lt;/g,'<')
    .replace(/&gt;/g,'>');
}
function absolute(href){
  try { return new URL(decode(href), target).href; } catch { return null; }
}

(async()=>{
  const res = await fetch(target, {
    headers: {
      'user-agent': 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/154 Mobile Safari/537.36 QueueGoCatalogResearch/1.0',
      'accept-language': 'th-TH,th;q=0.9,en;q=0.7',
      'accept': 'text/html,application/xhtml+xml'
    },
    redirect: 'follow'
  });
  const html = await res.text();
  console.log(JSON.stringify({
    status: res.status,
    finalUrl: res.url,
    bytes: Buffer.byteLength(html),
    contentType: res.headers.get('content-type')
  }, null, 2));

  if(!res.ok){
    console.error(html.slice(0,2000));
    process.exit(2);
  }

  const hrefs = uniq([...html.matchAll(/href=["']([^"']+)["']/gi)].map(m=>absolute(m[1])).filter(Boolean));
  const productLinks = hrefs.filter(h=>/\/p\//.test(h));
  const paginationLinks = hrefs.filter(h=>/(page|offset|cursor|pagination)/i.test(h));
  const imageUrls = uniq([
    ...[...html.matchAll(/(?:src|srcset)=["']([^"']+)["']/gi)].flatMap(m=>decode(m[1]).split(/\s*,\s*/).map(x=>x.trim().split(/\s+/)[0])),
    ...[...html.matchAll(/https?:\\?\/\\?\/[^"'<>\\\s]+\.(?:png|jpe?g|webp)(?:\?[^"'<>\\\s]*)?/gi)].map(m=>m[0].replace(/\\\//g,'/'))
  ].filter(Boolean));

  const nextData = html.match(/<script[^>]+id=["']__NEXT_DATA__["'][^>]*>([\s\S]*?)<\/script>/i);
  let nextSummary = null;
  if(nextData){
    try{
      const data = JSON.parse(nextData[1]);
      nextSummary = {
        topKeys: Object.keys(data),
        page: data.page,
        buildId: data.buildId,
        query: data.query
      };
    }catch(e){ nextSummary={parseError:e.message, sample:nextData[1].slice(0,1000)}; }
  }

  const scripts = hrefs.filter(h=>/\.(?:js|json)(?:\?|$)/i.test(h));
  const routeHints = uniq([...html.matchAll(/["']([^"']*(?:page|offset|cursor)[^"']*)["']/gi)].map(m=>decode(m[1]))).slice(0,200);

  console.log('PRODUCT_LINKS', productLinks.length);
  console.log(productLinks.slice(0,40).join('\n'));
  console.log('PAGINATION_LINKS', paginationLinks.length);
  console.log(paginationLinks.slice(0,100).join('\n'));
  console.log('IMAGE_URLS', imageUrls.length);
  console.log(imageUrls.slice(0,60).join('\n'));
  console.log('NEXT_DATA', JSON.stringify(nextSummary,null,2));
  console.log('SCRIPT_URLS', scripts.slice(0,80).join('\n'));
  console.log('ROUTE_HINTS', routeHints.slice(0,80).join('\n'));

  require('fs').mkdirSync('artifacts/makro-probe',{recursive:true});
  require('fs').writeFileSync('artifacts/makro-probe/page.html',html);
  require('fs').writeFileSync('artifacts/makro-probe/probe.json',JSON.stringify({
    target,resolved:res.url,status:res.status,productLinks,paginationLinks,imageUrls,nextSummary,scripts,routeHints
  },null,2));
})().catch(err=>{ console.error(err); process.exit(1); });
