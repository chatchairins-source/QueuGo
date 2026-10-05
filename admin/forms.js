/* Accessible Admin forms for the existing actions. */
(()=>{'use strict';
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
window.qgAdminInput=({title,label,value='',type='textarea',min,max,step,minLength=2,hint=''})=>new Promise(resolve=>{
 const actor=currentUser()?.id;if(currentUser()?.type!=='admin')return resolve(null);
 const box=document.createElement('dialog');box.className='qg-admin-form';
 const field=type==='textarea'?`<textarea name="answer" required minlength="${minLength}" maxlength="500" placeholder="${esc(label)}">${esc(value)}</textarea>`:`<input name="answer" type="${esc(type)}" required value="${esc(value)}" ${min!=null?`min="${min}"`:''} ${max!=null?`max="${max}"`:''} ${step!=null?`step="${step}"`:''}>`;
 box.innerHTML=`<form><div class="qg-menu-head"><h2>${esc(title)}</h2><button type="button" aria-label="ปิด">✕</button></div><label>${esc(label)}${field}</label><p class="qt-admin-note">${esc(hint)}</p><p role="status"></p><div class="row-btns"><button type="button">ยกเลิก</button><button class="btn-primary" type="submit">ดำเนินการต่อ</button></div></form>`;
 let done=false;const finish=result=>{if(done)return;done=true;window.removeEventListener('hashchange',cancel);box.remove();resolve(result)};const cancel=()=>finish(null);
 box.querySelectorAll('button[type=button]').forEach(b=>b.onclick=cancel);box.addEventListener('cancel',e=>{e.preventDefault();cancel()});window.addEventListener('hashchange',cancel);
 box.querySelector('form').onsubmit=e=>{e.preventDefault();if(currentUser()?.id!==actor||currentUser()?.type!=='admin')return cancel();const f=e.target,answer=f.elements.answer.value.trim();if(!f.reportValidity()||(type==='textarea'&&answer.length<minLength)){box.querySelector('[role=status]').textContent='กรุณากรอกข้อมูลให้ครบ';return}finish(answer)};
 document.body.append(box);box.showModal();box.querySelector('[name=answer]').focus();
});
})();
