(()=>{const S=()=>{try{return window.supabase?.createClient&&window.QT_SUPABASE_CONFIG?window.supabase.createClient(window.QT_SUPABASE_CONFIG.URL,window.QT_SUPABASE_CONFIG.KEY):null}catch(e){return null}};
async function openLaundry(){const db=S();if(!db)return alert('ระบบกำลังเชื่อมต่อ กรุณาลองใหม่');const {data:{user}}=await db.auth.getUser();if(!user)return alert('กรุณาเข้าสู่ระบบก่อนใช้บริการฝากซัก');const {data:cu}=await db.from('users').select('id').eq('auth_user_id',user.id).maybeSingle();const {data:h}=await db.from('laundry_hubs').select('id,name').eq('active',true).limit(1).maybeSingle();if(!cu||!h)return alert('ยังไม่มีร้านซักที่เปิดให้บริการ');let a=prompt('ระบุที่อยู่รับผ้า');if(!a)return;let n=prompt('หมายเหตุ (ถ้ามี)')||'';const {data:o,error}=await db.from('laundry_orders').insert({customer_id:cu.id,hub_id:h.id,pickup_address:a,note:n,service_type:'wash'}).select('order_number').single();if(error)return alert('สร้างรายการไม่สำเร็จ: '+error.message);alert('เรียก Rider รับผ้าแล้ว\n'+o.order_number)}
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