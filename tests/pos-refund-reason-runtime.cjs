'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../merchant/pos-table-v2.js'), 'utf8');
const match = source.match(/window\.qgPosRefund=async id=>\{[\s\S]*?\};\s*(?=window\.qgPosChangePrice)/);
assert.ok(match, 'Refund handler must exist');
async function scenario(reason, approved) {
  const calls = [];
  const notices = [];
  const sandbox = {
    window: {},
    prompt: () => reason,
    confirm: () => approved,
    message: msg => notices.push(msg),
    busy: value => calls.push(['busy', value]),
    rpc: async (name, args) => { calls.push(['rpc', name, args]); },
    load: async () => calls.push(['load']),
  };
  vm.runInNewContext(match[0], sandbox);
  await sandbox.window.qgPosRefund('test-order');
  return { calls, notices };
}
(async () => {
  const whitespace = await scenario('     ', true);
  assert.equal(whitespace.calls.filter(x => x[0] === 'rpc').length, 0, 'Whitespace-only reason must not submit');
  const tooShort = await scenario('   abc   ', true);
  assert.equal(tooShort.calls.filter(x => x[0] === 'rpc').length, 0, 'Trimmed reason shorter than 5 must not submit');
  const denied = await scenario('  legitimate return  ', false);
  assert.equal(denied.calls.filter(x => x[0] === 'rpc').length, 0, 'Cancelled confirmation must not submit');
  const valid = await scenario('  legitimate return  ', true);
  const rpcCalls = valid.calls.filter(x => x[0] === 'rpc');
  assert.equal(rpcCalls.length, 1);
  assert.equal(rpcCalls[0][1], 'pos_refund_bill');
  assert.equal(rpcCalls[0][2].p_order, 'test-order');
  assert.equal(rpcCalls[0][2].p_reason, 'legitimate return', 'Audit reason must be normalized before persistence');
  assert.equal(valid.calls.filter(x => x[0] === 'load').length, 1);
  console.log('POS refund reason runtime regression: 4 scenarios passed');
})().catch(err => { console.error(err); process.exitCode = 1; });
