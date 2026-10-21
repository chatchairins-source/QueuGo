const fs=require('fs'),assert=require('assert');
const merchant=fs.readFileSync('merchant/index.html','utf8');
assert(!merchant.includes('class="qgm-revenue-entry"'),'Merchant Home must not render a duplicate revenue card');
assert(!merchant.includes('<strong>รายได้ทั้งหมด</strong>'),'Merchant Home must not render duplicate รายได้ทั้งหมด copy');
assert(!merchant.includes('id="qgm-delivery-readiness"'),'Merchant Home must not render the Delivery readiness card');
assert(merchant.includes("['shop-settlement','chart','รายงาน']"),'Merchant Home must keep the Reports menu icon');
assert(merchant.includes("qgmLoadShopOpen()"),'Merchant Home must keep real open/close state loading');
assert(merchant.includes("window.qgmSetShopOpen=async function"),'Merchant Home must keep the real shop open/close control');
console.log('merchant home clean layout 6 checks PASS');
