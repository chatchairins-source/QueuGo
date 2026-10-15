(function(){
 const money=v=>'฿'+(Number(v||0)/100).toLocaleString('th-TH',{minimumFractionDigits:2,maximumFractionDigits:2});
 window.qgAdminLoadCredit=async function(){
  const host=document.getElementById('qg-credit-admin');if(!host)return;host.textContent='กำลังโหลดเครดิต...';
  try{
   const rows=await qtSupabaseRpc('queuego_admin_credit_accounts',{});if(!host.isConnected)return;host.replaceChildren();
   for(const a of rows){const card=document.createElement('article');card.className='card';const title=document.createElement('h3');title.textContent=(a.kind==='merchant'?'ร้านค้า · ':'Rider · ')+a.name;card.append(title);const total=document.createElement('p');total.textContent='เงินจริง '+money(a.cash_minor)+' · โปรโมชั่น '+money(a.promo_minor)+' · กันไว้ '+money(Number(a.held_cash_minor)+Number(a.held_promo_minor));card.append(total);
    for(const e of a.entries||[]){const row=document.createElement('p');row.textContent=e.event_type+' · '+money(Number(e.cash_delta)+Number(e.promo_delta));if(e.event_type==='capture'){const button=document.createElement('button');button.textContent='คืนค่าบริการ';button.type='button';button.onclick=async()=>{button.disabled=true;try{const reason=await qgAdminInput({title:'คืนค่าบริการเครดิต',label:'เหตุผลการคืน (อย่างน้อย 5 ตัวอักษร)'});if(reason===null)return;if(String(reason).trim().length<5)throw Error('กรุณาระบุเหตุผลอย่างน้อย 5 ตัวอักษร');await qtSupabaseRpc('queuego_admin_reverse_credit',{p_entry:e.id,p_reason:String(reason).trim()});toast('บันทึกคืนเครดิตแล้ว');await qgAdminLoadCredit()}catch(err){toast(err.message||'คืนเครดิตไม่สำเร็จ')}finally{button.disabled=false}};row.append(' ',button)}card.append(row)}host.append(card)
   }
   if(!rows.length)host.textContent='ยังไม่มีบัญชีเครดิต';
  }catch(_){host.textContent='ยังโหลดบัญชีเครดิตไม่ได้'}
 };
})();
