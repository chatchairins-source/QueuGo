'use strict';
const fs = require('node:fs');
const assert = require('node:assert/strict');
const native = fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantPosScreen.kt', 'utf8');
const api = fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantPosApi.kt', 'utf8');
const credit = fs.readFileSync('native-android/shared/src/main/java/com/queuego/shared/QueueGoCreditCard.kt', 'utf8');
const tab = native.split('private fun PosTabs(')[1]?.split('private fun PosCounterView(')[0] || '';
const tax = native.split('private fun PosTaxSettingsView(')[1]?.split('private fun PosReportsView(')[0] || '';
let checks = 0;
function ok(condition, description) { assert.ok(condition, description); checks++; }
ok(tab.includes('add("credit" to "เครดิตร้านค้า")'), 'Show real merchant credit in POS navigation');
ok(tab.includes('if (owner) {') && tab.includes('add("tax" to "ภาษี POS")'),
   'Credit and tax owner access must not be widened');
ok(native.includes('QueueGoCreditCard(') && native.includes('kind = "merchant"'),
   'Reuse authenticated credit ledger and topups; never create a second credit account');
ok(credit.includes('queuego_credit_summary') && credit.includes('available_minor'),
   'Credit display must use the existing server-backed available balance');
ok(native.includes('ยอดเครดิตสำหรับค่าบริการ QueueGo · ไม่ใช่เครดิตภาษี'),
   'Avoid conflating merchant platform credit with input VAT credit');
ok(api.includes('"pos_tax_settings?select=') && api.includes('"pos_save_tax_settings"'),
   'Tax settings must use the original server contract');
ok(native.includes('taxSettingsError = posTaxSettingsError(it)'),
   'Loading or saving failures must not be silently ignored');
ok(native.includes('if (!ready) {') && tax.includes('Text(loadError ?:'),
   'Display a readiness error when tax service is unavailable');
ok(tax.includes('OutlinedButton(onClick = onRetry, enabled = !busy)'),
   'A failed tax load must remain retryable');
ok(tax.includes('Button(onClick = onSave, enabled = !busy && ready'),
   'Never allow a tax save while backend readiness is false');
ok(!tax.includes('OutlinedButton(onClick = onRetry, enabled = !busy && ready)'),
   'Tax reload button must not depend on a successful load');
ok(native.includes('PosBillTaxDetails(selectedBill)') &&
   native.includes('PosBillTaxDetails(selected)'),
   'Show bill tax breakdown in both catalog bill and payment screens');
ok(native.includes('if (!bill.taxEnabledSnapshot) return') &&
   native.includes('if (base == null || tax == null)'),
   'No fabricated VAT for legacy bills or missing authoritative snapshots');
ok(api.includes('pos_tax_enabled_snapshot') && api.includes('pos_tax_amount'),
   'POS API must read saved immutable server tax snapshots');
ok(native.includes('internal fun posBillTaxSnapshotComplete(bill: PosBill)'),
   'Tax snapshot validation must be centralized and testable');
ok(native.includes('val canPaySelected = selected != null && posBillTaxSnapshotComplete(selected)'),
   'Cashier must fail closed on incomplete tax-enabled bills');
ok(native.includes('enabled = !busy && posBillTaxSnapshotComplete(selectedBill)'),
   'Catalog checkout must fail closed on incomplete tax-enabled bills');
ok(native.includes('enabled = canSwitchContext && posBillTaxSnapshotComplete(selectedBill)'),
   'Portrait checkout must fail closed on incomplete tax-enabled bills');

console.log(JSON.stringify({checks, failures: 0, scope: 'Native POS credit and VAT readiness'}));
