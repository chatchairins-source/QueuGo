'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../merchant/pos-table-v2.js'), 'utf8');
const calls = [];
const errors = [];
let response = '';
let activeOrder={ id: 'o1', total_amount: 90, discount_amount: 10 };
const context = {
  window: {},
  state: { products: [{ id: 'p1', name: 'Sample', pos_price: 50 }] },
  selected: () => activeOrder,
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
  for (const invalid of [null, '', 'NaN', -1, Infinity]) {
    activeOrder={ id: 'o1', total_amount: invalid, discount_amount: 10 };
    await check('qgPosDiscount', '1', false);
  }
  activeOrder={ id: 'o1', total_amount: 90, discount_amount: 'not-a-number' };
  await check('qgPosDiscount', '1', false);
  activeOrder={ id: 'o1', total_amount: 90, discount_amount: -100 };
  await check('qgPosDiscount', '1', false);
  activeOrder={ id: 'o1', total_amount: 90, discount_amount: 10 };
  await check('qgPosChangePrice', '0', true);
  await check('qgPosChangePrice', '19.99', true);
  assert.ok(errors.length >= 15);
  assert.equal(calls.at(-1).args.p_price, 19.99);
  // Refund audit must not submit an empty or too-short reason to the RPC.
  let refundReason = '', confirmed = 0;
  const refundCalls = [];
  const refundCtx = {
    window: {}, prompt: () => refundReason,
    confirm: () => { confirmed++; return true; },
    message: msg => errors.push(msg), busy: () => {},
    rpc: async (name, args) => { refundCalls.push({ name, args }); },
    load: async () => {},
  };
  vm.createContext(refundCtx);
  const refundLine = source.split('\n').find(line => line.trim().startsWith('window.qgPosRefund='));
  assert.ok(refundLine, 'missing qgPosRefund');
  vm.runInContext(refundLine, refundCtx);
  for (const invalid of ['', '   ', 'abc', 'abcd']) {
    refundReason = invalid;
    await refundCtx.window.qgPosRefund('o1');
    assert.equal(refundCalls.length, 0, 'invalid refund reason must not call RPC');
  }
  assert.equal(confirmed, 0, 'invalid refund must not request confirmation');
  refundReason = 'คืนเงินผิดบิล';
  await refundCtx.window.qgPosRefund('o1');
  assert.equal(refundCalls.length, 1);
  assert.equal(refundCalls[0].name, 'pos_refund_bill');
  assert.equal(refundCalls[0].args.p_reason, refundReason);
  assert.equal(confirmed, 1);
  console.log('POS price/discount/refund reason checks passed (isolated JS; not browser/payment E2E)');
})().catch(e => { console.error(e); process.exitCode = 1; });
