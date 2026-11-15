'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../merchant/market-catalog.js'), 'utf8');
const match = source.match(/async function recoverPending\(shopId,epoch\)\{[\s\S]*?\n\}\n(?=async function load)/);
assert.ok(match, 'pending recovery function exists');
async function scenario(activeSession, hasPending) {
  let calls = 0;
  const key = 'qg_market_catalog_pending_v1:merchant-a';
  const storage = new Map(hasPending ? [[key, JSON.stringify({p_request_id:'request-a',p_master_product_id:'product-a'})]] : []);
  const context = {
    pendingKey: id => 'qg_market_catalog_pending_v1:' + id,
    localStorage: {getItem: key => storage.get(key) || null, removeItem: key => storage.delete(key)},
    active: (epoch, id) => activeSession && epoch === 7 && id === 'merchant-a',
    rpc: async () => { calls++; },
    error: () => {throw Error('unexpected error');}
  };
  vm.createContext(context);
  vm.runInContext(match[0], context);
  await context.recoverPending('merchant-a', 7);
  return {calls, remaining:storage.has(key)};
}
(async () => {
  assert.deepEqual(await scenario(true, true), {calls:1,remaining:false}, 'current merchant replays and clears pending add');
  assert.deepEqual(await scenario(false, true), {calls:0,remaining:true}, 'switched or signed-out merchant cannot replay old add');
  assert.deepEqual(await scenario(true, false), {calls:0,remaining:false}, 'empty pending queue does not call RPC');
  console.log('Market catalog pending replay session guard regression passed');
})().catch(error => {console.error(error);process.exitCode=1;});
