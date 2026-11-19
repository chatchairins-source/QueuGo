'use strict';
// Isolated POS input-validation regression. NOT payment acceptance or browser E2E.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../merchant/pos-table-v2.js'), 'utf8');
const start = source.indexOf('window.qgPosConfirmPay=async()=>');
const end = source.indexOf('\n  window.qgPosDiscount=', start);
assert(start >= 0 && end > start, 'POS payment handler must exist');
const handler = source.slice(start, end);
async function check(value, total, expectedCalls) {
  let calls = 0;
  const elements = {
    'qg-pos-method': {value:'cash'},
    'qg-pos-cash': {value},
  };
  const context = {
    window:{qgPosReceipt(){}},
    document:{
      getElementById(id){return elements[id] || null;},
      querySelector(){return null;}
    },
    navigator:{onLine:true},
    selected(){return {id:'disposable-order',total_amount:total};},
    state:{loading:false,orders:[]},
    busy(){},
    message(){},
    async rpc(){calls++;return {id:'disposable-order'};},
    async load(){},
    Number,
    Error
  };
  vm.runInNewContext(handler, context);
  await context.window.qgPosConfirmPay();
  assert.equal(calls, expectedCalls, 'unexpected payment RPC count for '+value);
}
(async()=>{
  await check('1e309', 100, 0);
  await check('Infinity', 100, 0);
  await check('NaN', 100, 0);
  await check('99.99', 100, 0);
  await check('100', Infinity, 0);
  await check('100', 100, 1);
  console.log('POS finite cash validation: 6 cases passed (isolated unit test; no payment processed)');
})().catch(e=>{console.error(e);process.exitCode=1});
