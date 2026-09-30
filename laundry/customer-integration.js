(()=>{const S=()=>{try{return window.supabase?.createClient&&window.QT_SUPABASE_CONFIG?window.supabase.createClient(window.QT_SUPABASE_CONFIG.URL,window.QT_SUPABASE_CONFIG.KEY):null}catch(e){return null}};
async function openLaundry(){const db=S();if(!db)return alert('ระบบกำลังเชื่อมต่อ กรุณาลองใหม่');const {data:{user}}=await db.auth.getUser();if(!user)return alert('กรุณาเข้าสู่ระบบก่อนใช้บริการฝากซัก');const {data:cu}=await db.from('users').select('id').eq('auth_user_id',user.id).maybeSingle();const {data:h}=await db.from('laundry_hubs').select('id,name').eq('active',true).limit(1).maybeSingle();if(!cu||!h)return alert('ยังไม่มีร้านซักที่เปิดให้บริการ');let a=prompt('ระบุที่อยู่รับผ้า');if(!a)return;let n=prompt('หมายเหตุ (ถ้ามี)')||'';const {data:o,error}=await db.from('laundry_orders').insert({customer_id:cu.id,hub_id:h.id,pickup_address:a,note:n,service_type:'wash'}).select('order_number').single();if(error)return alert('สร้างรายการไม่สำเร็จ: '+error.message);alert('เรียก Rider รับผ้าแล้ว\n'+o.order_number)}
function mount(){
  // Laundry is a QueueGo Consumer service: inject into the existing “อื่นๆ” category.
  const install=()=>{
    const candidates=[...document.querySelectorAll('button')].filter(b=>(b.textContent||'').trim()==='อื่นๆ');
    candidates.forEach(other=>{
      const wrap=other.parentElement;if(!wrap||wrap.querySelector('.qg-laundry-menu'))return;
      const b=other.cloneNode(true);b.classList.add('qg-laundry-menu');b.onclick=openLaundry;
      const label=b.querySelector('b');if(label)label.textContent='ฝากซัก';
      const icon=b.querySelector('span');if(icon)icon.innerHTML='🧺';
      other.insertAdjacentElement('afterend',b);
    });
    const old=document.getElementById('qgLaundryConsumer');if(old)old.remove();
  };
  install();new MutationObserver(install).observe(document.body,{childList:true,subtree:true});
}document.readyState==='loading'?addEventListener('DOMContentLoaded',mount):mount();})();