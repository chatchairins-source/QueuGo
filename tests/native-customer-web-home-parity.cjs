'use strict';
const fs=require('node:fs');
const assert=require('node:assert/strict');

const native=fs.readFileSync('native-android/customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt','utf8');
const blueprint=JSON.parse(fs.readFileSync('data/native-web-parity-blueprint.json','utf8'));

assert.equal(blueprint.source_web_ref,'main');
assert.match(blueprint.source_web_sha,/^[0-9a-f]{40}$/);
const webHome=blueprint.customer.home.categories;
assert.deepEqual(webHome.map(x=>x.label),['อาหาร','เครื่องดื่ม','ร้านขายของชำ','ฝากซัก','ตลาดสด','บริการเรียกรถ','ช้อปปิ้ง']);

const block=native.match(/private val homeCategories = listOf\(([\s\S]*?)\n\)/);
assert.ok(block,'Native Customer Home must declare an explicit Web-parity category list');
const nativePairs=[...block[1].matchAll(/"([^"]+)"\s+to\s+"([^"]+)"/g)].map(x=>[x[1],x[2]]);
assert.deepEqual(nativePairs.map(x=>x[0]),webHome.map(x=>x.route));
assert.deepEqual(nativePairs.map(x=>x[1]),webHome.map(x=>x.label));
assert.ok(native.includes('homeCategories.forEach { (key, label) ->'),'Customer Home must render the Web-parity list directly');
assert.ok(!block[1].includes('"express"'),'Express must not create an Android-only Home category tile');
assert.ok(native.includes('"express" -> CustomerExpressScreen('),'Express capability must remain available; this change is Home presentation only');
assert.ok(native.includes('"express" -> screen = "express"'),'Express routing must remain intact for existing entry points');

console.log(JSON.stringify({checks:10,failures:0,scope:'Customer Android Home categories match pinned Web main blueprint; branch-local stale Web markup cannot redefine parity'}));
