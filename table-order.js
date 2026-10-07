(()=>{'use strict';
 const base='https://pkypiqhlrmzocysgeqew.supabase.co/rest/v1/rpc/';
 const key='sb_publishable_Vn2Il4jp-iBtzNsGRNY8Lg_Tpvk2Vre';
 const storage='queuego-table-session-v1',pendingKey='queuego-table-pending-v1',cartKey='queuego-table-cart';
 const app=document.querySelector('#app');let session=null,device=null,products=[],cart=new Map(),pending=null,submitting=false;
 const esc=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const money=x=>Number(x).toLocaleString('th-TH',{minimumFractionDigits:2,maximumFractionDigits:2})+' ฿';
 const read=k=>{try{return JSON.parse(localStorage.getItem(k)||'null')}catch(_){return null}};
 async function rpc(name,body){
   const r=await fetch(base+name,{method:'POST',headers:{apikey:key,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
   const d=await r.json();if(!r.ok){const e=Error(d?.message||'เชื่อมต่อเซิร์ฟเวอร์ไม่สำเร็จ');e.status=r.status;throw e}return d;
 }
 function locationPoint(){return new Promise((resolve,reject)=>{if(!navigator.geolocation)return reject(Error('อุปกรณ์นี้ไม่มีระบบตำแหน่งที่ตั้ง'));navigator.geolocation.getCurrentPosition(p=>resolve({lat:p.coords.latitude,lng:p.coords.longitude}),e=>reject(Error('กรุณาเปิดสิทธิ์ตำแหน่งที่ตั้งและอยู่ภายใน 100 เมตรจากร้าน ('+e.message+')')),{enableHighAccuracy:true,maximumAge:0,timeout:15000})})}
 function save(){localStorage.setItem(storage,JSON.stringify({session:session.session_id,device}))}
 function saveCart(){localStorage.setItem(cartKey,JSON.stringify({session:session.session_id,shop:session.shop_id,table:session.table_id,items:[...cart].map(([id,v])=>({id,...v})),products}))}
 function restoreCart(changing=false){
   const saved=read(cartKey),same=saved&&(saved.session===session.session_id||(saved.shop===session.shop_id&&saved.table===session.table_id&&session.shop_id&&session.table_id));
   if(changing&&!pending&&!same&&saved?.items?.length){if(!confirm('มีรายการอาหารจากโต๊ะอื่น ต้องการเริ่มรายการใหม่ที่โต๊ะนี้หรือไม่?'))return false;localStorage.removeItem(cartKey)}
   const items=pending?.items||(same?saved.items:[]);
   cart=new Map((Array.isArray(items)?items:[]).filter(x=>typeof x.id==='string'&&Number.isInteger(x.quantity)&&x.quantity>0&&x.quantity<=99).map(x=>[x.id,{quantity:x.quantity,note:String(x.note||'').slice(0,500)}]));
   if(same&&Array.isArray(saved.products))products=saved.products;return true;
 }
 function expired(message){
   app.innerHTML='<header><b><i>Q</i> QueueGo</b></header><section class="card"><h1>สิทธิ์การสั่งอาหารจากโต๊ะนี้หมดอายุแล้ว</h1><p>กรุณาสแกน QR Code ที่โต๊ะอีกครั้ง</p>'+(message?'<p class="good">'+esc(message)+'</p>':'')+'</section>';
   if(!pending)localStorage.removeItem(storage)
 }
 function error(e){app.innerHTML='<header><b><i>Q</i> QueueGo</b></header><section class="card"><h1>ยังเปิดโต๊ะไม่ได้</h1><p class="banner">'+esc(e?.name==='TimeoutError'||e?.name==='AbortError'?'การเชื่อมต่อใช้เวลานาน กรุณาลองอีกครั้ง รายการเดิมยังอยู่':e.message||e)+'</p><button class="primary" onclick="location.reload()">ลองอีกครั้ง</button></section>'}
 async function status(){const next=await rpc('qg_table_session_status',{p_session:session.session_id,p_device_key:device});if(!next?.session_id)throw Error('ยังไม่ได้รับข้อมูลโต๊ะ กรุณาลองอีกครั้ง');session=next;return next.active===true}
 async function init(){
   try{
     const saved=read(storage),savedPending=read(pendingKey),hash=location.hash.slice(1),qr=hash.startsWith('scan/')?hash.slice(5):null;
     pending=savedPending?.session===saved?.session?savedPending:null;
     if(qr&&!pending){
       device=crypto.randomUUID();const point=await locationPoint();session=await rpc('qg_table_scan',{p_qr_token:qr,p_device_key:device,p_lat:point.lat,p_lng:point.lng});
       if(!session?.session_id)throw Error('ยังไม่ได้รับข้อมูลโต๊ะ กรุณาลองอีกครั้ง');if(!restoreCart(true)){app.innerHTML='<section class="card"><p>เก็บรายการเดิมไว้แล้ว สแกน QR ของโต๊ะเดิมเพื่อดำเนินการต่อ</p></section>';return}save();history.replaceState(null,'',location.pathname+'#table-order');
     }else{
       if(!saved){expired();return}device=saved.device;session={session_id:saved.session};restoreCart();
       if(!await status()&&!pending){if(cart.size)saveCart();expired();return}
     }
     restoreCart();
     if(session.active!==false){const rows=await rpc('qg_table_catalog',{p_session:session.session_id,p_device_key:device});if(!Array.isArray(rows))throw Error('ยังโหลดเมนูไม่ได้ กรุณาลองอีกครั้ง');products=rows}
     render();if(pending)notice('มีออเดอร์ที่ยังไม่ทราบผล กดตรวจออเดอร์เดิมก่อนสั่งเพิ่ม');
   }catch(e){error(e)}
 }
 function render(){
   const all=[...cart.entries()].map(([id,v])=>({...v,id})),locked=Boolean(pending||submitting),active=session.active!==false;
   const total=all.reduce((s,c)=>s+c.quantity*Number(products.find(p=>p.id===c.id)?.price||0),0);
   app.innerHTML=`<header><b><i>Q</i> QueueGo</b><span>${esc(session.table_name||'')}</span></header><section class="card"><h1>${esc(session.shop_name||'สั่งอาหารจากโต๊ะ')}</h1><p>สั่งอาหารจากโต๊ะ · ส่งเข้าครัวทันที</p><small class="muted">${active?'ใช้ได้ถึง '+new Date(session.expires_at).toLocaleTimeString('th-TH',{hour:'2-digit',minute:'2-digit'})+' ตามเวลาของเซิร์ฟเวอร์':'หมดเวลาสั่งเพิ่ม · ตรวจผลออเดอร์เดิมได้'}</small></section><section class="card"><h2>เมนูอาหาร</h2><div class="products">${products.map(p=>`<div class="product">${p.image?`<img loading="lazy" src="${esc(p.image)}" alt="">`:''}<div class="text"><b>${esc(p.name)}</b><small>${esc(p.description||'')} · ${money(p.price)}</small></div><button data-add="${p.id}" aria-label="เพิ่ม ${esc(p.name)}" ${locked||!active?'disabled':''}>＋</button></div>`).join('')||'<p>ร้านยังไม่มีเมนูหน้าร้าน</p>'}</div></section><section class="card"><h2>รายการที่เลือก</h2>${all.map(c=>{const p=products.find(x=>x.id===c.id);return `<div class="cart"><div class="cart-row"><b>${esc(p?.name||'รายการเดิม')} · ${money(p?.price||0)}</b><button data-minus="${c.id}" ${locked||!active?'disabled':''}>−</button><strong>${c.quantity}</strong><button data-add="${c.id}" ${locked||!active?'disabled':''}>＋</button></div><input data-note="${c.id}" maxlength="500" placeholder="หมายเหตุสำหรับครัว" value="${esc(c.note||'')}" ${locked||!active?'disabled':''}></div>`}).join('')||'<p>แตะ ＋ ที่เมนูเพื่อเริ่มสั่ง</p>'}</section><div id="feedback" role="status"></div><div class="footer"><button class="primary" id="submit" ${submitting||(!pending&&(!all.length||!active))?'disabled':''}>${submitting?'กำลังยืนยันออเดอร์…':pending?'ตรวจออเดอร์ที่ส่งไปแล้ว':'ส่งเข้าครัว · '+money(total)}</button><small>ออเดอร์หน้าร้าน ไม่มีค่า GP และค่าส่ง</small></div>`;
   app.querySelectorAll('[data-add]').forEach(b=>b.onclick=()=>{if(locked||!active)return;const id=b.dataset.add;if(!products.some(p=>p.id===id))return;const c=cart.get(id)||{quantity:0,note:''};if(c.quantity>=99)return notice('สั่งได้ไม่เกิน 99 ชิ้นต่อรายการ');c.quantity++;cart.set(id,c);saveCart();render()});
   app.querySelectorAll('[data-minus]').forEach(b=>b.onclick=()=>{if(locked||!active)return;const id=b.dataset.minus,c=cart.get(id);if(!c)return;if(--c.quantity<=0)cart.delete(id);saveCart();render()});
   app.querySelectorAll('[data-note]').forEach(b=>b.oninput=()=>{if(locked||!active)return;const c=cart.get(b.dataset.note);if(c){c.note=b.value.trim();saveCart()}});
   app.querySelector('#submit').onclick=submit;
 }
 function notice(s){const f=app.querySelector('#feedback');if(f)f.innerHTML='<p class="banner">'+esc(s)+'</p>'}
 async function submit(){
   if(submitting)return;if(!navigator.onLine)return notice('ไม่มีอินเทอร์เน็ต รายการเดิมยังอยู่ กรุณาลองอีกครั้งเมื่อเชื่อมต่อ');
   if(!pending&&!cart.size)return;
   submitting=true;render();let message='',confirmed=false;
   try{
     if(!pending){
       if(!await status()){saveCart();expired();return}
       const point=await locationPoint(),items=[...cart].map(([id,v])=>({id,quantity:v.quantity,note:v.note||''})).sort((a,b)=>a.id.localeCompare(b.id));
       pending={session:session.session_id,request:crypto.randomUUID(),items,point};localStorage.setItem(pendingKey,JSON.stringify(pending));
     }
     // An interrupted request reuses its original payload. The server alone confirms creation/replay.
     if(!pending.point){pending.point=await locationPoint();localStorage.setItem(pendingKey,JSON.stringify(pending))}
     const result=await rpc('qg_table_checkout',{p_session:session.session_id,p_device_key:device,p_request:pending.request,p_items:pending.items,p_lat:pending.point.lat,p_lng:pending.point.lng});
     if(!result?.order_id||!String(result?.order_number||'').trim())throw Error('ยังไม่ได้รับเลขยืนยันออเดอร์จากร้าน');
     cart.clear();pending=null;localStorage.removeItem(pendingKey);localStorage.removeItem(cartKey);
     message='ส่งเข้าครัวแล้ว · เลขออเดอร์ '+QueueGoOrderNumber.format(result)+' คุณสั่งอาหารเพิ่มได้ในเวลาที่เหลือ';confirmed=true;
   }catch(e){
     if(e.status>=400&&e.status<500&&![408,429].includes(e.status)){pending=null;localStorage.removeItem(pendingKey)}
     message=(e?.name==='TimeoutError'||e?.name==='AbortError'?'การเชื่อมต่อใช้เวลานาน ยังยืนยันผลไม่ได้':e.message||'ยังยืนยันผลไม่ได้')+(pending?' · กดตรวจออเดอร์เดิมอีกครั้ง':' · รายการเดิมยังอยู่ กรุณาตรวจสอบแล้วลองอีกครั้ง');
   }finally{
     submitting=false;
     if(session.active===false&&!pending){expired(message)}else{render();const f=app.querySelector('#feedback');if(f)f.innerHTML='<p class="'+(confirmed?'good':'banner')+'">'+esc(message)+'</p>'}
     if(confirmed)window.scrollTo({top:0,behavior:'smooth'});
   }
 }
 init();
})();
