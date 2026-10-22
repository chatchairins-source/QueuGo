/* Same authenticated credit surface for Merchant and Rider; no provider credentials. */
(function(){
 const money=n=>'฿'+(Number(n||0)/100).toLocaleString('th-TH',{minimumFractionDigits:2,maximumFractionDigits:2});
 const labels={topup:'เพิ่มเครดิต',promo:'เพิ่มเครดิต',reserve:'กันเครดิต',release:'คืนยอดกัน',capture:'หักค่าบริการ',reversal:'คืนค่าบริการ'};
 const post=async({getToken,base,key},path,body)=>{const token=await getToken();if(!token)throw Error('กรุณาเข้าสู่ระบบใหม่');const response=await fetch(base+path,{method:'POST',headers:{apikey:key,authorization:'Bearer '+token,'content-type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(20000)});if(!response.ok)throw Error('ยังตรวจสอบเครดิตไม่ได้');return response.json()};
 window.QueueGoCredit={
  mountSummary:async function(host,{kind,owner,getToken,base,key,onOpen}){
   if(!host||!owner)return;const card=document.createElement('button');card.type='button';card.className='qg-credit-summary';card.setAttribute('aria-label',kind==='merchant'?'ดูเครดิตร้านค้า':'ดูเครดิต Rider');
   card.innerHTML='<span class="qg-credit-summary-icon">฿</span><span class="qg-credit-summary-copy"><strong></strong><b data-balance>กำลังโหลด...</b><small data-note></small></span><span class="qg-credit-summary-arrow">›</span>';
   card.querySelector('strong').textContent=kind==='merchant'?'เครดิตร้านค้า':'เครดิต Rider';host.replaceChildren(card);
   if(typeof onOpen==='function')card.onclick=onOpen;
   try{const summary=await post({getToken,base,key},'/rest/v1/rpc/queuego_credit_summary',{p_kind:kind,p_topup:null});if(!card.isConnected)return;
    card.querySelector('[data-balance]').textContent='ใช้ได้ '+money(summary.available_minor);
    card.querySelector('[data-note]').textContent=summary.enabled?'แตะเพื่อเติมเครดิตหรือดูประวัติ':'แตะดูเครดิตและประวัติ · ระบบเติมเครดิตยังไม่เปิด';
   }catch(e){if(card.isConnected){card.querySelector('[data-balance]').textContent='ยังโหลดเครดิตไม่ได้';card.querySelector('[data-note]').textContent=''}}
  },
  mountBadge:async function(host,{kind,owner,getToken,base,key,onOpen}){
   if(!host||!owner)return;const button=document.createElement('button');button.type='button';button.className='qg-credit-badge';button.textContent=kind==='merchant'?'เครดิต …':'เครดิต Rider …';button.setAttribute('aria-label',kind==='merchant'?'ดูเครดิตร้านค้า':'ดูเครดิต Rider');if(typeof onOpen==='function')button.onclick=onOpen;host.replaceChildren(button);
   try{const summary=await post({getToken,base,key},'/rest/v1/rpc/queuego_credit_summary',{p_kind:kind,p_topup:null});if(!button.isConnected)return;button.textContent='เครดิต '+money(summary.available_minor);button.title='ยอดเครดิตที่ใช้ได้ '+money(summary.available_minor)}
   catch(e){if(button.isConnected){button.textContent='เครดิต --';button.title='ยังโหลดเครดิตไม่ได้'}}
  },
  mount:async function(host,{kind,owner,getToken,base,key}){
  if(!host)return;const panel=document.createElement('section');panel.className='qg-credit-panel'+(kind==='rider'?' qg-credit-panel-rider':'');
  panel.innerHTML='<h3></h3><p data-balance>กำลังตรวจสอบเครดิต...</p><button type="button" data-add hidden>เติมเงิน</button><form hidden><label>จำนวนเงิน (20–100,000 บาท)<input name="amount" inputmode="decimal" type="text" maxlength="12" required></label><button type="submit">สร้าง QR PromptPay</button></form><button type="button" data-refresh></button><button type="button" data-new hidden>รายการใหม่</button><p data-status role="status"></p><img data-qr hidden alt="QR เติมเครดิต" width="240" height="240"><div data-entries></div><small>เครดิตใช้ชำระค่าบริการ QueueGo · ถอนหรือโอนไม่ได้</small>';
  host.appendChild(panel);panel.querySelector('h3').textContent=kind==='merchant'?'เครดิตร้านค้า':'เครดิต Rider';panel.querySelector('[data-refresh]').textContent=kind==='rider'?'รีเฟรชยอด':'ตรวจสอบรายการ / รีเฟรชยอด';
  const storageKey='qg-credit-request-v1:'+owner+':'+kind;let busy=false,summary=null;
  const session=()=>{try{const p=JSON.parse(localStorage.getItem(storageKey)||'null');return p?.id&&p?.kind===kind?p:null}catch{return null}};
  const request=(path,body)=>post({getToken,base,key},path,body);
  const current=()=>panel.isConnected;
  const status=s=>{if(current())panel.querySelector('[data-status]').textContent=s};
  const refresh=async(verify=false)=>{
   summary=await request('/rest/v1/rpc/queuego_credit_summary',{p_kind:kind,p_topup:session()?.id||null});if(!current())return;
   panel.querySelector('[data-balance]').textContent='ใช้ได้ '+money(summary.available_minor);
   if(!session()){
    const restored=(summary.topups||[]).find(t=>['created','pending'].includes(t.status));
    if(restored)localStorage.setItem(storageKey,JSON.stringify({id:restored.id,kind,amount_minor:restored.amount_minor}));
   }
   let pending=session(),topup=(summary.topups||[]).find(t=>t.id===pending?.id);
   if(verify&&pending&&!['successful','failed','expired'].includes(topup?.status)){
    await request('/functions/v1/queuego-credit',pending);
    summary=await request('/rest/v1/rpc/queuego_credit_summary',{p_kind:kind,p_topup:pending.id});if(!current())return;
    panel.querySelector('[data-balance]').textContent='ใช้ได้ '+money(summary.available_minor);
    topup=(summary.topups||[]).find(t=>t.id===pending.id);
   }
   const form=panel.querySelector('form');
   panel.querySelector('[data-add]').hidden=!summary.enabled;

   form.hidden=!summary.enabled;form.querySelector("input").disabled=!!pending;form.querySelector('button').textContent=pending?'ตรวจสอบรายการเดิม':'สร้าง QR PromptPay';
   const qr=panel.querySelector('[data-qr]');qr.hidden=true;qr.removeAttribute('src');
   if(topup?.status==='pending'&&/^https:\/\/api\.omise\.co\/charges\//.test(topup.qr_url||'')){qr.src=topup.qr_url;qr.hidden=false;status('รอชำระ '+money(topup.amount_minor)+' · เพิ่มเครดิตอัตโนมัติเมื่อตรวจสอบเงินเข้า')}
   if(pending&&!topup)status('กำลังตรวจสอบรายการเดิม กรุณากดตรวจสอบรายการ');
   if(topup?.status==='created')status('รอสร้าง QR · กดตรวจสอบรายการเพื่อลองรายการเดิมอีกครั้ง');
   if(topup?.status==='successful')status('เติมเครดิตสำเร็จ '+money(topup.amount_minor));
   if(['failed','expired'].includes(topup?.status))status('รายการไม่สำเร็จหรือหมดอายุ');
   panel.querySelector('[data-new]').hidden=!['successful','failed','expired'].includes(topup?.status);
   if(!summary.enabled)status(kind==='rider'?'PromptPay ยังไม่เปิดใช้งาน':'ระบบเติมเครดิตยังไม่เปิดใช้งาน');
   const list=panel.querySelector('[data-entries]');list.replaceChildren();for(const e of (summary.entries||[]).slice(0,20)){const row=document.createElement('p');row.textContent=(labels[e.event_type]||'รายการเครดิต')+' · '+money(['reserve','release'].includes(e.event_type)?Math.abs(Number(e.held_cash_delta)+Number(e.held_promo_delta)):Number(e.cash_delta)+Number(e.promo_delta))+' · '+new Date(e.created_at).toLocaleString('th-TH');list.appendChild(row)}
  };
  const act=async fn=>{if(busy)return;busy=true;panel.querySelectorAll('button').forEach(b=>b.disabled=true);try{await fn()}catch(e){status(e.message||'ยังยืนยันรายการไม่ได้ กรุณาตรวจรายการเดิมอีกครั้ง')}finally{busy=false;if(current())panel.querySelectorAll('button').forEach(b=>b.disabled=false)}};
  panel.querySelector('form').onsubmit=e=>{e.preventDefault();act(async()=>{
   let p=session();if(!p){const raw=panel.querySelector('input').value.trim();if(!/^\d+(?:\.\d{1,2})?$/.test(raw))throw Error('กรุณากรอกจำนวนเงินไม่เกิน 2 ตำแหน่ง');const [baht,satang='']=raw.split('.'),minor=Number(baht)*100+Number(satang.padEnd(2,'0'));if(!Number.isSafeInteger(minor)||minor<2000||minor>10000000)throw Error('เติมได้ 20–100,000 บาท');p={id:crypto.randomUUID(),kind,amount_minor:minor};localStorage.setItem(storageKey,JSON.stringify(p))}
   const r=await request('/functions/v1/queuego-credit',p);status(r.status==='successful'?'เติมเครดิตสำเร็จ':'กำลังตรวจสอบรายการเดิม');await refresh();
  })};
  panel.querySelector('[data-add]').onclick=()=>{panel.querySelector('form').hidden=false;panel.querySelector('input').focus()};
  panel.querySelector('[data-refresh]').onclick=()=>act(()=>refresh(true));
  panel.querySelector('[data-new]').onclick=()=>act(async()=>{localStorage.removeItem(storageKey);await refresh()});
  await act(refresh);
  const timer=setInterval(()=>{if(!current()){clearInterval(timer);return}if(!document.hidden&&session()&&!busy)act(()=>refresh(true))},10000);
 }};
})();
