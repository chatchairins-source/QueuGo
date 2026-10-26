'use strict';
const fs=require('node:fs');
const assert=require('node:assert/strict');

const native=fs.readFileSync('native-android/customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt','utf8');
const web=fs.readFileSync('index.html','utf8');

const webHome=['อาหาร','เครื่องดื่ม','ร้านขายของชำ','ฝากซัก','ตลาดสด','บริการเรียกรถ','ช้อปปิ้ง'];
const webHomeMarkup=web.slice(web.indexOf('<section class="sec"><div class="cats">'), web.indexOf('</div></section>',web.indexOf('<section class="sec"><div class="cats">'))+16);
const webLabels=[...webHomeMarkup.matchAll(/<b>([^<]+)<\/b>/g)].map(x=>x[1]);
assert.deepEqual(webLabels,webHome,'Web Customer Home category blueprint changed; reconcile Native intentionally');

const block=native.match(/private val homeCategories = listOf\(([\s\S]*?)\n\)/);
assert.ok(block,'Native Customer Home must declare an explicit Web-parity category list');
const nativePairs=[...block[1].matchAll(/"([^"]+)"\s+to\s+"([^"]+)"/g)].map(x=>[x[1],x[2]]);
assert.deepEqual(nativePairs.map(x=>x[1]),['food','cafe','grocery','laundry','market','ride','shopping']);
assert.deepEqual(nativePairs.map(x=>x[2]),webHome);
assert.ok(native.includes('homeCategories.forEach { (key, label) ->'),'Customer Home must render the Web-parity list directly');
assert.ok(!block[1].includes('"express"'),'Express must not create an Android-only Home category tile');
assert.ok(native.includes('"express" -> CustomerExpressScreen('),'Express capability must remain available; this change is Home presentation only');
assert.ok(native.includes('"express" -> screen = "express"'),'Express routing must remain intact for existing entry points');

console.log(JSON.stringify({checks:8,failures:0,scope:'Customer Android Home categories match Web main exactly; Express capability preserved off Home'}));
