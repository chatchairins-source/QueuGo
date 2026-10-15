#!/usr/bin/env node
'use strict';
const fs=require('fs');
const {chromium}=require('playwright-core');
const target=process.argv[2]||'https://www.makro.pro/th/c/dry-grocery';

(async()=>{
  fs.mkdirSync('artifacts/makro-browser-probe',{recursive:true});
  const executableCandidates=['/usr/bin/google-chrome','/usr/bin/google-chrome-stable','/usr/bin/chromium','/usr/bin/chromium-browser'];
  const executablePath=executableCandidates.find(p=>fs.existsSync(p));
  if(!executablePath) throw new Error('Chromium/Chrome executable not found');

  const browser=await chromium.launch({headless:true,executablePath,args:['--no-sandbox','--disable-dev-shm-usage']});
  const page=await browser.newPage({viewport:{width:1366,height:900},locale:'th-TH'});
  const requests=[],responses=[];

  page.on('request',req=>{
    const type=req.resourceType();
    if(type!=='xhr'&&type!=='fetch')return;
    const u=req.url();
    if(/google|facebook|doubleclick|analytics|segment|braze|metrics|appsflyer|sentry|accesstrade/i.test(u))return;
    requests.push({type,method:req.method(),url:u,postData:req.postData(),headers:req.headers()});
  });

  page.on('response',async res=>{
    const req=res.request();
    const type=req.resourceType();
    if(type!=='xhr'&&type!=='fetch')return;
    const u=res.url();
    if(/google|facebook|doubleclick|analytics|segment|braze|metrics|appsflyer|sentry|accesstrade/i.test(u))return;
    const ct=res.headers()['content-type']||'';
    if(!ct.includes('json'))return;
    try{
      const body=await res.json();
      const text=JSON.stringify(body);
      if(/"hits"|"found"|"products"|"items"/i.test(text)){
        responses.push({url:u,status:res.status(),request:{method:req.method(),postData:req.postData(),headers:req.headers()},body});
      }
    }catch{}
  });

  await page.goto(target,{waitUntil:'domcontentloaded',timeout:120000});
  await page.waitForTimeout(5000);

  const clicked=await page.evaluate(()=>{
    const el=document.querySelector('button[data-test-id$="paginationItem-2"]');
    if(!el)return false;
    el.click();
    return true;
  });
  if(!clicked) throw new Error('Makro pagination page 2 button not found');
  await page.waitForTimeout(8000);

  fs.writeFileSync('artifacts/makro-browser-probe/requests.json',JSON.stringify(requests,null,2));
  fs.writeFileSync('artifacts/makro-browser-probe/responses.json',JSON.stringify(responses,null,2));
  console.log('PAGE_AFTER_CLICK',page.url());
  console.log('CURRENT_PAGE_MARKER',await page.locator('[aria-current="true"]').first().textContent().catch(()=>null));
  console.log('BROWSER_REQUESTS',JSON.stringify(requests.slice(-120),null,2));
  console.log('BROWSER_RESPONSES',JSON.stringify(responses.slice(-40).map(x=>({
    url:x.url,status:x.status,method:x.request.method,postData:x.request.postData,
    found:x.body?.found ?? x.body?.results?.[0]?.found ?? x.body?.pageProps?.initialSearchResult?.found,
    page:x.body?.page ?? x.body?.results?.[0]?.page ?? x.body?.pageProps?.initialSearchResult?.page,
    hitCount:x.body?.hits?.length ?? x.body?.results?.[0]?.hits?.length ?? x.body?.pageProps?.initialSearchResult?.hits?.length,
    bodyKeys:Object.keys(x.body||{})
  })),null,2));

  await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
