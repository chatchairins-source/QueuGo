const fs=require('fs'),assert=require('assert'),vm=require('vm');
const read=p=>fs.readFileSync(p,'utf8');
const context={window:{}};
vm.runInNewContext(read('queuego-order-number.js'),context,{filename:'queuego-order-number.js'});
const format=context.window.QueueGoOrderNumber.format;
const replaceInText=context.window.QueueGoOrderNumber.replaceInText;
assert.equal(format('QT-20261006-2141'),'QT-2141');
assert.equal(format('QT-001'),'QT-0001');
assert.equal(format({order_number:'QT-20261006-7015'}),'QT-7015');
assert.equal(format({orderNumber:'QT-9198'}),'QT-9198');
assert.equal(format({orderNumber:'QT-9198'}),'QT-9198');
assert.equal(format({order_number:'LW-20260930-112419-e26b'}),'QT-E26B');
assert.equal(format({id:'abc-def-1234'}),'QT-----');
assert.equal(format({id:'d889d1a6-c10d-4f61-a6c8-31312dd6cbd1'}),'QT-----');
assert.equal(format('d889d1a6-c10d-4f61-a6c8-31312dd6cbd1'),'QT-----','UUID string must not become an order code');
assert.equal(replaceInText('กรุณาตรวจสอบคำสั่งซื้อ QT-20261006-2141'),'กรุณาตรวจสอบคำสั่งซื้อ QT-2141');
assert.equal(replaceInText('ออเดอร์ QT-9198 พร้อมส่ง'),'ออเดอร์ QT-9198 พร้อมส่ง');
assert(format('QT-20261006-2141').startsWith('QT-'),'visible order codes must always emit QT');
assert(!read('queuego-order-number.js').includes("return 'QO-'"),'shared formatter must not emit QO');

const customer=read('index.html');
const merchant=read('merchant/index.html');
const rider=read('rider/index.html');
const admin=read('admin/index.html');
const customerFeatures=read('customer-features.js');
const customerLaundry=read('customer-laundry.js');
const release=read('.github/workflows/build-queuego-apks.yml');
const pilot=read('.github/workflows/build-queuego-pilot-apks.yml');

assert(customer.includes('src="queuego-order-number.js"'));
for(const source of [merchant,rider,admin])assert(source.includes('src="../queuego-order-number.js"'));
assert(customer.includes('customerOrderNumber=o=>QueueGoOrderNumber.format(o)'));
assert(merchant.includes('function displayOrderNumber(order){return QueueGoOrderNumber.format(order);}'));
assert(rider.includes('function qgShortOrder(o){return QueueGoOrderNumber.format(o);}'));
assert(admin.includes('function displayOrderNumber(order){return QueueGoOrderNumber.format(order);}'));
assert(admin.includes('QueueGoOrderNumber.format(x.order_number)'));
assert(admin.includes('QueueGoOrderNumber.format(o),shopName'));
assert(!customerFeatures.includes('<h1>#${esc(customerOrderNumber(o))}</h1>'));
assert(customerLaundry.includes('QueueGoOrderNumber.format(o)'));
assert(customerLaundry.includes('QueueGoOrderNumber.format(l)'));
assert(!rider.includes('#${esc(j.order_number||\'\')}'));
assert(rider.includes('qgShortOrder({order_number:o.order_number,id:o.order_id})'));
assert(merchant.includes('escText(displayOrderNumber(o))'));
for(const workflow of [release,pilot]){
  assert(workflow.includes('../queuego-order-number.js'));
  assert(workflow.includes("s=s.replace('../queuego-order-number.js','queuego-order-number.js')"));
}
for(const source of [customer,merchant,rider,admin]){
  assert(!source.includes('QT-YYYYMMDD-xxxx'));
}
console.log(JSON.stringify({checks:20,failures:0,scope:'Unified visible order code QT-XXXX across Customer, Merchant, Rider and Admin'}));
