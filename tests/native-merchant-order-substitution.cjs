const fs=require('fs'),assert=require('assert');

const read=p=>fs.readFileSync(p,'utf8');
const migration=read('supabase/migrations/20261008072000_merchant_order_item_substitution.sql');
const api=read('native-android/merchant/src/main/java/com/queuego/merchant/MerchantApi.kt');
const ui=read('native-android/merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt');

let checks=0;
const ok=(v,m)=>{assert.ok(v,m);checks++};

ok(migration.includes('CREATE OR REPLACE FUNCTION public.qg_merchant_edit_order_items'),'Production mirror must retain merchant substitution RPC');
ok(migration.includes("v_order.status<>'rider_assigned' or v_order.rider_id is null"),'Backend must allow edit only after Rider assignment and before preparation');
ok(migration.includes("coalesce(v_order.fulfillment_vertical,'food')<>'food'"),'Backend must limit this editor to ordinary food delivery');
ok(migration.includes('jsonb_array_length(p_items) not between 1 and 30'),'Backend must keep item-count bounds');
ok(migration.includes("coalesce(v_item->>'qty','') !~ '^[1-9][0-9]?$'"),'Backend must keep quantity 1..99');
ok(migration.includes('p.shop_id=v_order.shop_id')&&migration.includes('p.available=true')&&migration.includes('p.delivery_available=true'),'Substitute products must belong to the same shop and be Delivery-available');
ok(migration.includes('replacement total cannot exceed customer-authorized subtotal'),'Backend must enforce customer-authorized merchandise ceiling');
ok(migration.includes('qg_order_item_adjustments'),'Backend must audit order-item substitutions');
ok(migration.includes("'ร้านปรับรายการสินค้า'"),'Backend must notify customer/rider about substitution');

ok(api.includes('select=id,product_id,item_name,description,quantity,unit_price,total_price,item_image'),'Native Merchant must load editable item identity and unit price');
ok(api.includes('data class MerchantOrderEditItem'),'Native Merchant must model edit payloads');
ok(api.includes('qg_merchant_edit_order_items'),'Native Merchant must call the Production substitution RPC');
ok(api.includes('require(items.size in 1..30)'),'Native API must mirror item-count bounds before network');
ok(api.includes('require(item.quantity in 1..99)'),'Native API must mirror quantity bounds before network');
ok(api.includes('(item.itemId.isNullOrBlank()) xor (item.productId.isNullOrBlank())'),'Each edit row must identify exactly an existing item or a substitute product');
ok(api.includes('"p_reason", reason.trim().take(200)'),'Native API must keep substitution reason bounded');

ok(ui.includes('private fun MerchantOrderItemEditor('),'Native Merchant must expose an order-item editor');
ok(ui.includes('Text("แก้ไขรายการ")'),'Assigned Merchant order must expose the editor entry point');
ok(ui.includes('order.status !in setOf("rider_assigned", "assigned")'),'Realtime status advancement must close a stale order editor');
ok(ui.includes('ลดจำนวน ลบ หรือเลือกสินค้าทดแทนจากร้านได้ก่อนเริ่มเตรียมสินค้า'),'Native editor must retain Production Web substitution guidance');
ok(ui.includes('item.copy(quantity = (item.quantity - 1).coerceAtLeast(1))'),'Native editor must support decrementing quantity with floor 1');
ok(ui.includes('item.copy(quantity = (item.quantity + 1).coerceAtMost(99))'),'Native editor must support incrementing quantity with ceiling 99');
ok(ui.includes('draft = draft.filterIndexed'),'Native editor must support removing an item');
ok(ui.includes('it.available && it.deliveryAvailable'),'Native substitute chooser must only show available Delivery products');
ok(ui.includes('draft.none { row -> !row.productId.isNullOrBlank() && row.productId == it.id }'),'Native substitute chooser must reject duplicate products');
ok(ui.includes('draft.size < 30'),'Native editor must keep the backend 30-row maximum');
ok(ui.includes('Text("เพิ่มสินค้าทดแทน")'),'Native editor must support adding a substitute');
ok(ui.includes('Text("ยอดค่าสินค้าใหม่"'),'Native editor must show the new merchandise subtotal');
ok(ui.includes('Text("บันทึกการแก้ไข")'),'Native editor must require an explicit save action');
ok(ui.includes('api.editOrderItems(auth, current.id, draft)'),'Merchant shell must send edits through the Production RPC wrapper');
ok(ui.includes('val freshItems = api.loadOrderItems(auth, current.id)'),'Merchant shell must refresh authoritative items after save');
ok(!/"preparing" -> \{[\s\S]{0,1000}Text\("แก้ไขรายการ"\)/.test(ui),'Order editor must remain absent after preparation begins');

console.log(JSON.stringify({checks,failures:0,scope:'Native Merchant ordinary-food order substitution parity with Production Web/RPC; physical visual parity remains OPEN'}));
