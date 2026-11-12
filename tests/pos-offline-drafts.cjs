const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert'),{JSDOM}=require('jsdom'),{indexedDB}=require('fake-indexeddb');
const root=path.resolve(__dirname,'..'),source=fs.readFileSync(path.join(root,'merchant/pos-offline-drafts.js'),'utf8');
let checks=0;const ok=(v,m)=>{assert.equal(v,true,m);checks++},eq=(a,b,m)=>{assert.deepEqual(a,b,m);checks++};
function boot(){const d=new JSDOM('',{url:'https://queuego.test/merchant/',runScripts:'outside-only'}),w=d.window;Object.defineProperty(w,'indexedDB',{value:indexedDB});w.crypto.randomUUID=()=>`uuid-${Math.random()}`;vm.runInContext(source,d.getInternalVMContext());return{d,w,api:w.QGPosOfflineDraftStore}}
(async()=>{
  const one=boot(),api=one.api,scope={shopId:'shop-a',actorId:'cashier-a',deviceId:api.deviceId()};
  eq(api.deviceId(),scope.deviceId,'device ID must persist across POS reloads');
  const first={id:'draft-a',...scope,type:'TAKEAWAY',tableId:'ignored',requestId:'request-a',items:[{productId:'p1',quantity:2,basePriceSnapshot:30,priceSnapshot:35,nameSnapshot:'ชาไทย',options:{size:'large'},optionLabel:'ใหญ่'}],accessToken:'must-not-persist',cardNumber:'must-not-persist'};
  const saved=await api.save(first);eq(saved.status,'NOT_SUBMITTED');eq(saved.tableId,null);eq(saved.items[0].quantity,2);eq(saved.items[0].requestIds,[],'request IDs are created only when the cashier submits online');ok(!JSON.stringify(saved).includes('must-not-persist'),'sensitive/unrecognized properties must be dropped');
  const reopened=boot();eq((await reopened.api.list(scope)).map(x=>x.id),['draft-a'],'draft survives module/page reload');
  const changed=await reopened.api.save({...saved,items:[{...saved.items[0],quantity:3}]});eq(changed.createdAt,saved.createdAt);eq(changed.items[0].quantity,3);
  eq(await reopened.api.list({shopId:'shop-a',actorId:'cashier-b',deviceId:scope.deviceId}),[],'other actor cannot read a saved draft');
  eq(await reopened.api.list({shopId:'shop-b',actorId:'cashier-a',deviceId:scope.deviceId}),[],'other shop cannot read a saved draft');
  eq(await reopened.api.list({shopId:'shop-a',actorId:'cashier-a',deviceId:'device-b'}),[],'other device cannot read a saved draft');
  let overwriteRejected=false;try{await reopened.api.save({...saved,actorId:'cashier-b'})}catch(_){overwriteRejected=true}ok(overwriteRejected,'another actor cannot overwrite a draft ID');
  eq(await reopened.api.remove('draft-a',{shopId:'shop-a',actorId:'cashier-b',deviceId:scope.deviceId}),false,'other actor cannot delete a draft');
  eq((await api.list(scope)).length,1,'unauthorized delete leaves the record');
  eq(await reopened.api.remove('draft-a',scope),true,'owner can explicitly remove a draft');
  eq(await api.list(scope),[],'owner deletion is durable across tabs');
  api.rememberShop(scope);eq(api.lastShop(scope.actorId,scope.deviceId),scope.shopId,'last shop scope is actor and device keyed');
  await api.saveCatalog(scope,[{id:'p1',name:'ชาไทย',price:30,pos_price:35,available:true,pos_available:true,category:'เครื่องดื่ม',variants:[{schema:'queuego.menu-options.v1',key:'size',name:'ขนาด',type:'single',required:true,options:[{key:'large',name:'ใหญ่',price_delta:5}]}],accessToken:'must-not-cache',image:'https://must-not-cache',stock:7}],[]);
  const cached=await api.getCatalog(scope);eq(cached.products.length,1);eq(cached.products[0].stock,7);eq(cached.products[0].category,'เครื่องดื่ม','offline POS keeps the product category for its category filter');eq(cached.products[0].variants[0].options[0].key,'large');ok(!JSON.stringify(cached).includes('must-not-cache'),'catalog snapshot contains only allowed fields');eq(await api.getCatalog({shopId:'shop-a',actorId:'cashier-b',deviceId:scope.deviceId}),null,'catalog snapshot is isolated by actor');
  await api.saveCatalog(scope,[
    null, // A malformed API/catalog row must not abort the whole offline cache.
    undefined,
    7,
    {id:'valid-zero',name:'สินค้าฟรี',price:0,pos_price:0,available:true,pos_available:true},
    {id:'invalid-negative',name:'ราคาไม่ถูกต้อง',price:-10,pos_price:10,available:true,pos_available:true},
    {id:'invalid-pos',name:'ราคาหน้าร้านไม่ถูกต้อง',price:20,pos_price:-1,available:true,pos_available:true},
    {id:'invalid-nan',name:'ราคาเสีย',price:'not-a-price',pos_price:20,available:true,pos_available:true},
    {id:'invalid-pos-nan',name:'ราคา POS เสีย',price:20,pos_price:'not-a-price',available:true,pos_available:true},
    {id:'missing-price',name:'ไม่มีราคา',pos_price:20,available:true,pos_available:true},
    {id:'null-price',name:'ราคา null',price:null,pos_price:20,available:true,pos_available:true},
    {id:'blank-price',name:'ราคาว่าง',price:'  ',pos_price:20,available:true,pos_available:true},
    {id:'blank-pos-price',name:'ราคา POS ว่าง',price:20,pos_price:' ',available:true,pos_available:true},
    {id:'null-pos-price',name:'ไม่มีราคาหน้าร้าน',price:20,pos_price:null,available:true,pos_available:true}
  ],[]);
  eq((await api.getCatalog(scope)).products.map(p=>p.id),['valid-zero','null-pos-price'],'offline catalog rejects missing, blank and invalid price snapshots, permits nullable POS price and retains valid zero-price products');
  let invalidRejected=false;try{await api.save({...first,id:'bad',items:[{productId:'p1',quantity:0,priceSnapshot:35,nameSnapshot:'ชาไทย',options:{}}]})}catch(_){invalidRejected=true}ok(invalidRejected,'invalid quantities are rejected');
  // Submission replay IDs must be exactly one per unit, never silently truncated.
  const replayItem={...first.items[0],requestIds:['unit-1','unit-2']};
  const replaySaved=await api.save({...first,id:'draft-replay-keys',items:[replayItem]});
  eq(replaySaved.items[0].requestIds,['unit-1','unit-2'],'valid replay keys survive persistence unchanged');
  let surplusRejected=false;
  try{await api.save({...first,id:'draft-surplus-keys',items:[{...replayItem,requestIds:['unit-1','unit-2','unit-3']}]})}catch(_){surplusRejected=true}
  ok(surplusRejected,'surplus replay keys must fail instead of being silently truncated');
  let missingRejected=false;
  try{await api.save({...first,id:'draft-missing-keys',items:[{...replayItem,requestIds:['unit-1']}]})}catch(_){missingRejected=true}
  ok(missingRejected,'partial replay keys must fail before storing an ambiguous submission');
  // The server uses replay IDs to deduplicate units across the entire bill, not per line.
  const duplicateAcrossLines=[
    {...replayItem,productId:'p1'},
    {...replayItem,productId:'p2',requestIds:['unit-2','unit-3']}
  ];
  let crossLineRejected=false;
  try{await api.save({...first,id:'draft-cross-line-duplicate',items:duplicateAcrossLines})}catch(_){crossLineRejected=true}
  ok(crossLineRejected,'duplicate replay keys across different product lines are rejected');
  const uniqueAcrossLines=await api.save({...first,id:'draft-cross-line-unique',items:[
    {...replayItem,productId:'p1'},
    {...replayItem,productId:'p2',requestIds:['unit-3','unit-4']}
  ]});
  eq(uniqueAcrossLines.items[1].requestIds,['unit-3','unit-4'],'unique replay keys across product lines remain valid');
  // Another tab can delete/recreate the IndexedDB database; a closed connection must not be reused.
  await new Promise((resolve,reject)=>{
    const deletion=indexedDB.deleteDatabase('queuego-pos-offline-drafts-v1');
    deletion.onsuccess=resolve;
    deletion.onerror=()=>reject(deletion.error||Error('IndexedDB test deletion failed'));
    deletion.onblocked=()=>reject(Error('Other POS tabs did not close on versionchange'));
  });
  const recovered=await reopened.api.save({...first,id:'draft-after-versionchange'});
  eq(recovered.id,'draft-after-versionchange','POS can save again after another tab invalidates its IndexedDB connection');
  eq((await api.list(scope)).map(d=>d.id),['draft-after-versionchange'],'all open POS tabs recover their own closed IndexedDB connections');
  one.d.window.close();reopened.d.window.close();
  console.log(JSON.stringify({checks,failures:0,scope:'identity and device scoped local POS drafts; no order/payment RPC or Production data'}));
})().catch(e=>{console.error(e);process.exit(1)});
