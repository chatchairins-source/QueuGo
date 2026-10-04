/* QueueGo Thermal Printer Module v1 */
(()=>{'use strict';
 const SETTINGS='qg_printer_settings_v1', PRINTED='qg_printer_printed_v1';
 const defaults={mode:'browser',width:'80',autoKitchen:false,autoReceipt:false,bridgeUrl:'',btService:'',btCharacteristic:''};
 const nativeAndroid=()=>window.Capacitor?.isNativePlatform?.()===true&&window.Capacitor?.getPlatform?.()==='android';
 const normalizeBridgeUrl=value=>{
   const raw=String(value||'').trim();if(!raw)throw Error('กรุณาใส่ URL ของ LAN / Print Bridge');
   let u;try{u=new URL(raw)}catch(_){throw Error('URL ของ Print Bridge ไม่ถูกต้อง')}
   const h=u.hostname.toLowerCase(),privateHttp=u.protocol==='http:'&&(h==='localhost'||h==='127.0.0.1'||h==='::1'||h.startsWith('10.')||h.startsWith('192.168.')||/^172\.(1[6-9]|2\d|3[01])\./.test(h));
   if(u.protocol!=='https:'&&!privateHttp)throw Error('Print Bridge ต้องใช้ HTTPS หรือ HTTP ภายในเครือข่ายร้าน');
   return u.href;
 };
 let shopId=null,shopName='ร้านค้า',bt=null,usb=null,generation=0,queue=Promise.resolve();
 const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const money=n=>Number(n||0).toLocaleString('th-TH',{minimumFractionDigits:2,maximumFractionDigits:2})+' ฿';
 const get=()=>{try{return {...defaults,...JSON.parse(localStorage.getItem(SETTINGS)||'{}')}}catch(_){return {...defaults}}};
 const save=s=>{const v={...get(),...s};localStorage.setItem(SETTINGS,JSON.stringify(v));return v};
 const printed=()=>{try{return JSON.parse(localStorage.getItem(PRINTED)||'{}')}catch(_){return {}}};
 const was=(id,t,batch)=>!!printed()[shopId+':'+t+':'+id+(batch===undefined?'':':'+batch)];
 const mark=(id,t,batch)=>{const p=printed();p[shopId+':'+t+':'+id+(batch===undefined?'':':'+batch)]=Date.now();const ks=Object.keys(p).sort((a,b)=>p[b]-p[a]).slice(0,300),o={};ks.forEach(k=>o[k]=p[k]);localStorage.setItem(PRINTED,JSON.stringify(o))};
 async function req(path){const token=await qtGetAccessToken();if(!token)throw Error('กรุณาเข้าสู่ระบบร้านค้า');return qtSupabaseTable(path,{accessToken:token})}
 function ensureShop(){if(!shopId)throw Error('กรุณาเปิดหน้าร้านก่อนใช้เครื่องพิมพ์');return shopId}
 async function fetchOrder(id){
   ensureShop();
   const [o,i]=await Promise.all([
    req('orders?select=id,order_number,order_type,table_id,status,kitchen_status,payment_status,payment_method,subtotal,total_amount,discount_amount,cash_tendered,cash_change,created_at,paid_at&shop_id=eq.'+shopId+'&id=eq.'+id),
    req('order_items?select=id,order_id,item_name,description,quantity,unit_price,total_price,pos_kitchen_status,pos_batch,created_at&order_id=eq.'+id+'&order=created_at.asc')
   ]);
   return {order:o?.[0],items:i||[]};
 }
 async function tableLabel(id){if(!id)return 'ไม่ระบุโต๊ะ';const r=await req('pos_tables?select=label&id=eq.'+id);return r?.[0]?.label||'โต๊ะ'}
 async function ticket(order,items,type){
   const where=order.order_type==='DINE_IN'?await tableLabel(order.table_id):'รับกลับ';
   const line='--------------------------------';
   let s='QueueGo\n'+shopName+'\n'+(type==='kitchen'?'ใบเข้าครัว':'ใบเสร็จรับเงิน')+'\n'+line+'\n';
   s+=order.order_number+'\n'+where+'\n'+new Date(order.created_at||Date.now()).toLocaleString('th-TH')+'\n'+line+'\n';
   for(const x of items)s+=Number(x.quantity)+' x '+x.item_name+(x.description?'\n  '+x.description:'')+'\n';
   if(type==='receipt'){
     s+=line+'\nรวม '+money(order.total_amount)+'\n';
     if(Number(order.discount_amount||0)>0)s+='ส่วนลด '+money(order.discount_amount)+'\n';
     if(order.payment_method)s+='ชำระ '+order.payment_method+'\n';
     if(order.payment_method==='cash'){s+='รับเงิน '+money(order.cash_tendered)+'\nเงินทอน '+money(order.cash_change)+'\n'}
   }
   return s+line+'\n\n';
 }
 function escpos(text){const enc=new TextEncoder(),body=enc.encode(text),a=new Uint8Array([27,64]),b=new Uint8Array([29,86,0]),out=new Uint8Array(a.length+body.length+b.length);out.set(a,0);out.set(body,a.length);out.set(b,a.length+body.length);return out}
 async function browserPrint(text,title){const w=get().width==='58'?'58mm':'80mm',win=window.open('','_blank','width=420,height=720');if(!win)throw Error('กรุณาอนุญาต Pop-up เพื่อพิมพ์');win.document.write('<!doctype html><meta charset="utf-8"><title>'+esc(title)+'</title><style>@page{size:'+w+' auto;margin:3mm}body{font-family:-apple-system,BlinkMacSystemFont,"Noto Sans Thai",sans-serif;margin:0;width:'+w+';font-size:12px;line-height:1.35}pre{white-space:pre-wrap;margin:0}</style><pre>'+esc(text)+'</pre><script>onload=()=>setTimeout(()=>print(),80)<\/script>');win.document.close()}
 async function bluetoothPrint(text){if(nativeAndroid())throw Error('Bluetooth printer แบบ Web Bluetooth ยังไม่รองรับใน QueueGo Android APK กรุณาใช้ Print Bridge HTTPS');if(!navigator.bluetooth)throw Error('เบราว์เซอร์นี้ไม่รองรับ Web Bluetooth');const s=get();if(!bt){const d=await navigator.bluetooth.requestDevice({acceptAllDevices:true,optionalServices:s.btService?[s.btService]:[]}),server=await d.gatt.connect();let ch=null;if(s.btService&&s.btCharacteristic){ch=await (await server.getPrimaryService(s.btService)).getCharacteristic(s.btCharacteristic)}else{for(const svc of await server.getPrimaryServices()){const chars=await svc.getCharacteristics().catch(()=>[]);ch=chars.find(x=>x.properties.write||x.properties.writeWithoutResponse);if(ch)break}}if(!ch)throw Error('ไม่พบช่องส่งข้อมูลของเครื่องพิมพ์ Bluetooth');bt={d,server,ch}}const bytes=escpos(text);for(let i=0;i<bytes.length;i+=180){const p=bytes.slice(i,i+180);if(bt.ch.properties.writeWithoutResponse&&bt.ch.writeValueWithoutResponse)await bt.ch.writeValueWithoutResponse(p);else await bt.ch.writeValue(p)}}
 async function usbPrint(text){if(nativeAndroid())throw Error('USB printer แบบ WebUSB ยังไม่รองรับใน QueueGo Android APK กรุณาใช้ Print Bridge HTTPS');if(!navigator.usb)throw Error('เบราว์เซอร์นี้ไม่รองรับ WebUSB');if(!usb){const d=await navigator.usb.requestDevice({filters:[]});await d.open();if(!d.configuration)await d.selectConfiguration(1);const iface=d.configuration.interfaces.find(x=>x.alternates.some(a=>a.endpoints.some(e=>e.direction==='out')));if(!iface)throw Error('ไม่พบช่อง USB สำหรับเครื่องพิมพ์');await d.claimInterface(iface.interfaceNumber);const alt=iface.alternates.find(a=>a.endpoints.some(e=>e.direction==='out')),ep=alt.endpoints.find(e=>e.direction==='out');usb={d,ep:ep.endpointNumber}}await usb.d.transferOut(usb.ep,escpos(text))}
 async function bridgePrint(text,type){const s=get();if(!s.bridgeUrl)throw Error('กรุณาใส่ URL ของ LAN / Print Bridge');if(nativeAndroid()&&!/^https:\/\//i.test(s.bridgeUrl))throw Error('Android APK อนุญาต Print Bridge แบบ HTTPS เท่านั้น เพื่อไม่เปิด cleartext ทั้งแอป');const r=await fetch(s.bridgeUrl,{signal:AbortSignal.timeout(15000),method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({type,width:s.width,text,shop_id:shopId,shop_name:shopName})});if(!r.ok)throw Error('Print Bridge ตอบกลับ '+r.status)}
 async function send(text,type){const m=get().mode;if(nativeAndroid()&&m!=='bridge')throw Error('แอป Android ใช้การพิมพ์ผ่าน LAN / Wi‑Fi Print Bridge ในรุ่นนี้');if(m==='bluetooth')return bluetoothPrint(text);if(m==='usb')return usbPrint(text);if(m==='bridge')return bridgePrint(text,type);return browserPrint(text,type==='kitchen'?'QueueGo Kitchen':'QueueGo Receipt')}
 function printOrder(id,type='kitchen',force=false){
   const epoch=generation,shop=shopId;
   const current=()=>epoch===generation&&shop&&shop===shopId;
   const job=queue.then(async()=>{
     if(!current())return;
     const {order,items}=await fetchOrder(id);if(!current())return;
     if(!order)throw Error('ไม่พบบิล');
     const batch=type==='kitchen'?Math.max(0,...items.map(i=>Number(i.pos_batch||0))):undefined;
     if(!force&&was(id,type,batch))return;
     const rows=type==='kitchen'?items.filter(i=>Number(i.pos_batch||0)===batch):items;
     const text=await ticket(order,rows,type);if(!current())return;
     await send(text,type);if(!current())return;
     mark(id,type,batch);toast(type==='kitchen'?'ส่งพิมพ์ใบครัวแล้ว':'ส่งพิมพ์ใบเสร็จแล้ว');
   });
   queue=job.catch(()=>{});return job;
 }
 function realtime(payload){
   const n=payload?.new;if(!shopId||!n?.id||n.shop_id!==shopId)return;
   const s=get();if(s.autoKitchen&&n.kitchen_status==='SENT_TO_KITCHEN')printOrder(n.id,'kitchen').catch(showErr);
   if(s.autoReceipt&&n.payment_status==='PAID')printOrder(n.id,'receipt').catch(showErr);
 }
 function showErr(e){try{toast(String(e?.message||e))}catch(_){}}
 async function recent(type){ensureShop();const filter=type==='receipt'?'payment_status=eq.PAID':'kitchen_status=in.(SENT_TO_KITCHEN,COOKING,READY,SERVED)';return req('orders?select=id,order_number,order_type,table_id,kitchen_status,payment_status,total_amount,created_at&shop_id=eq.'+shopId+'&'+filter+'&order=created_at.desc&limit=20')}
 function close(){document.querySelector('#qg-printer-overlay')?.remove()}
 async function open(){
   try{ensureShop()}catch(e){return showErr(e)}
   close();const s=get(),android=nativeAndroid(),div=document.createElement('section');div.id='qg-printer-overlay';const modes=android?'<option value="bridge">LAN / Wi‑Fi / Print Bridge</option>':'<option value="browser">Browser / ระบบพิมพ์ของเครื่อง</option><option value="bluetooth">Bluetooth</option><option value="usb">USB</option><option value="bridge">LAN / Wi‑Fi / Print Bridge</option>';div.innerHTML='<div class="qgp-box"><div class="qgp-head"><div><b>เครื่องพิมพ์ QueueGo</b><small>'+esc(shopName)+'</small></div><button id="qgp-close">×</button></div>'+(android?'<small>Android: รุ่นนี้รองรับเครื่องพิมพ์ผ่าน LAN / Wi‑Fi Print Bridge เพื่อความเสถียรของ WebView</small>':'')+'<label>การเชื่อมต่อ<select id="qgp-mode">'+modes+'</select></label><label>กระดาษ<select id="qgp-width"><option value="58">58 mm</option><option value="80">80 mm</option></select></label><label class="qgp-check"><input id="qgp-auto-k" type="checkbox"> พิมพ์ใบครัวอัตโนมัติเมื่อมีออเดอร์ใหม่</label><label class="qgp-check"><input id="qgp-auto-r" type="checkbox"> พิมพ์ใบเสร็จอัตโนมัติเมื่อชำระเงิน</label><label>LAN / Print Bridge URL<input id="qgp-bridge" placeholder="http://192.168.1.50:9100/print"></label><details><summary>Bluetooth UUID (ถ้าเครื่องต้องกำหนด)</summary><label>Service UUID<input id="qgp-bs"></label><label>Characteristic UUID<input id="qgp-bc"></label></details><div class="qgp-actions"><button id="qgp-save">บันทึก</button><button id="qgp-test">ทดสอบพิมพ์</button></div><h3>พิมพ์ซ้ำล่าสุด</h3><div class="qgp-tabs"><button data-list="kitchen">ใบครัว</button><button data-list="receipt">ใบเสร็จ</button></div><div id="qgp-list"><small>เลือกประเภทด้านบน</small></div></div>';document.body.append(div);
   div.querySelector('#qgp-mode').value=android?'bridge':s.mode;div.querySelector('#qgp-width').value=s.width;div.querySelector('#qgp-auto-k').checked=s.autoKitchen;div.querySelector('#qgp-auto-r').checked=s.autoReceipt;div.querySelector('#qgp-bridge').value=s.bridgeUrl||'';div.querySelector('#qgp-bs').value=s.btService||'';div.querySelector('#qgp-bc').value=s.btCharacteristic||'';
   div.querySelector('#qgp-close').onclick=close;
   div.querySelector('#qgp-save').onclick=()=>{const mode=div.querySelector('#qgp-mode').value,bridgeUrl=div.querySelector('#qgp-bridge').value.trim();if(mode==='bridge'&&bridgeUrl)try{normalizeBridgeUrl(bridgeUrl)}catch(e){return showErr(e)}save({mode,width:div.querySelector('#qgp-width').value,autoKitchen:div.querySelector('#qgp-auto-k').checked,autoReceipt:div.querySelector('#qgp-auto-r').checked,bridgeUrl,btService:div.querySelector('#qgp-bs').value.trim(),btCharacteristic:div.querySelector('#qgp-bc').value.trim()});toast('บันทึกการตั้งค่าแล้ว')};
   div.querySelector('#qgp-test').onclick=async()=>{div.querySelector('#qgp-save').click();try{await send('QueueGo\n'+shopName+'\nทดสอบเครื่องพิมพ์\n'+new Date().toLocaleString('th-TH')+'\n\n','test')}catch(e){showErr(e)}};
   div.querySelectorAll('[data-list]').forEach(b=>b.onclick=async()=>{const type=b.dataset.list,l=div.querySelector('#qgp-list');l.innerHTML='กำลังโหลด…';try{const rows=await recent(type);l.innerHTML=rows.map(o=>'<button class="qgp-order" data-print="'+o.id+'" data-type="'+type+'"><span><b>'+esc(o.order_number)+'</b><small>'+new Date(o.created_at).toLocaleString('th-TH')+'</small></span><strong>'+money(o.total_amount)+'</strong></button>').join('')||'<small>ยังไม่มีรายการ</small>';l.querySelectorAll('[data-print]').forEach(x=>x.onclick=()=>printOrder(x.dataset.print,x.dataset.type,true).catch(showErr))}catch(e){l.textContent=e.message}});
 }
 function disconnect(){generation++;shopId=null;shopName='ร้านค้า';close();document.querySelector('#qg-printer-btn')?.remove()}
 function mount(shop,name){
   if(shopId!==shop)disconnect();shopId=shop;shopName=name||'ร้านค้า';
   if(document.querySelector('#qg-printer-btn'))return;
   const b=document.createElement('button');b.id='qg-printer-btn';b.textContent='🖨 เครื่องพิมพ์';b.onclick=open;document.body.append(b);
 }
 window.qgPrinterMount=mount;window.qgPrinterDisconnect=disconnect;window.qgPrinterOrderChanged=realtime;
})();
