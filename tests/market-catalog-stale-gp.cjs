'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const source = fs.readFileSync(path.join(__dirname, '../merchant/market-catalog.js'), 'utf8');
const match = source.match(/if\(!active\(epoch,id\)\)return;\s*state\.gpRate=gp;/);
assert.ok(match, 'stale-response guard must precede GP rate mutation');

function applyResponse(isActive, initialRate, incomingRate) {
  const state = { gpRate: initialRate };
  const context = {
    state, epoch: 7, id: 'shop-a', gp: incomingRate,
    active: () => isActive
  };
  vm.runInNewContext('(function(){' + match[0] + '})()', context);
  return state.gpRate;
}
assert.equal(applyResponse(false, 12, 99), 12,
  'stale response from previous shop/request must not overwrite active GP rate');
assert.equal(applyResponse(true, 12, 8), 8,
  'active response must still update GP rate');
assert.equal(applyResponse(false, null, 20), null,
  'stale response must not initialize an unverified GP rate');
console.log('Market catalog stale GP rate guard regression passed');
