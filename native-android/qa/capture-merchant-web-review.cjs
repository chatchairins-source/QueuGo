'use strict';
// Real deployed Web, existing secret-backed review account; no injected session or fixture data.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const BASE='https://chatchairins-source.github.io/QueuGo/merchant/';
const API='pkypiqhlrmzocysgeqew.supabase.co';
const READ_RPCS=new Set(['queuego_shop_readiness','effective_gp_rate','get_my_shop_orders',
  'merchant_revenue_days','pos_my_shop','pos_is_owner','pos_current_shift',
  'pos_register_shift_history','pos_owner_dashboard','pos_owner_item_sales','pos_product_codes_for_shop','queuego_credit_summary','check_active_session']);
// Existing normal sign-in ownership handshake; no business mutation or privilege changes.
// Production heartbeat only touches last_seen_at for this authenticated session.
const AUTH_RPCS=new Set(['claim_active_session','touch_active_session']);
function allowedRequest(method,url,body){
  const u=new URL(url);
  if(['GET','HEAD','OPTIONS'].includes(method))return true;
  if(method!=='POST'||u.hostname!==API)return false;
  if(u.pathname==='/auth/v1/token')return ['password','refresh_token'].includes(u.searchParams.get('grant_type'));
  if(u.pathname.startsWith('/rest/v1/rpc/')){const name=u.pathname.slice('/rest/v1/rpc/'.length);return READ_RPCS.has(name)||AUTH_RPCS.has(name)}
  if(u.pathname==='/functions/v1/queuego-credit'){
    try{return JSON.parse(body||'{}').action==='summary'}catch{return false}
  }
  return false;
}
async function main(){
  const {chromium}=require('playwright-core');
  const phone=process.env.QG_MERCHANT_REVIEW_PHONE,password=process.env.QG_MERCHANT_REVIEW_PASSWORD;
  if(!phone||!password)throw Error('Missing review credentials');
  const out=path.resolve('artifacts/merchant-web-review');fs.mkdirSync(out,{recursive:true});
  const browser=await chromium.launch({executablePath:process.env.QUEUEGO_CHROME||'/usr/bin/google-chrome',headless:true});
  let stage='launch';const blocked=[],screens=[];
  try{
    const context=await browser.newContext({viewport:{width:393,height:829},deviceScaleFactor:1,locale:'th-TH'});
    await context.route('**/*',route=>{
      const r=route.request();
      if(allowedRequest(r.method(),r.url(),r.postData()))return route.continue();
      blocked.push({method:r.method(),path:new URL(r.url()).pathname});return route.abort();
    });
    const page=await context.newPage();page.setDefaultTimeout(30000);
    stage='login';await page.goto(BASE,{waitUntil:'domcontentloaded'});
    await page.getByRole('textbox',{name:'เบอร์โทรศัพท์หรืออีเมล',exact:true}).waitFor({state:'visible'});
    await page.screenshot({path:path.join(out,'phone-login.png')});
    await page.getByRole('textbox',{name:'เบอร์โทรศัพท์หรืออีเมล',exact:true}).fill(phone);
    await page.getByRole('textbox',{name:'รหัสผ่าน',exact:true}).fill(password);
    await page.getByRole('button',{name:'เข้าสู่ระบบ',exact:true}).click();
    await page.locator('.qgm-bottom').waitFor({state:'visible',timeout:90000});
    // Clear neither sessions nor backend data; retain the normal authenticated browser session only in memory.
    const routes=[['home','dashboard','.qgm-menu-grid'],['orders','shop-orders','.qgm-bottom'],
      ['products','shop-products','.qgm-bottom'],['promotions','shop-promote','.qgm-bottom'],
      ['profile','shop-profile','.qgm-bottom'],['pos-counter','pos','.qg-pos-tabs']];
    for(const [device,viewport] of [['phone',{width:393,height:829}],['tablet',{width:1067,height:1707}]]){
      await page.setViewportSize(viewport);
      for(const [name,hash,selector] of routes){
        stage=device+'-'+name;await page.goto(BASE+'#'+hash,{waitUntil:'domcontentloaded'});
        await page.locator(selector).first().waitFor({state:'visible',timeout:90000});
        await page.evaluate(()=>document.fonts.ready);await page.waitForTimeout(4000);
        if(hash==='pos'){
          for(const [tab,label] of [['counter','＋ รับออเดอร์'],['tables','โต๊ะ'],['cashier','คิดเงิน']]){
            if(label){await page.getByRole('button',{name:label,exact:true}).click();await page.waitForTimeout(2000)}
            const file=device+'-pos-'+tab+'.png';await page.screenshot({path:path.join(out,file)});
            screens.push({device,screen:'pos-'+tab,file,viewport,url:page.url(),sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(out,file))).digest('hex')});
          }
        }else{
          const file=device+'-'+name+'.png';await page.screenshot({path:path.join(out,file)});
          screens.push({device,screen:name,file,viewport,url:page.url(),sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(out,file))).digest('hex')});
        }
      }
    }
    fs.writeFileSync(path.join(out,'metadata.json'),JSON.stringify({source_head:process.env.GITHUB_SHA,
      production_url:BASE,captured_at_utc:new Date().toISOString(),screens,blocked_mutations:blocked,
      physical_device:false,visual_parity_certified:false,production_business_mutations:false,
      scope:'Real deployed Merchant Web screenshots; ordinary Auth sign-in/session claim and allowlisted reads only'},null,2));
    console.log('Merchant Production Web review: '+screens.length+' screenshots; no business writes allowed');
  }catch{
    // Safe diagnostics only: no credential fields, tokens, response bodies or URLs with query strings.
    fs.writeFileSync(path.join(out,'failure.json'),JSON.stringify({source_head:process.env.GITHUB_SHA,
      stage,blocked_mutations:blocked,captured_screens:screens.map(x=>x.file)},null,2));
    console.error('Merchant Production Web review failed at stage: '+stage);process.exitCode=1
  }
  finally{await browser.close()}
}
module.exports={allowedRequest};
if(require.main===module)main().catch(()=>{console.error('Merchant Web review could not start');process.exitCode=1});
