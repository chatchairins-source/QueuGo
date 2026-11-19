'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../merchant/pos-table-v2.js'), 'utf8');
const calls = [];
const errors = [];
let response = '';
const context = {
  window: {},
  state: { products: [{ id: 'p1', name: 'Sample', pos_price: 50 }] },
  selected: () => ({ id: 'o1', total_amount: 90, discount_amount: 10 }),
  prompt: () => response,
  message: msg => errors.push(msg),
  busy: () => {},
  rpc: async (name, args) => { calls.push({ name, args }); },
  load: async () => {},
};
vm.createContext(context);
for (const fn of ['qgPosDiscount', 'qgPosChangePrice']) {
  const line = source.split('\n').find(s => s.trim().startsWith('window.' + fn + '='));
  assert.ok(line, 'missing ' + fn);
  vm.runInContext(line, context);
}
async function check(fn, input, accepted) {
  response = input;
  const before = calls.length;
  await context.window[fn](fn === 'qgPosChangePrice' ? 'p1' : undefined);
  assert.equal(calls.length - before, accepted ? 1 : 0, fn + ' ' + JSON.stringify(input));
}
(async () => {
  for (const value of ['', ' ', '1e309', 'NaN', '-1', '1.001', 'abc']) {
    await check('qgPosDiscount', value, false);
    await check('qgPosChangePrice', value, false);
  }
  await check('qgPosDiscount', '101', false);
  await check('qgPosDiscount', '100', true);
  await check('qgPosDiscount', '0', true);
  await check('qgPosChangePrice', '0', true);
  await check('qgPosChangePrice', '19.99', true);
  assert.ok(errors.length >= 15);
  assert.equal(calls.at(-1).args.p_price, 19.99);
  console.log('POS discount/price input validation: 20 checks passed (isolated JS; not browser/payment E2E)');
})().catch(e => { console.error(e); process.exitCode = 1; });
