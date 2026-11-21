'use strict';
const fs = require('node:fs');
const assert = require('node:assert/strict');
const web = fs.readFileSync('merchant/index.html', 'utf8');
const nativeApi = fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantApi.kt', 'utf8');
const nativeRevenue = fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantRevenueScreen.kt', 'utf8');
const reportStart = web.indexOf('window.qgmLoadRevenuePeriod=async function');
const reportEnd = web.indexOf('window.qgmLoadRevenue=async function', reportStart);
const selectedStart = reportEnd;
const selectedEnd = web.indexOf('// Merchant mutations use one durable request UUID', selectedStart);
const exportStart = web.indexOf('window.qgmExportRevenue=async function');
const exportEnd = web.indexOf('\n};', exportStart) + 3;
assert.ok(reportStart >= 0 && reportEnd > reportStart && selectedEnd > selectedStart && exportEnd > exportStart, 'Web period, selected-day, and export revenue paths must exist');
const period = web.slice(reportStart, reportEnd);
const selected = web.slice(selectedStart, selectedEnd);
const csv = web.slice(exportStart, exportEnd);
for (const [source, name] of [[period, 'period report'], [selected, 'selected-day report'], [csv, 'CSV export']]) {
  assert.ok(source.includes('gp_credit_deducted'), `${name} must read credit-deducted GP like Native`);
  assert.ok(source.includes('gp_due'), `${name} must preserve legacy GP separately`);
}
assert.ok(period.includes('GP ที่หักจากเครดิต') && period.includes('GP เดิมก่อนระบบเครดิต'), 'period report must label current and legacy GP separately');
assert.ok(selected.includes('GP ที่หักจากเครดิต') && selected.includes('GP เดิมก่อนระบบเครดิต'), 'selected-day report must label current and legacy GP separately');
assert.ok(nativeApi.includes('gpAmount = r.optDouble("gp_credit_deducted"') && nativeApi.includes('legacyGpDue = r.optDouble("gp_due"'), 'Native and Web must use the same report RPC fields');
assert.ok(nativeRevenue.includes('GP ที่หักจากเครดิต') && nativeRevenue.includes('GP เดิมก่อนระบบเครดิต'), 'Native report labels define the shared GP semantics');
console.log('PASS 8 Merchant revenue Web/Native GP semantics and export checks');
