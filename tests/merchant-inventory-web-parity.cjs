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
const adjust=section('window.qgmSubmitInventoryAdjustment=async function','document.addEventListener');
const history=section('window.qgmLoadInventoryAdjustmentHistory=async function','window.qgmSubmitInventoryAdjustment=');
assert.ok(web.includes('inventoryStockTracking:p.inventory_stock_tracking===true')&&web.includes('inventoryStockTracking:row.inventory_stock_tracking===true'),'Web hydration and save responses must preserve the tracked-stock flag');
assert.ok(editor.includes('readonly')&&editor.includes('inventoryStockTracking')&&editor.includes('p?.grocery_master_id'),'Tracked products must lock direct editing and Grocery Master must own its stock controls');
assert.ok(editor.includes('qgmOpenInventoryAdjustment')&&editor.includes('qgm-stock-history-')&&editor.includes('id="qgm-stock-tracking"'),'Web editor must expose tracking, audited adjustment, and history');
assert.ok(save.includes('inventory_stock_tracking:inventoryStockTracking')&&save.includes('delete body.stock')&&save.includes('existingProduct?.inventoryStockTracking&&stock!==Number(existingProduct.stock??0)'),'Web saves must preserve tracking state and prohibit direct stock edits');
assert.ok(adjust.includes("'rpc/pos_inventory_stock_adjust'")&&adjust.includes('p_product_id:productId,p_delta:delta,p_reason:reason,p_note:note,p_request_key:requestKey')&&adjust.includes('result?.stock_after'),'Web adjustments must use the audited RPC and confirmed receipt');
assert.ok(adjust.includes('note.length<3||note.length>300')&&adjust.includes('Math.abs(delta)>100000'),'Web adjustment validation must match Native limits');
assert.ok(history.includes("'rpc/pos_inventory_adjustment_history'")&&history.includes('p_limit:40')&&history.includes('p_before_at:beforeAt,p_before_id:beforeId')&&history.includes('result?.has_more'),'Web history must use cursor pagination');
assert.ok(nativeApi.includes('"pos_inventory_stock_adjust"')&&nativeApi.includes('"pos_inventory_adjustment_history"')&&nativeUi.includes('ปรับสต็อก')&&nativeUi.includes('ประวัติการเคลื่อนไหวสต็อก')&&inventory.includes('p_request_key'),'Android contract remains the parity reference');

// Run the browser-style fixture for actual retry-key and rendered history behavior.
require('./pos-inventory-adjustments-runtime.cjs');
