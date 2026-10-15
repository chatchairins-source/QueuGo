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
  const seen=[];
  const responses=[];
  page.on('request',req=>{
    const u=req.url();
    if(/search|type|product|catalog|collection/i.test(u)){
      seen.push({method:req.method(),url:u,postData:req.postData(),headers:req.headers()});
    }
  });
  page.on('response',async res=>{
    const u=res.url();
    if(!/search|type|product|catalog|collection/i.test(u))return;
    const ct=res.headers()['content-type']||'';
    if(!ct.includes('json'))return;
    try{
      const body=await res.json();
      const text=JSON.stringify(body);
      if(/"hits"|"found"|"products"/i.test(text)){
        responses.push({url:u,status:res.status(),body});
      }
    }catch{}
  });
  await page.goto(target,{waitUntil:'domcontentloaded',timeout:120000});
  await page.waitForTimeout(5000);
  for(let i=0;i<6;i++){
    await page.evaluate(()=>window.scrollTo(0,document.body.scrollHeight));
    await page.waitForTimeout(1800);
  }
  fs.writeFileSync('artifacts/makro-browser-probe/requests.json',JSON.stringify(seen,null,2));
  fs.writeFileSync('artifacts/makro-browser-probe/responses.json',JSON.stringify(responses,null,2));
  console.log('BROWSER_REQUESTS',JSON.stringify(seen.slice(-80),null,2));
  console.log('BROWSER_RESPONSES',JSON.stringify(responses.slice(-20).map(x=>({
    url:x.url,status:x.status,
    found:x.body?.found ?? x.body?.results?.[0]?.found,
    page:x.body?.page ?? x.body?.results?.[0]?.page,
    hitCount:x.body?.hits?.length ?? x.body?.results?.[0]?.hits?.length
  })),null,2));
  await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
