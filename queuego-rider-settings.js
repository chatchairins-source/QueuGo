(function(){
'use strict';
let active=null;
function preference(raw){
  const row=Array.isArray(raw)?raw[0]:raw;
  if(!row||typeof row.configured!=='boolean'||typeof row.enabled!=='boolean')throw Error('ยังยืนยันการตั้งค่าจุดหมายไม่ได้');
  if(!row.configured)return {configured:false,enabled:false};
  const label=String(row.label||'').trim(),lat=row.latitude,lng=row.longitude;
  if(!validPoint(lat,lng)||!label||label.length>160)throw Error('ข้อมูลจุดหมายไม่ครบ กรุณาลองใหม่');
  return {configured:true,enabled:row.enabled,label,lat:Number(lat),lng:Number(lng)};
}
function validPoint(lat,lng){return lat!==null&&lat!==undefined&&lat!==''&&lng!==null&&lng!==undefined&&lng!==''&&Number.isFinite(Number(lat))&&Number.isFinite(Number(lng))&&Number(lat)>=5&&Number(lat)<=21&&Number(lng)>=97&&Number(lng)<=106}
function same(a,b){return a.configured&&a.enabled===b.p_enabled&&a.label===b.p_label&&Math.abs(a.lat-b.p_latitude)<1e-6&&Math.abs(a.lng-b.p_longitude)<1e-6}
async function openDestination({getActor,getAccessToken,rpc,map:Q,gps=null,toast=()=>{}}){
  if(active){active.node.querySelector('button')?.focus();return}
  const actor=getActor();if(!actor)return;
  const opener=document.activeElement,node=document.createElement('div');node.className='qg-destination-overlay';
  node.innerHTML='<section class="qg-destination-dialog" role="dialog" aria-modal="true" aria-labelledby="qg-destination-title" tabindex="-1"><header><div><h2 id="qg-destination-title">ปลายทางของฉัน</h2><p>เลือกทิศทางงานที่คุณต้องการ</p></div><button type="button" data-close aria-label="ปิดการตั้งค่าจุดหมาย">×</button></header><div role="status" data-message>กำลังโหลดการตั้งค่า…</div><form hidden><div class="qg-destination-search"><input type="search" data-search-input autocomplete="off" aria-label="ค้นหาสถานที่" placeholder="ค้นหาสถานที่ ถนน หรือชุมชน"><button type="button" data-search>ค้นหา</button></div><div data-search-results hidden></div><div class="qg-destination-map-wrap"><div data-map></div><div class="qg-destination-pin" aria-hidden="true">●</div></div><div class="qg-destination-map-note"><span>เลื่อนแผนที่ให้หมุดอยู่ตรงจุดหมาย</span><button type="button" data-gps>ตำแหน่งปัจจุบัน</button></div><label class="qg-destination-label">ชื่อจุดหมาย<input name="label" maxlength="160" required autocomplete="off" placeholder="เช่น บ้านหรือย่านที่ต้องการไป"></label><label class="qg-destination-check"><span>เลือกงานที่ไปทางนี้<small>เป็นความต้องการเส้นทาง ไม่รับประกันว่าจะมีงาน</small></span><input name="enabled" type="checkbox"></label><button type="submit" class="qg-btn qg-btn-primary" disabled>บันทึกจุดหมาย</button></form></section>';
  document.body.append(node);
  const dialog=node.querySelector('section'),form=node.querySelector('form'),message=node.querySelector('[data-message]'),save=form.querySelector('[type=submit]'),label=form.elements.namedItem('label'),enabled=form.elements.namedItem('enabled'),closeButton=node.querySelector('[data-close]'),gpsButton=node.querySelector('[data-gps]'),searchInput=node.querySelector('[data-search-input]'),searchButton=node.querySelector('[data-search]'),searchResults=node.querySelector('[data-search-results]');
  const state={node,busy:false};active=state;
  let nativeMap=null,loaded=null,point=null,mapReady=false,disposed=false,gpsBusy=false,searchSerial=0;
  const current=()=>!disposed&&active===state&&getActor()===actor;
  const sync=()=>{save.disabled=state.busy||!loaded||!point||!label.value.trim()||label.value.trim().length>160||(!loaded.configured&&!mapReady);label.disabled=state.busy;enabled.disabled=state.busy;gpsButton.disabled=state.busy||gpsBusy||!nativeMap;closeButton.disabled=state.busy;};
  const cleanup=()=>{
    if(disposed)return;disposed=true;clearInterval(watch);document.removeEventListener('keydown',key);
    Q?.destroy(nativeMap);node.remove();if(active===state)active=null;
    if(opener?.isConnected)opener.focus();
  };
  const close=()=>{if(!state.busy)cleanup()};
  const key=e=>{
    if(!current())return;
    if(e.key==='Escape'){e.preventDefault();close()}
    if(e.key==='Tab'){
      const items=[...dialog.querySelectorAll('button:not(:disabled),input:not(:disabled),[tabindex="0"]')].filter(x=>!x.closest('[hidden]'));
      const first=items[0],last=items.at(-1);
      if(e.shiftKey&&(document.activeElement===first||document.activeElement===dialog)){e.preventDefault();last?.focus()}
      else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus()}
    }
  };
  document.addEventListener('keydown',key);closeButton.onclick=close;node.onclick=e=>{if(e.target===node)close()};dialog.focus();
  const watch=setInterval(()=>{if(!current())cleanup()},500);
  label.oninput=sync;enabled.onchange=sync;
  const clearSearch=()=>{searchSerial++;searchResults.replaceChildren();searchResults.hidden=true};
  const runSearch=async()=>{
    const query=String(searchInput.value||'').trim(),ticket=++searchSerial;searchResults.replaceChildren();
    if(query.length<2){searchResults.hidden=true;return}
    searchResults.hidden=false;const status=document.createElement('div');status.textContent='กำลังค้นหาสถานที่…';searchResults.append(status);searchButton.disabled=true;
    try{
      const rows=await Q.search(query,{limit:8});if(!current()||ticket!==searchSerial)return;
      searchResults.replaceChildren();
      if(!rows.length){status.textContent='ไม่พบสถานที่';searchResults.append(status);return}
      for(const row of rows){
        const lat=Number(row.lat),lng=Number(row.lng??row.lon);if(!validPoint(lat,lng))continue;
        const item=document.createElement('button');item.type='button';item.textContent=String(row.name||row.label||'สถานที่');item.setAttribute('data-place-id',String(row.place_id||''));
        item.onclick=()=>{if(!current()||!nativeMap)return;point={lat,lng};label.value=String(row.name||row.label||'').slice(0,160);Q.setCenter(nativeMap,lat,lng,16,450);clearSearch();sync()};searchResults.append(item);
      }
      if(!searchResults.childElementCount){status.textContent='ไม่พบพิกัดที่ใช้ได้';searchResults.append(status)}
    }catch(_){if(current()&&ticket===searchSerial){status.textContent='ค้นหาสถานที่ไม่ได้ กรุณาลองใหม่';searchResults.replaceChildren(status)}}
    finally{if(searchButton.isConnected)searchButton.disabled=false}
  };
  searchButton.onclick=runSearch;searchInput.addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();runSearch()}});searchInput.addEventListener('input',clearSearch);
  const readCenter=()=>{
    if(!current()||state.busy||!mapReady)return;
    const center=nativeMap.getCenter();point=validPoint(center.lat,center.lng)?{lat:Number(center.lat),lng:Number(center.lng)}:null;sync();
  };
  gpsButton.onclick=async()=>{
    if(!nativeMap||state.busy||gpsBusy)return;
    gpsBusy=true;sync();message.textContent='กำลังหาตำแหน่ง…';
    try{
      const fix=await Q.locate(null);
      if(current()&&!state.busy){Q.setCenter(nativeMap,fix.lat,fix.lng,16,450);message.textContent=''}
    }catch(_){if(current())message.textContent='ยังหาตำแหน่งไม่ได้ กรุณาเปิดสิทธิ์ตำแหน่งแล้วลองใหม่'}
    finally{gpsBusy=false;if(current())sync()}

  };
  form.onsubmit=async e=>{
    e.preventDefault();if(save.disabled||!current())return;
    const body={p_label:label.value.trim(),p_latitude:point.lat,p_longitude:point.lng,p_enabled:enabled.checked};
    state.busy=true;sync();message.textContent='กำลังบันทึก…';
    try{
      const token=await getAccessToken();if(!current()||!token)throw Error('กรุณาเข้าสู่ระบบใหม่');
      let ack;
      try{ack=preference(await rpc('qg_rider_set_destination_preference',body,token))}
      catch(error){
        if(!current())return;
        try{const reconciled=preference(await rpc('qg_rider_destination_preference',{},token));if(same(reconciled,body))ack=reconciled}catch(_){}
        if(!ack)throw error;
      }
      if(!current())return;
      if(!same(ack,body))throw Error('ยังยืนยันการบันทึกไม่ได้ กรุณาตรวจสอบอีกครั้ง');
      loaded=ack;state.busy=false;cleanup();toast('บันทึกจุดหมายแล้ว');
    }catch(_){if(current())message.textContent='ยังยืนยันการบันทึกไม่ได้ ลองเปิดการตั้งค่าอีกครั้งเพื่อตรวจสอบ'}
    finally{state.busy=false;if(current())sync()}
  };
  try{
    const token=await getAccessToken();if(!current()||!token)throw Error('session');
    loaded=preference(await rpc('qg_rider_destination_preference',{},token));if(!current())return;
    label.value=loaded.label||'';enabled.checked=loaded.enabled;
    point=loaded.configured?{lat:loaded.lat,lng:loaded.lng}:validPoint(gps?.lat,gps?.lng)?{lat:Number(gps.lat),lng:Number(gps.lng)}:null;
    form.hidden=false;message.textContent='';
    const initial=point||{lat:14.994,lng:103.103};
    try{
      nativeMap=Q?.create(node.querySelector('[data-map]'),{...initial,zoom:point?16:12});
      if(nativeMap){nativeMap.on('load',()=>{if(!current())return;mapReady=true;readCenter()});nativeMap.on('moveend',readCenter)}
    }catch(_){}
    if(!nativeMap)message.textContent='แผนที่ยังไม่พร้อม กรุณาลองใหม่';
    sync();
  }catch(_){if(current())message.textContent='โหลดการตั้งค่าจุดหมายไม่ได้ กรุณาปิดแล้วลองใหม่'}
}
window.QueueGoRiderSettings={openDestination};
})();
