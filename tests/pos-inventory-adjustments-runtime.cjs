'use strict';
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('node:assert/strict');
const {JSDOM}=require('jsdom'),root=path.resolve(__dirname,'..');
const html=fs.readFileSync(path.join(root,'merchant/index.html'),'utf8');
const product='10000000-0000-4000-8000-000000000001';
(async()=>{
 const dom=new JSDOM(html,{url:'https://queuego.test/merchant/#shop-product/'+product,runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window,ctx=dom.getInternalVMContext();let calls=0;
 w.fetch=async()=>({ok:true,json:async()=>[]});w.setInterval=w.setTimeout=()=>0;w.requestAnimationFrame=()=>0;w.AbortSignal=AbortSignal;w.alert=()=>{};w.confirm=()=>true;
 w.crypto.randomUUID=()=>`60000000-0000-4000-8000-${String(++calls).padStart(12,'0')}`;
 for(const script of w.document.querySelectorAll('script')){let code=script.textContent;if(script.src){const u=new URL(script.src);if(u.hostname!=='queuego.test')continue;code=fs.readFileSync(path.join(root,u.pathname),'utf8')}if(code.trim())vm.runInContext(code,ctx)}
 const run=code=>vm.runInContext(code,ctx);await Promise.resolve();
 const modal=w.document.createElement('div');modal.id='qgm-inventory-adjustment-dialog';modal.hidden=true;modal.innerHTML='<section class="qgm-stock-modal-card"><p></p><form id="qgm-inventory-adjustment-form"><input id="qgm-stock-delta" value="2"><select id="qgm-stock-reason"><option value="receiving" selected>รับเข้า</option></select><input id="qgm-stock-note" value="รับเข้าเอกสาร PO-7"><div id="qgm-stock-adjustment-status" role="status"></div><button type="submit">บันทึกการปรับ</button></form></section>';w.document.body.append(modal);
 const stockInput=w.document.createElement('input');stockInput.id='qgm-prod-stock';stockInput.value='4';w.document.body.append(stockInput);
 const history=w.document.createElement('div');history.id='qgm-stock-history-'+product;history.className='qgm-stock-history';w.document.body.append(history);w.setTimeout=fn=>{fn();return 0};
 run(`currentUser=()=>({id:'shop-user',type:'shop',shopProfileId:'shop-id',category:'food'});shopUser=()=>({id:'shop-user',type:'shop',shopProfileId:'shop-id',category:'food'});qtGetAccessToken=async()=> 'access-token';dbGet=key=>key==='qt_products'?QT_DB_CACHE.qt_products:[];qtSanitizeCachedProducts=x=>x;qtMarkClean=()=>{};qgSaveMerchantCache=()=>{};qtBackgroundHydrate=async()=>{};toast=()=>{};QT_DB_CACHE.qt_products=[{id:'${product}',shopProfileId:'shop-id',stock:4}];window.adjustmentRequests=[];qtSupabaseTable=async(path,options)=>{if(path==='rpc/pos_inventory_stock_adjust'){window.adjustmentRequests.push(options.body);if(window.adjustmentRequests.length===1)throw Error('isolated timeout');return {stock_after:6,replayed:true}}if(path==='rpc/pos_inventory_adjustment_history')return {entries:[{id:'70000000-0000-4000-8000-000000000001',event_type:'manual_adjustment',delta:2,stock_before:4,stock_after:6,reason:'receiving',note:'<unsafe>',actor_name:'Owner',created_at:'2026-10-21T10:00:00Z'}],has_more:false};throw Error('unexpected route '+path)};`);
 run(`qgmOpenInventoryAdjustment('${product}',4)`);assert.equal(modal.hidden,false);assert.equal(w.document.activeElement.id,'qgm-stock-delta');
 const form=w.document.getElementById('qgm-inventory-adjustment-form');let action;form.addEventListener('submit',e=>{action=w.qgmSubmitInventoryAdjustment(e,product)});
 form.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));await action;
 assert.match(w.document.getElementById('qgm-stock-adjustment-status').textContent,/ตรวจคำขอเดิม/);
 form.dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));await action;
 const sent=run('window.adjustmentRequests');assert.equal(sent.length,2);assert.equal(sent[0].p_request_key,sent[1].p_request_key,'retry must reuse the idempotency key');
 assert.equal(stockInput.value,'6');assert.equal(run('QT_DB_CACHE.qt_products[0].stock'),6);
 await w.qgmLoadInventoryAdjustmentHistory(product);assert.match(history.textContent,/ปรับสต็อกโดยพนักงาน/);assert.match(history.textContent,/4 → 6 ชิ้น/);assert.equal(history.querySelector('unsafe'),null,'history note must be escaped');
 assert.equal(w.document.querySelector('#qgm-stock-history-'+product+' script'),null);
 console.log(JSON.stringify({checks:9,failures:0,scope:'merchant stock adjustment UI retry idempotency, cache refresh, accessible modal and escaped history'}));
 dom.window.close();
})().catch(err=>{console.error(err);process.exit(1)});
