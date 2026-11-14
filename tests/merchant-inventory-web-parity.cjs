'use strict';
const fs=require('node:fs');
const assert=require('node:assert/strict');
const web=fs.readFileSync('merchant/index.html','utf8');
const nativeApi=fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantApi.kt','utf8');
const nativeUi=fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantProductsScreen.kt','utf8');
const inventory=fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantInventory.kt','utf8');
function section(start,end){const a=web.indexOf(start),b=web.indexOf(end,a);assert.ok(a>=0&&b>a,`missing source section ${start}`);return web.slice(a,b)}
const editor=section('window.renderShopProductEditor=function(id){','let qgmProductSaveBusy=false;');
const save=section('window.qgmSaveProduct=async function(e,id,requestProductId){','let qgmProductDeleteBusy=');
const adjust=section('window.qgmOpenInventoryAdjustment=function(productId){','window.qgmOpenInventoryHistory=');
const history=section('window.qgmOpenInventoryHistory=async function(','window.qgmSaveProduct=');
const keyStart=web.indexOf('function qgmInventoryRequest('),keyEnd=web.indexOf('\n}',keyStart)+2;
assert.ok(keyStart>=0&&keyEnd>keyStart,'stable inventory request-key helper must exist');

assert.ok(web.includes('inventoryStockTracking:p.inventory_stock_tracking===true')&&web.includes('inventoryStockTracking:row.inventory_stock_tracking===true'),'Web hydration and save responses must preserve the tracked-stock flag');
assert.ok(editor.includes('inventoryTracked=p?.inventoryStockTracking===true')&&editor.includes('inventoryTracked?\'readonly aria-readonly="true"\':\'\'')&&editor.includes('p?.grocery_master_id?\'\':`<div class="qgm-inventory-controls"'),'Tracked products must lock direct quantity editing and Grocery Master must own its stock controls');
assert.ok(editor.includes('qgmOpenInventoryAdjustment')&&editor.includes('qgmOpenInventoryHistory')&&editor.includes('id="qgm-prod-stock-tracking"'),'Web editor must expose tracking, audited adjustment, and history actions');
assert.ok(save.includes('inventory_stock_tracking=inventoryStockTracking')&&save.includes('delete body.stock')&&save.includes('existingProduct?.inventoryStockTracking===true&&stock!==Number(existingProduct.stock||0)'),'Web saves must preserve tracking state and prohibit direct stock edits for tracked items');
assert.ok(adjust.includes("'rpc/pos_inventory_stock_adjust'")&&adjust.includes('p_product_id:product.id,p_delta:delta,p_reason:reason,p_note:note,p_request_key:request.requestId')&&adjust.includes('receipt.stock_after'),'Web adjustments must use the existing audited RPC and server-confirmed stock receipt');
assert.ok(adjust.includes('note.length<3||note.length>300')&&adjust.includes('Math.abs(delta)>100000'),'Web adjustment validation must match Native limits');
assert.ok(history.includes("'rpc/pos_inventory_adjustment_history'")&&history.includes('p_limit:40')&&history.includes('p_before_at:beforeAt,p_before_id:beforeId')&&history.includes('page.has_more'),'Web history must use the existing cursor-paged audit RPC');
assert.ok(nativeApi.includes('"pos_inventory_stock_adjust"')&&nativeApi.includes('"pos_inventory_adjustment_history"')&&nativeUi.includes('ปรับสต็อก')&&nativeUi.includes('ประวัติการเคลื่อนไหวสต็อก')&&inventory.includes('p_request_key'),'Android contract remains the parity reference');

const store=new Map();let next=0;
const fakeStorage={getItem:key=>store.get(key)||null,setItem:(key,value)=>store.set(key,String(value)),removeItem:key=>store.delete(key)};
const makeKey=new Function('shopUser','sessionStorage','qtUuidLike','uid',`const qgmInventoryRequestMemory=new Map();${web.slice(keyStart,keyEnd)};return qgmInventoryRequest`)(()=>({id:'merchant-1'}),fakeStorage,value=>/^[0-9a-f-]{36}$/i.test(String(value||'')),()=>`00000000-0000-4000-8000-${String(++next).padStart(12,'0')}`);
const first=makeKey('product-1',{delta:4,reason:'receiving',note:'ใบส่งสินค้า 42'});
assert.equal(makeKey('product-1',{delta:4,reason:'receiving',note:'ใบส่งสินค้า 42'}).requestId,first.requestId,'same retry must reuse the request key');
assert.notEqual(makeKey('product-1',{delta:5,reason:'receiving',note:'ใบส่งสินค้า 42'}).requestId,first.requestId,'changed adjustment intent must receive a new request key');
console.log('PASS 10 Merchant tracked inventory Web parity checks');
