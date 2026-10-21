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
assert(merchant.includes('qgm-credit-head-add'),'top-up control stays in the approved credit header position');
assert(merchant.includes("historyButton.textContent='ประวัติรายการ'"),'credit actions include a real history shortcut');
assert(merchant.includes("grid-template-columns:repeat(3,minmax(0,1fr));gap:6px"),'mobile credit actions stay on one compact row');
assert(merchant.includes('qgm-revenue-layout-v4'),'revenue page has dedicated layout layer');
assert(merchant.includes('qgm-revenue-store'),'current store context is part of the title row');
assert(merchant.includes('qgm-credit-history-card'),'credit history is a separate card, not inside the balance card');
assert(merchant.includes('qgm-credit-live-status')&&merchant.includes('new MutationObserver(syncStatus).observe(status'),'credit status is mirrored outside controls without breaking live refresh');
assert(merchant.includes("document.body.classList.add('qgm-revenue-page')"),'revenue-only shell spacing is scoped');
assert(merchant.includes("historyList.className='qgm-credit-history-list'")&&merchant.includes("new MutationObserver(syncHistory).observe(entries,{childList:true})"),'history layout mirrors live entries without moving the component source');
assert(merchant.includes("qgm-wordmark"),'revenue header includes QueueGo wordmark');
console.log('merchant revenue premium 28 checks passed');
