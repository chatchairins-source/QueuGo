'use strict';
const fs=require('node:fs');
const assert=require('node:assert/strict');

const native=fs.readFileSync('native-android/customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt','utf8');
const blueprint=JSON.parse(fs.readFileSync('data/native-web-parity-blueprint.json','utf8'));

assert.equal(blueprint.source_web_ref,'main');
assert.match(blueprint.source_web_sha,/^[0-9a-f]{40}$/);
const webHome=blueprint.customer.home.categories;
assert.deepEqual(webHome.map(x=>x.label),['อาหาร','เครื่องดื่ม','ร้านขายของชำ','ฝากซัก','ตลาดสด','บริการเรียกรถ','ช้อปปิ้ง']);
const tile=blueprint.customer.home.category_tile;
assert.deepEqual(tile,{
  tile_width_css_px:64,tile_width_native_dp:64,
  gap_css_px:13,gap_native_dp:13,
  circle_css_px:54,circle_native_dp:54,
  icon_css_px:25,icon_native_dp:25,
  label_font_css_px:10,label_font_native_sp:10,
  label_nowrap:true
});

const block=native.match(/private val homeCategories = listOf\(([\s\S]*?)\n\)/);
assert.ok(block,'Native Customer Home must declare an explicit Web-parity category list');
const nativePairs=[...block[1].matchAll(/"([^"]+)"\s+to\s+"([^"]+)"/g)].map(x=>[x[1],x[2]]);
assert.deepEqual(nativePairs.map(x=>x[0]),webHome.map(x=>x.route));
assert.deepEqual(nativePairs.map(x=>x[1]),webHome.map(x=>x.label));
assert.ok(native.includes('homeCategories.forEach { (key, label) ->'),'Customer Home must render the Web-parity list directly');
assert.ok(!block[1].includes('"express"'),'Express must not create an Android-only Home category tile');
assert.ok(native.includes('"express" -> CustomerExpressScreen('),'Express capability must remain available; this change is Home presentation only');
assert.ok(native.includes('"express" -> screen = "express"'),'Express routing must remain intact for existing entry points');
const categoryFn=native.slice(native.indexOf('private fun CustomerCategoryButton('),native.indexOf('@Composable\nprivate fun CustomerBlueprintShopCard'));
assert.ok(categoryFn.includes('.width(' + tile.tile_width_native_dp + '.dp)'),'Native category tile width must match Web Home');
assert.ok(categoryFn.includes('horizontalArrangement = Arrangement.spacedBy(13.dp)')===false,'Category button owns width only; row gap remains in Customer Home');
assert.ok(native.includes('horizontalArrangement = Arrangement.spacedBy(' + tile.gap_native_dp + '.dp)'),'Native Home category gap must match Web Home');
assert.ok(categoryFn.includes('.size(' + tile.circle_native_dp + '.dp)'),'Native category circle must match Web Home');
assert.ok(categoryFn.includes('QgIcon(icon, Modifier.size(' + tile.icon_native_dp + '.dp), QgRed)'),'Native category icon must match Web Home');
assert.ok(categoryFn.includes('fontSize = ' + tile.label_font_native_sp + '.sp'),'Native category label font must match Web Home');
assert.ok(categoryFn.includes('maxLines = 1')&&categoryFn.includes('softWrap = false'),'Native category label must stay on one line like Web nowrap');

console.log(JSON.stringify({checks:18,failures:0,scope:'Customer Android Home category order and geometry match pinned Web main blueprint; Express preserved off Home'}));
