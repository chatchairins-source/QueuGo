'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
async function main() {
  const fees = { minimum_order: 1000, base_pickup_fee: 1234.5, return_fee: 2000, round_trip_fee: 3234.5 };
  const ids = ['qgl-min', 'qgl-pickup', 'qgl-return', 'qgl-round'];
  const inputs = {};
  const writes = [];
  let html;
  const context = {
    window: { addEventListener() {}, qtShopLayout(title, body) {
      if (!body.includes('id="qgl-min"')) return;
      html = body;
      for (const id of ids) {
        const match = body.match(new RegExp('id="' + id + '"[^>]*value="([^"]*)"'));
        assert.ok(match, id + ' rendered');
        // Number inputs sanitize localized grouping separators to the empty string.
        inputs[id] = { value: /^\d+(?:\.\d+)?$/.test(match[1]) ? match[1] : '' };
      }
    } },
    document: { getElementById(id) { return inputs[id] || null; }, querySelectorAll() { return []; } },
    location: { hash: '' }, currentUser: () => ({type:'shop'}), navigate() {}, toast() {},
    QueueGoOrderNumber: { format: () => '' },
    qtGetAccessToken: async () => 'test-session',
    qtSupabaseTable: async (path, options) => {
      if (path === 'rpc/queuego_laundry_merchant_state') return {hub:{id:'shop'}, settings:fees, services:[]};
      if (path === 'rpc/queuego_feature_enabled') return true;
      if (path === 'rpc/queuego_laundry_save_settings') { writes.push(options.body); return true; }
      return [];
    }
  };
  vm.createContext(context);
  vm.runInContext(fs.readFileSync('merchant/laundry.js','utf8'), context);
  await context.window.qgRenderLaundryMerchant();
  assert.ok(html.includes('ค่าบริการรับ-ส่ง'));
  assert.deepEqual(ids.map(id => Number(inputs[id].value)), Object.values(fees));
  inputs['qgl-enabled'] = {checked:true};
  inputs['qgl-mode'] = {value:'separate'};
  // The handler is installed through bind; re-render with listener-capable save button.
  let save;
  inputs['qgl-save-settings'] = {addEventListener(event, handler) { save = handler; }};
  await context.window.qgRenderLaundryMerchant();
  await save();
  assert.equal(writes.length, 1);
  assert.deepEqual(['p_minimum_order','p_pickup_fee','p_return_fee','p_round_trip_fee'].map(k => writes[0][k]), Object.values(fees));
  console.log('Merchant Laundry runtime: grouped/fractional fees survive render and save PASS');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
