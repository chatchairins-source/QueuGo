const fs=require('fs'),assert=require('assert');
const merchant=fs.readFileSync('merchant/index.html','utf8');
const must=s=>assert(merchant.includes(s),s);
[
 'qgm-revenue-premium-v1',
 'qgm-revenue-modern',
 'qgm-revenue-hero',
 'qgm-revenue-metrics',
 'qgm-credit-premium',
 'วิเคราะห์ร้านค้า',
 'รายได้ คำสั่งซื้อ ค่าใช้จ่าย และเครดิตร้านค้า',
 'GP ที่หักจากเครดิต',
 'เงินสดค่าสินค้าที่ร้านได้รับ',
 'qgmRevenueMetric',
 'qgmRevenuePickDate'
].forEach(must);
assert(!merchant.includes("b.textContent='วิเคราะห์ร้านค้า'"),'legacy analytics button must not remain on revenue page');
assert(merchant.includes("QueueGoCredit.mount(creditHost,{kind:'merchant'"),'real Merchant credit component must remain mounted');
assert(merchant.includes("qgmExportRevenue(this)"),'CSV export must remain available');
assert(merchant.includes('.qgm-revenue-ranges{grid-template-columns:repeat(4,minmax(0,1fr))'),'approved mobile range controls stay on one row');
assert(merchant.includes('.qgm-revenue-metrics{grid-template-columns:repeat(4,minmax(0,1fr))'),'approved mobile KPI cards stay on one row');
assert(merchant.includes('qgm-credit-head')&&merchant.includes('qgm-credit-head-copy'),'credit heading has dedicated non-overlapping layout');
assert(!merchant.includes("settings.textContent='⚙ ตั้งค่าการใช้งาน'"),'no emoji gear in professional credit controls');
console.log('merchant revenue premium 18 checks passed');
