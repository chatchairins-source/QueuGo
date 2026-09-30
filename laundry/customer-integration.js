(()=>{const S=()=>{try{return window.supabase?.createClient&&window.QT_SUPABASE_CONFIG?window.supabase.createClient(window.QT_SUPABASE_CONFIG.URL,window.QT_SUPABASE_CONFIG.KEY):null}catch(e){return null}};
const qgLaundryEsc=v=>String(v??'').replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
async function openLaundry(){
 const ctx=typeof window.qgLaundryCustomerContext==='function'?window.qgLaundryCustomerContext():{};
 const session=ctx.session||null;
 const u=ctx.user||null;
 if(!session||!u){ if(typeof window.qgLaundryNavigate==='function')return window.qgLaundryNavigate('login'); return; }
 const db=S();if(!db)return alert('ระบบกำลังเชื่อมต่อ กรุณาลองใหม่');
 try{if(session.accessToken&&session.refreshToken)await db.auth.setSession({access_token:session.accessToken,refresh_token:session.refreshToken})}catch(e){}
 const {data:hubs,error}=await db.from('laundry_hubs').select('id,name,shop_id').eq('active',true);
 if(error||!hubs?.length)return alert('ยังไม่มีร้านซักที่เปิดให้บริการ');
 const address=u.address||u.deliveryAddress||u.locationAddress||u.addressText||u.savedAddress||'';
 const lat=Number(u.lat??u.latitude),lng=Number(u.lng??u.longitude);
 const name=u.name||u.customerName||u.fullName||'ลูกค้า QueueGo';
 const phone=u.phone||u.phoneNumber||'';
 const old=document.getElementById('qgLaundrySheet');if(old)old.remove();
 const box=document.createElement('div');box.id='qgLaundrySheet';
 box.innerHTML='<div class="qg-laundry-backdrop"></div><section class="qg-laundry-sheet"><button class="qg-laundry-close" type="button">×</button><h2>ฝากซัก</h2><div class="qg-laundry-customer"><b>'+qgLaundryEsc(name)+'</b><span>'+qgLaundryEsc(phone)+'</span></div><label>ร้านซัก</label><select id="qgLaundryHub">'+hubs.map(h=>'<option value="'+h.id+'">'+qgLaundryEsc(h.name)+'</option>').join('')+'</select><label>จุดรับผ้า</label><div class="qg-laundry-address">'+qgLaundryEsc(address||'ยังไม่ได้ระบุที่อยู่จัดส่ง')+'</div><button type="button" id="qgLaundryPin">เลือก/แก้ไขพิกัดรับผ้า</button><button type="button" id="qgLaundryConfirm" class="primary">ยืนยันเรียก Rider รับผ้า</button></section>';
 const st=document.createElement('style');st.textContent='#qgLaundrySheet{position:fixed;inset:0;z-index:10050;font-family:inherit}.qg-laundry-backdrop{position:absolute;inset:0;background:#0006}.qg-laundry-sheet{position:absolute;left:0;right:0;bottom:0;background:#fff;border-radius:24px 24px 0 0;padding:24px 20px calc(24px + env(safe-area-inset-bottom));display:grid;gap:12px}.qg-laundry-close{position:absolute;right:18px;top:14px;border:0;background:#f2f2f2;border-radius:50%;width:34px;height:34px;font-size:24px}.qg-laundry-customer{display:flex;justify-content:space-between;background:#f7f7f8;padding:14px;border-radius:14px}.qg-laundry-sheet select,.qg-laundry-address,.qg-laundry-sheet button{font:inherit}.qg-laundry-sheet select,.qg-laundry-address{border:1px solid #e5e5e5;border-radius:14px;padding:14px;background:#fff}.qg-laundry-sheet button{padding:14px;border-radius:14px;border:1px solid #ddd;background:#fff;font-weight:700}.qg-laundry-sheet .primary{background:#e6002d;color:#fff;border-color:#e6002d}';box.appendChild(st);document.body.appendChild(box);
 box.querySelector('.qg-laundry-close').onclick=()=>box.remove();box.querySelector('.qg-laundry-backdrop').onclick=()=>box.remove();
 box.querySelector('#qgLaundryPin').onclick=()=>{box.remove();if(typeof window.qgLaundryNavigate==='function')window.qgLaundryNavigate('profile')};
 box.querySelector('#qgLaundryConfirm').onclick=async()=>{
   const hubId=box.querySelector('#qgLaundryHub').value;
   if(!address||!Number.isFinite(lat)||!Number.isFinite(lng)){box.remove();if(typeof window.qgLaundryNavigate==='function')window.qgLaundryNavigate('profile');return alert('กรุณาปักพิกัดจัดส่งก่อนเรียก Rider');}
   const {data:o,error:oe}=await db.from('laundry_orders').insert({customer_id:u.id,hub_id:hubId,pickup_address:address,pickup_latitude:lat,pickup_longitude:lng,service_type:'wash'}).select('order_number').single();
   if(oe)return alert('สร้างรายการไม่สำเร็จ: '+oe.message);box.remove();alert('เรียก Rider รับผ้าแล้ว\\n'+o.order_number);
 };
}
function mount(){
 const washer='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="4" y="2.5" width="16" height="19" rx="2.5"/><path d="M4 7.5h16"/><circle cx="12" cy="14.5" r="4.5"/><circle cx="8" cy="5" r=".65" fill="currentColor" stroke="none"/><circle cx="11" cy="5" r=".65" fill="currentColor" stroke="none"/></svg>';
 const install=()=>{
   document.getElementById('qgLaundryConsumer')?.remove();
   document.querySelectorAll('.qg-laundry-menu,.qg-laundry-main').forEach(x=>x.remove());
   const market=[...document.querySelectorAll('button.qg-cat')].find(x=>(x.textContent||'').trim()==='ตลาดสด');
   if(!market||market.parentElement.querySelector('.qg-laundry-category'))return;
   const btn=document.createElement('button');
   btn.type='button';btn.className='qg-cat qg-laundry-category';btn.setAttribute('aria-label','ฝากซัก');btn.onclick=openLaundry;
   btn.innerHTML='<span class="qg-cat-icon">'+washer+'</span><b>ฝากซัก</b>';
   market.before(btn);
 };
 install();new MutationObserver(install).observe(document.body,{childList:true,subtree:true});
}document.readyState==='loading'?addEventListener('DOMContentLoaded',mount):mount();})();