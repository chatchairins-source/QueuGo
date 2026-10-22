/* Device-local POS cart drafts. This store never creates or completes a sale. */
(()=>{
  'use strict';
  const DB_NAME='queuego-pos-offline-drafts-v1';
  const STORE='drafts';
  const DEVICE_KEY='queuego-pos-device-id-v1';
  const LAST_SHOP_PREFIX='queuego-pos-last-shop-v1:';
  let dbTask=null;
  const uuid=()=>window.crypto?.randomUUID?.()||'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g,c=>{const r=Math.random()*16|0;return(c==='x'?r:(r&3|8)).toString(16)});
  function deviceId(){
    let id='';
    try{id=localStorage.getItem(DEVICE_KEY)||'';if(!id){id=uuid();localStorage.setItem(DEVICE_KEY,id)}}catch(_){throw Error('อุปกรณ์นี้ไม่สามารถบันทึกร่าง POS แบบออฟไลน์ได้')}
    if(!id)throw Error('ไม่พบรหัสอุปกรณ์สำหรับร่าง POS');
    return id;
  }
  function open(){
    if(!window.indexedDB)return Promise.reject(Error('เบราว์เซอร์นี้ไม่รองรับการบันทึกร่าง POS แบบออฟไลน์'));
    if(!dbTask)dbTask=new Promise((resolve,reject)=>{
      const request=window.indexedDB.open(DB_NAME,2);
      request.onupgradeneeded=()=>{const db=request.result;if(!db.objectStoreNames.contains(STORE))db.createObjectStore(STORE,{keyPath:'id'});if(!db.objectStoreNames.contains('catalogs'))db.createObjectStore('catalogs',{keyPath:'key'})};
      request.onsuccess=()=>{const db=request.result;db.onversionchange=()=>db.close();resolve(db)};
      request.onerror=()=>{dbTask=null;reject(request.error||Error('เปิดที่เก็บร่าง POS ไม่สำเร็จ'))};
      request.onblocked=()=>{dbTask=null;reject(Error('ปิดแท็บ POS ที่ค้างอยู่แล้วลองใหม่'))};
    });
    return dbTask;
  }
  function scopeValid(s){return Boolean(s&&typeof s.shopId==='string'&&s.shopId&&typeof s.actorId==='string'&&s.actorId&&typeof s.deviceId==='string'&&s.deviceId)}
  const scopeKey=s=>[s.shopId,s.actorId,s.deviceId].join(':');
  function rememberShop(scope){if(!scopeValid(scope))throw Error('ขอบเขตร้าน POS ไม่ถูกต้อง');localStorage.setItem(LAST_SHOP_PREFIX+scope.actorId+':'+scope.deviceId,scope.shopId)}
  function lastShop(actorId,device){if(!actorId||!device)return null;try{return localStorage.getItem(LAST_SHOP_PREFIX+actorId+':'+device)||null}catch(_){return null}}
  function clean(d){
    if(!d||typeof d.id!=='string'||!d.id||!scopeValid(d)||!['DINE_IN','TAKEAWAY'].includes(d.type)||!Array.isArray(d.items)||!d.items.length||d.items.length>100)throw Error('ข้อมูลร่าง POS ไม่ถูกต้อง');
    const items=d.items.map(i=>{
      if(!i||typeof i.productId!=='string'||!i.productId||!Number.isInteger(i.quantity)||i.quantity<1||i.quantity>99||!Number.isFinite(i.priceSnapshot)||i.priceSnapshot<0||!Number.isFinite(i.basePriceSnapshot)||i.basePriceSnapshot<0||typeof i.nameSnapshot!=='string'||i.nameSnapshot.length>200||!i.options||typeof i.options!=='object'||Array.isArray(i.options)||Object.keys(i.options).length>30)throw Error('รายการสินค้าในร่างไม่ถูกต้อง');
      const options={};for(const [key,value]of Object.entries(i.options)){if(!/^[a-zA-Z0-9_-]{1,100}$/.test(key)||!(typeof value==='string'||(Array.isArray(value)&&value.length<=30&&value.every(x=>typeof x==='string'&&x.length<=100))))throw Error('ตัวเลือกสินค้าในร่างไม่ถูกต้อง');options[key]=Array.isArray(value)?[...value]:value}
      const requestIds=Array.isArray(i.requestIds)?i.requestIds.slice(0,i.quantity):[];if(requestIds.length&&requestIds.length!==i.quantity)throw Error('รหัสส่งร่าง POS ไม่ครบ');if(requestIds.some(x=>typeof x!=='string'||x.length>100)||new Set(requestIds).size!==requestIds.length)throw Error('รหัสส่งร่าง POS ไม่ถูกต้อง');
      return {productId:i.productId,quantity:i.quantity,priceSnapshot:i.priceSnapshot,basePriceSnapshot:i.basePriceSnapshot,nameSnapshot:i.nameSnapshot,options,optionLabel:String(i.optionLabel||'').slice(0,300),requestIds};
    });
    return {id:d.id,shopId:d.shopId,actorId:d.actorId,deviceId:d.deviceId,type:d.type,tableId:d.type==='DINE_IN'?(typeof d.tableId==='string'?d.tableId:null):null,requestId:typeof d.requestId==='string'&&d.requestId?d.requestId:uuid(),status:'NOT_SUBMITTED',createdAt:typeof d.createdAt==='string'?d.createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),items};
  }
  async function save(draft){
    const value=clean(draft),db=await open();
    return new Promise((resolve,reject)=>{
      const tx=db.transaction(STORE,'readwrite'),store=tx.objectStore(STORE),get=store.get(value.id);
      get.onsuccess=()=>{const old=get.result;if(old&&(old.shopId!==value.shopId||old.actorId!==value.actorId||old.deviceId!==value.deviceId)){tx.abort();reject(Error('ร่างนี้เป็นของบัญชีหรืออุปกรณ์อื่น'));return}if(old)value.createdAt=old.createdAt;store.put(value)};
      tx.oncomplete=()=>resolve({...value});tx.onerror=()=>reject(tx.error||Error('บันทึกร่าง POS ไม่สำเร็จ'));tx.onabort=()=>{if(!tx.error)reject(Error('ไม่สามารถบันทึกร่าง POS นี้ได้'))};
    });
  }
  async function list(scope){
    if(!scopeValid(scope))return [];
    const db=await open();
    return new Promise((resolve,reject)=>{const tx=db.transaction(STORE,'readonly'),request=tx.objectStore(STORE).getAll();request.onsuccess=()=>resolve((request.result||[]).filter(d=>d.shopId===scope.shopId&&d.actorId===scope.actorId&&d.deviceId===scope.deviceId).sort((a,b)=>String(b.updatedAt).localeCompare(String(a.updatedAt))));request.onerror=()=>reject(request.error||Error('อ่านร่าง POS ไม่สำเร็จ'))});
  }
  async function saveCatalog(scope,products,tables){
    if(!scopeValid(scope)||!Array.isArray(products)||products.length>20000||!Array.isArray(tables)||tables.length>300)throw Error('แคตตาล็อก POS สำหรับออฟไลน์ไม่ถูกต้อง');
    const cleanVariants=value=>{if(!Array.isArray(value))return[];return value.slice(0,30).flatMap(g=>{if(typeof g==='string')return[String(g).slice(0,100)];if(!g||g.schema!=='queuego.menu-options.v1'||!Array.isArray(g.options))return[];return[{schema:g.schema,key:String(g.key||'').slice(0,100),name:String(g.name||'').slice(0,120),type:g.type==='multi'?'multi':'single',required:g.required===true,min:Number.isInteger(g.min)?g.min:null,max:Number.isInteger(g.max)?g.max:null,options:g.options.slice(0,100).map(o=>({key:String(o.key||'').slice(0,100),name:String(o.name||'').slice(0,120),price_delta:Number(o.price_delta??o.price??0)})).filter(o=>o.key&&Number.isFinite(o.price_delta))}]})};
    const safeProducts=products.map(p=>({id:String(p.id||''),name:String(p.name||'').slice(0,200),price:Number(p.price),pos_price:p.pos_price==null?null:Number(p.pos_price),available:p.available===true,pos_available:p.pos_available===true,variants:cleanVariants(p.variants),inventory_stock_tracking:p.inventory_stock_tracking===true,grocery_master_id:p.grocery_master_id||null,grocery_stock_tracking:p.grocery_stock_tracking===true,stock:Number(p.stock||0)})).filter(p=>p.id&&Number.isFinite(p.price));
    const safeTables=tables.map(t=>({id:String(t.id||''),label:String(t.label||'').slice(0,100),active:t.active===true})).filter(t=>t.id);
    const db=await open(),value={key:scopeKey(scope),shopId:scope.shopId,actorId:scope.actorId,deviceId:scope.deviceId,products:safeProducts,tables:safeTables,savedAt:new Date().toISOString()};
    return new Promise((resolve,reject)=>{const tx=db.transaction('catalogs','readwrite');tx.objectStore('catalogs').put(value);tx.oncomplete=()=>resolve(value.savedAt);tx.onerror=()=>reject(tx.error||Error('บันทึกแคตตาล็อก POS ไม่สำเร็จ'))});
  }
  async function getCatalog(scope){
    if(!scopeValid(scope))return null;const db=await open();
    return new Promise((resolve,reject)=>{const tx=db.transaction('catalogs','readonly'),request=tx.objectStore('catalogs').get(scopeKey(scope));request.onsuccess=()=>{const x=request.result;resolve(x&&x.shopId===scope.shopId&&x.actorId===scope.actorId&&x.deviceId===scope.deviceId?x:null)};request.onerror=()=>reject(request.error||Error('อ่านแคตตาล็อก POS ไม่สำเร็จ'))});
  }
  async function remove(id,scope){
    if(typeof id!=='string'||!scopeValid(scope))return false;
    const db=await open();
    return new Promise((resolve,reject)=>{const tx=db.transaction(STORE,'readwrite'),store=tx.objectStore(STORE),request=store.get(id);let removed=false;request.onsuccess=()=>{const d=request.result;if(d&&d.shopId===scope.shopId&&d.actorId===scope.actorId&&d.deviceId===scope.deviceId){store.delete(id);removed=true}};tx.oncomplete=()=>resolve(removed);tx.onerror=()=>reject(tx.error||Error('ลบร่าง POS ไม่สำเร็จ'))});
  }
  window.QGPosOfflineDraftStore={deviceId,save,list,remove,saveCatalog,getCatalog,rememberShop,lastShop};
})();
