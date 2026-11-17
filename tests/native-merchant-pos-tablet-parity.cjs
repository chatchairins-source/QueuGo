'use strict';
const fs=require('node:fs'),assert=require('node:assert/strict');
const native=fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantPosScreen.kt','utf8');
const web=fs.readFileSync('merchant/pos-table-v2.css','utf8');
const counter=native.split('private fun PosCounterView(')[1]?.split('private fun PosTablesView(')[0] || '';
let checks=0;
const ok=(condition,message)=>{assert.ok(condition,message); checks++;};
ok(/private fun PosCounterView\(/.test(native),'Native POS counter must exist');
ok(/val desktopPos = maxWidth >= 840\.dp && maxWidth > maxHeight/.test(counter),'Large landscape tablets must use side-by-side POS');
ok(/catalogContent\(Modifier\.fillMaxSize\(\)\.verticalScroll/.test(counter),'Tablet catalog must have its own scroll');
ok(/billContent\(Modifier\.fillMaxSize\(\)\.verticalScroll/.test(counter),'Tablet cart must have its own scroll');
ok(/Modifier\.width\(330\.dp\)\.fillMaxHeight/.test(counter),'Tablet cart must be visible alongside catalog');
ok(/val catalogContent: @Composable \(Modifier\) -> Unit/.test(counter),'Product catalog is shared between layouts');
ok(/val billContent: @Composable \(Modifier\) -> Unit/.test(counter),'Actual bill/payment controls are shared between layouts');
ok(/if \(cartExpanded\)/.test(counter)&&/ดูบิล \/ คิดเงิน/.test(counter),'Portrait POS needs a persistent expandable checkout');
ok(/heightIn\(max = maxHeight \* 0\.62f\)/.test(counter)&&/billContent\(Modifier\.fillMaxWidth\(\)\.verticalScroll/.test(counter),'Expanded bill must be a height-bounded independently scrolling bottom drawer');
ok(/if \(showOrderNote \|\| note\.isNotBlank\(\)\)/.test(counter)&&/＋ เพิ่มหมายเหตุ/.test(counter),'Optional note must not take permanent POS screen space');
ok(/จัดการโต๊ะ \/ QR/.test(counter),'Table management must be accessible from compact table toolbar');
ok(/selectedBill\.total/.test(counter),'Cart summary must reflect authoritative selected bill total');
for(const contract of ['onProduct(product)', 'onOpenBill(bill)', 'onPay', 'onDiscount', 'onCancel',
                         'onReduce(line)', 'onBillAction("send"', 'onBillAction("serve"']) {
    ok(counter.includes(contract),'Preserve existing POS operation: '+contract);
}
ok(/val tabHeight = if \(mobile\) 48\.dp/.test(native),'Tabs need accessible 48dp targets');
ok(/val labelSize = if \(mobile\) 14\.sp/.test(native),'Tablet/phone tab labels must remain readable');
ok(/Text\("＋", fontSize = 19\.sp/.test(counter),'Compact red add affordance must remain visible on product cards');
ok(/\.semantics \{ contentDescription = "เพิ่ม \$\{product\.name\} ลงบิล" \}/.test(counter),'Product tile must expose an accessible add-to-bill label');
ok(/qg-pos-cart-toggle/.test(web)&&/qg-pos-grid/.test(web),'Web layout remains the blueprint');
console.log(JSON.stringify({checks,failures:0,scope:'Native POS responsive landscape split / portrait expandable checkout with Web parity controls'}));
