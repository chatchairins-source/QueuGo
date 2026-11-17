'use strict';
const fs=require('node:fs'),assert=require('node:assert/strict');
const native=fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantPosScreen.kt','utf8');
const web=fs.readFileSync('merchant/pos-table-v2.css','utf8');
const counter=native.split('private fun PosCounterView(')[1]?.split('private fun PosTablesView(')[0] || '';
let checks=0;
const ok=(condition,message)=>{assert.ok(condition,message); checks++;};
ok(/private fun PosCounterView\(/.test(native),'Native POS counter must exist');
const choice = native.split('private fun PosChoiceButton(')[1]?.split('private fun PosReportLine(')[0] || '';
ok(/val target = modifier\.heightIn\(min = 48\.dp\)/.test(choice)&&
   /fontSize = 14\.sp, maxLines = 2/.test(choice)&&
   /Button\(onClick = onClick, enabled = enabled, modifier = target\)/.test(choice)&&
   /OutlinedButton\(onClick = onClick, enabled = enabled, modifier = target\)/.test(choice),
   'Product category and table chips need readable 14sp labels and accessible 48dp hit targets');

ok(/val desktopPos = maxWidth >= 680\.dp && maxWidth > maxHeight/.test(counter),'Landscape tablets must keep cart visible after the Merchant navigation rail');
ok(/catalogContent\(Modifier\.fillMaxSize\(\)\.verticalScroll/.test(counter),'Tablet catalog must have its own scroll');
ok(/billContent\(Modifier\.fillMaxSize\(\)\.verticalScroll/.test(counter),'Tablet cart must have its own scroll');
ok(/val cartPanelWidth = if \(maxWidth >= 1000\.dp\) 330\.dp else 280\.dp/.test(counter)&&/Modifier\.width\(cartPanelWidth\)\.fillMaxHeight/.test(counter),'Tablet cart panel must resize and stay alongside the product catalog');
ok(/val catalogContent: @Composable \(Modifier\) -> Unit/.test(counter),'Product catalog is shared between layouts');
ok(/val billContent: @Composable \(Modifier\) -> Unit/.test(counter),'Actual bill/payment controls are shared between layouts');
ok(/if \(cartExpanded\)/.test(counter)&&/onClick = \{ cartExpanded = !cartExpanded \}/.test(counter)&&/ดูบิล/.test(counter),
   'Portrait POS must keep the independently expandable bill accessible at all times');
ok(/if \(selectedBill != null && canClose\)/.test(counter)&&
   /onClick = onPay,\s*enabled = canSwitchContext &&/.test(counter)&&
   /!kitchenEnabled \|\| selectedBill\.kitchenStatus in setOf\("READY", "SERVED"\)/.test(counter)&&
   /Text\("คิดเงิน", fontSize = 14\.sp, maxLines = 1\)/.test(counter),
   'Phone POS checkout requires an authorized ready bill and no unresolved POS edits');
ok(/maxLines = 1, overflow = androidx\.compose\.ui\.text\.style\.TextOverflow\.Ellipsis/.test(counter),
   'Compact checkout title must truncate instead of overlapping its actual controls');
ok(/import androidx\.compose\.foundation\.layout\.imePadding/.test(native)&&
   /Modifier\.align\(Alignment\.BottomCenter\)\.fillMaxWidth\(\)\s*\.imePadding\(\)/.test(counter),
   'Mobile POS bill and checkout must rise above the software keyboard');

ok(/val cartDrawerMaxHeight = maxHeight \* 0\.62f/.test(counter)&&/heightIn\(max = cartDrawerMaxHeight\)/.test(counter)&&/billContent\(Modifier\.fillMaxWidth\(\)\.verticalScroll/.test(counter),'Expanded bill must be a height-bounded independently scrolling bottom drawer');
ok(/if \(showOrderNote \|\| note\.isNotBlank\(\)\)/.test(counter)&&/＋ เพิ่มหมายเหตุ/.test(counter),'Optional note must not take permanent POS screen space');
ok(/จัดการโต๊ะ \/ QR/.test(counter),'Table management must be accessible from compact table toolbar');
ok(/selectedBill\.total/.test(counter),'Cart summary must reflect authoritative selected bill total');
ok(/canSwitchContext = !busy && pendingPosRequestId == null &&\s*pendingEdit == null && pendingEditError == null/.test(native)&&
   /canSwitchContext: Boolean/.test(counter),
   'POS context-changing controls must cover unresolved edits and pending creates, not only a busy network call');
ok(/onMode = \{ next ->[\s\S]*?if \(busy \|\| pendingPosRequestId != null \|\| pendingEdit != null \|\| pendingEditError != null\)/.test(native)&&
   /onTable = \{ id ->[\s\S]*?if \(busy \|\| pendingPosRequestId != null \|\| pendingEdit != null \|\| pendingEditError != null\)/.test(native)&&
   /onNoTable = \{[\s\S]*?if \(busy \|\| pendingPosRequestId != null \|\| pendingEdit != null \|\| pendingEditError != null\)/.test(native),
   'Host callbacks must reject mode and table changes while a bill mutation or uncertain creation is pending');
ok(/PosChoiceButton\("🍽 ทานที่ร้าน",[\s\S]*?enabled = canSwitchContext/.test(counter)&&
   /PosChoiceButton\("🛍 รับกลับ",[\s\S]*?enabled = canSwitchContext/.test(counter)&&
   /PosChoiceButton\("ไม่ระบุโต๊ะ",[\s\S]*?enabled = canSwitchContext/.test(counter)&&
   /tableId == table.id,\s*enabled = canSwitchContext/.test(counter)&&
   /enabled: Boolean = true/.test(choice),
   'Actual POS mode/table buttons must disable while a bill mutation is outstanding');

const tablesView = native.split('private fun PosTablesView(')[1]?.split('private fun PosKitchenView(')[0] || '';
ok(/canSwitchContext = !busy && pendingPosRequestId == null &&\s*pendingEdit == null && pendingEditError == null/.test(native)&&
   /canSwitchContext: Boolean/.test(tablesView)&&
   /enabled = \(table\.active \|\| bill != null\) && canSwitchContext/.test(tablesView)&&
   /onClick = onNoTable,\s*enabled = canSwitchContext/.test(tablesView),
   'Table grid opening and no-table navigation must obey unresolved POS edit guards');
ok(/"tables" -> PosTablesView\([\s\S]*?onOpen = \{ table ->\s*if \(busy \|\| pendingPosRequestId != null \|\| pendingEdit != null \|\| pendingEditError != null\)/.test(native)&&
   /"tables" -> PosTablesView\([\s\S]*?onNoTable = \{\s*if \(busy \|\| pendingPosRequestId != null \|\| pendingEdit != null \|\| pendingEditError != null\)/.test(native),
   'Table selection callbacks must fail closed during unresolved bills even when invoked programmatically');
const billsView = native.split('private fun PosBillsView(')[1]?.split('private fun PosShiftsView(')[0] || '';
ok(/LaunchedEffect\(auth\.session\.authUserId, selectedBillId\) \{\s*cashReceived = ""\s*\}/.test(native),
   'Cash tender must also reset on table navigation, restored bill selection and shop-account change');
ok(/onOpenBill = \{ bill ->[\s\S]*?if \(selectedBillId != bill\.id\) cashReceived = ""/.test(native)&&
   /onSelect = \{ bill ->[\s\S]*?if \(selectedBillId != bill\.id\) cashReceived = ""/.test(native),
   'Switching a POS bill from either counter or cashier must clear the previous cash tender');

ok(/onOpenBill = \{ bill ->[\s\S]*?pendingPosRequestId != null \|\| pendingEdit != null \|\| pendingEditError != null/.test(native)&&
   /\.clickable\(enabled = canSwitchContext\) \{ onOpenBill\(bill\) \}/.test(counter),
   'Counter must keep the selected bill fixed during an unresolved create or edit');
ok(/onSelect = \{ bill ->[\s\S]*?cashShiftPending \|\| pendingPosRequestId != null/.test(native)&&
   /\.clickable\(enabled = !busy && !shiftPending\) \{ onSelect\(bill\) \}/.test(billsView),
   'Cashier must keep the selected bill fixed during payment and cash-shift operations');

ok(/onClick = \{ onReduce\(line\) \}/.test(counter)&&
   /onClick = \{ onProduct\(matchingProduct\) \}/.test(counter)&&
   /!posProductSoldOut\(matchingProduct\)/.test(counter)&&
   /selectedBill\.kitchenStatus == "NEW"/.test(counter),
   'Bill +/- controls must use real guarded POS mutations, never locally fabricated quantities');

for(const contract of ['onProduct(product)', 'onOpenBill(bill)', 'onPay', 'onDiscount', 'onCancel',
                         'onReduce(line)', 'onBillAction("send"', 'onBillAction("serve"']) {
    ok(counter.includes(contract),'Preserve existing POS operation: '+contract);
}
ok(/val wideWorkspace = maxWidth >= 1000\.dp && maxWidth > maxHeight/.test(native),'Landscape workspace must use large-screen layout without treating portrait tablets as desktops');
ok(/PosTabs\([\s\S]*?vertical = true/.test(native),'Large screens must render actual native POS navigation rail');
ok(/Color\(0xFF181B22\)/.test(native),'Navigation rail must preserve the reference charcoal-black palette');
ok(/Text\(\s*snap\.shopName,/.test(native)&&/screenWidthDp >= 700/.test(native)&&/TextOverflow\.Ellipsis/.test(native),
   'Tablet header must display the authenticated shop name without inventing multi-branch switching');
ok(/vertical = false/.test(native),'Phones and portrait tablets must retain horizontal navigation');
ok(/canOpenDelivery = snap\.owner/.test(native)&&/onOpenDelivery = \{[\s\S]*?pendingEdit == null && pendingEditError == null[\s\S]*?view = "delivery"/.test(native),'Delivery shortcut must navigate into the existing authorized module');
ok(/PosChoiceButton\("🍽 ทานที่ร้าน"/.test(counter)&&/onMode\("DINE_IN"\)/.test(counter),'Dine-in button must wire to original POS order mode');
ok(/PosChoiceButton\("🛍 รับกลับ"/.test(counter)&&/onMode\("TAKEAWAY"\)/.test(counter),'Takeaway button must wire to original POS order mode');
ok(/val tabHeight = if \(mobile\) 48\.dp/.test(native),'Tabs need accessible 48dp targets');
ok(/val labelSize = if \(mobile\) 14\.sp/.test(native),'Tablet/phone tab labels must remain readable');
ok(/Text\("＋", fontSize = 19\.sp/.test(counter),'Compact red add affordance must remain visible on product cards');
ok(/\.semantics \{ contentDescription = "เพิ่ม \$\{product\.name\} ลงบิล" \}/.test(counter),'Product tile must expose an accessible add-to-bill label');
ok(/qg-pos-cart-toggle/.test(web)&&/qg-pos-grid/.test(web),'Web layout remains the blueprint');

const standaloneStaff = fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MainActivity.kt','utf8');
ok(/unreadNotifications = 0/.test(standaloneStaff)&&/onNavigateApp = \{\}/.test(standaloneStaff)&&/showMerchantAppShortcuts = false/.test(standaloneStaff),
   'Standalone POS staff host must compile without pretending to provide Merchant-app navigation');
ok(/if \(showMerchantAppShortcuts\)/.test(native)&&
   (native.match(/showMerchantAppShortcuts = showMerchantAppShortcuts/g)||[]).length === 2,
   'Host-only sidebar links must be hidden for standalone POS staff and kept for the authenticated Merchant shell');

const tabs = native.split('private fun PosTabs(')[1]?.split('private fun PosCounterView(')[0] || '';
ok(/val shortcuts = buildList/.test(tabs)&&/if \(showMerchantAppShortcuts\)/.test(tabs)&&
   /if \(shortcuts\.isNotEmpty\(\)\)/.test(tabs)&&
   /DropdownMenu\(/.test(tabs)&&/DropdownMenuItem\(/.test(tabs)&&
   /onNavigateApp\(route\)/.test(tabs),
   'Phone/tablet Merchant POS must expose the same real authorized shop shortcuts and hide them for standalone staff');
ok(/onNavigateApp: \(String\) -> Unit/.test(native),'The POS must accept authenticated host navigation');
ok(/shortcuts\.forEach/.test(native)&&/onNavigateApp\(route\)/.test(native),'Sidebar shortcuts must open existing Merchant routes');
ok(/add\("orders" to "ออเดอร์ออนไลน์"\)/.test(native),'Online orders shortcut must be available');
ok(/add\("products" to "สินค้า"\)/.test(native),'Products shortcut must be available');
ok(/if \(stockEnabled\) add\("market-stock" to "สต็อกสินค้า"\)/.test(native),'Stock shortcut must respect shop type');
ok(/add\("profile" to "ตั้งค่าร้าน"\)/.test(native),'Settings shortcut must be available');
const merchantHost = fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt','utf8');
ok(/onNavigateApp = \{ destination ->/.test(merchantHost),'Existing merchant host must handle POS route callbacks');
ok(/destination in setOf\("orders", "products", "profile", "notifications"\)/.test(merchantHost),'POS route callback must be allowlisted');
ok(/unreadNotifications = notifications\.count \{ !it\.isRead \}/.test(merchantHost),'POS must receive the actual Merchant notification count');
ok(/add\("notifications" to if \(unreadNotifications > 0\)/.test(native),'Sidebar must show a real unread notification count');
ok(/if \(connected\) "● เชื่อมต่อออนไลน์" else "● ไม่มีอินเทอร์เน็ต"/.test(native),'Connectivity status must follow actual POS connectivity state');
ok(/destination == "market-stock" && shop\?\.let \{ merchantMarketStockPermitted\(it\.category\) \}/.test(merchantHost),'Stock shortcut must block ineligible shops');
const reportView = native.split('private fun PosReportsView(')[1]?.split('private fun PosHistoryView(')[0] || '';
ok(/report\.hourlySales\.sortedBy/.test(reportView),'Hourly chart must use real owner dashboard hourly values');
ok(/showHourlyDetail/.test(reportView),'Hourly details must remain reachable on demand');
ok(/height = if \(maxHourly > 0\.0\)/.test(reportView),'Bar height must normalize against real maximum');
ok(/report\.dineIn \+ report\.takeaway/.test(reportView),'Report total must be computed from actual POS dashboard fields');
ok(!/Text\("กำไรสุทธิ"/.test(reportView),'Do not invent profit without actual cost-backed calculation');

console.log(JSON.stringify({checks,failures:0,scope:'Native POS responsive landscape split / portrait expandable checkout with Web parity controls'}));
