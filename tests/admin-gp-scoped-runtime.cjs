const fs=require('fs'),assert=require('assert/strict'),{JSDOM}=require('jsdom');
const tick=async()=>{for(let i=0;i<12;i++)await new Promise(r=>setImmediate(r))};
const uid=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
(async()=>{
  const html=fs.readFileSync('admin/index.html','utf8');
  const m=html.match(/<script id="qg-admin-live-gp">([\s\S]*?)<\/script>/);
  assert(m,'GP script missing');
  const d=new JSDOM('<div id="qg-gp-live"></div>',{url:'https://isolated.invalid/admin/#admin-gp',runScripts:'outside-only'}),w=d.window;
  let actor={id:uid(999),type:'admin',status:'approved'},calls=[],holdNextOrder=false,releaseOld=null,oldOrderStarted=false;
  w.currentUser=()=>actor;w.navigate=()=>{};w.escapeHtml=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  w.qtGetAccessToken=async()=> 'fixture';w.qtUuidLike=v=>/^[a-f0-9-]{36}$/.test(String(v));w.qgAdminInput=async()=>null;w.toast=()=>{};w.confirm=()=>true;
  w.fetch=async()=>({ok:true,json:async()=>({signedURL:'/x'})});w.open=()=>{};
  w.layout=(title,body)=>{w.document.body.innerHTML=body};
  const parseIds=(path,key)=>{const re=new RegExp(key+'=in\\.\\(([^)]+)\\)');return (path.match(re)?.[1]||'').split(',').filter(Boolean)};
  w.qtSupabaseTable=async(path,options={})=>{
    calls.push({path,options});
    if(path.startsWith('orders?')){
      if(holdNextOrder&&!oldOrderStarted){
        oldOrderStarted=true;
        return await new Promise(resolve=>{releaseOld=()=>resolve([{id:uid(8000),shop_id:uid(800),subtotal:999,gp_amount:99,gp_rate:10,status:'completed'}])});
      }
      if(calls.filter(x=>x.path.startsWith('orders?')).length===1){
        return Array.from({length:201},(_,i)=>({id:uid(3000+i),shop_id:uid(1000+i),subtotal:10,gp_amount:1,gp_rate:10,status:'completed'}));
      }
      return [{id:uid(9000),shop_id:uid(900),subtotal:50,gp_amount:5,gp_rate:10,status:'completed'}];
    }
    if(path.startsWith('shop_profiles?')){
      const ids=parseIds(path,'id');
      return ids.map(id=>({id,shop_name:id===uid(900)?'Fresh Shop':id===uid(800)?'Old Shop':'Shop '+id.slice(-4)}));
    }
    if(path.startsWith('settlements?')){
      const ids=parseIds(path,'shop_id');
      return ids.map((id,index)=>({id:uid(7000+Number(id.slice(-3)||0)),shop_id:id,date_key:w.qgGpDay,status:index===0?'processing':'idle',amount:0,metadata:index===0?{slip_path:'fixture-slip.jpg'}:{}}));
    }
    throw Error('unexpected '+path);
  };
  w.eval(m[1]);

  // 201 unique shops force two bounded profile/settlement batches.
  w.qgGpDay='2026-10-20';await w.qgLoadAdminGp();await tick();
  let checks=0;
  const orderCalls=calls.filter(x=>x.path.startsWith('orders?')),profileCalls=calls.filter(x=>x.path.startsWith('shop_profiles?')),settlementCalls=calls.filter(x=>x.path.startsWith('settlements?'));
  assert.equal(orderCalls.length,1);assert.equal(profileCalls.length,2);assert.equal(settlementCalls.length,2);checks+=3;
  for(const c of profileCalls){assert(c.path.includes('id=in.('));assert(c.path.includes('&limit=200'));assert(parseIds(c.path,'id').length<=200);checks+=3}
  for(const c of settlementCalls){assert(c.path.includes('shop_id=in.('));assert(c.path.includes('&limit=200'));assert(parseIds(c.path,'shop_id').length<=200);checks+=3}
  assert(!profileCalls.some(c=>c.path==='shop_profiles?select=id,shop_name'));assert(!settlementCalls.some(c=>!c.path.includes('shop_id=in.(')));checks+=2;
  const firstText=w.document.getElementById('qg-gp-live').textContent;
  assert(firstText.includes('ยอดขายร้านรวม'));assert(firstText.includes('ค่าบริการ GP รวม'));assert(firstText.includes('ออเดอร์ที่เข้าเกณฑ์'));checks+=3;
  assert(firstText.includes('201'));assert(firstText.includes('2,010.00'));assert(firstText.includes('201.00'));checks+=3;
  assert(firstText.includes('รอตรวจสลิป'));assert(firstText.includes('รอร้านชำระ'));checks+=2;
  const actionText=w.document.querySelector('.qg-live-gp-actions')?.textContent||'';
  assert(actionText.includes('เปิดสลิป'));assert(actionText.includes('ยืนยันสลิปและรับ GP'));assert(actionText.includes('ตีกลับสลิป'));checks+=3;

  // Old day resolves after a newer day. It must not trigger old scoped reads or repaint.
  calls=[];holdNextOrder=true;oldOrderStarted=false;w.qgGpDay='2026-10-18';const old=w.qgLoadAdminGp();await tick();
  assert(oldOrderStarted&&releaseOld);checks++;
  w.qgGpDay='2026-10-19';const fresh=w.qgLoadAdminGp();await fresh;await tick();
  const freshText=w.document.getElementById('qg-gp-live').textContent;assert(freshText.includes('Fresh Shop'));checks++;
  const beforeRelease=calls.length;releaseOld();await old;await tick();
  assert.equal(calls.length,beforeRelease);assert(w.document.getElementById('qg-gp-live').textContent.includes('Fresh Shop'));assert(!w.document.getElementById('qg-gp-live').textContent.includes('Old Shop'));checks+=3;

  assert(m[1].includes('qgAdminGpLoadVersion'));assert(!m[1].includes("qtSupabaseTable('shop_profiles?select=id,shop_name',{accessToken:token})"));checks+=2;
  assert(html.includes('ค่าบริการร้านค้า (GP)'));assert(html.includes('อัตราเฉพาะร้าน'));assert(html.includes('อัตราค่าบริการมาตรฐาน'));checks+=3;
  assert(!html.includes('🎯 อัตราพิเศษ'));assert(!html.includes('โปรหมดอายุแล้ว'));checks+=2;
  assert(html.includes('qg-gp-summary'));assert(html.includes('qg-gp-shop-card'));assert(html.includes('qg-gp-chip'));checks+=3;
  d.window.close();
  console.log(JSON.stringify({checks,failures:0,scope:'Admin GP keeps daily financial math and scoped reads while presenting clearer merchant-fee language, status chips, slip actions and stale date/account fencing'}));
})().catch(e=>{console.error(e);process.exit(1)});
