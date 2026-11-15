'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../merchant/market-catalog.js'), 'utf8');
const match = source.match(/function active\(epoch,shopId\)\{[\s\S]*?\n\}/);
assert.ok(match, 'catalog active guard exists');
let principal = { id: 'merchant-a', category: 'market' };
const context = {
  shop: () => principal,
  permitted: u => u?.category === 'market',
  profileId: u => u?.id,
  state: { epoch: 5, shop: 'merchant-a' },
  location: { hash: '#market-catalog' }
};
vm.createContext(context);
vm.runInContext(match[0], context);
assert.equal(context.active(5, 'merchant-a'), true, 'current merchant response is accepted');
principal = { id: 'merchant-b', category: 'market' };
assert.equal(context.active(5, 'merchant-a'), false, 'old response rejected after merchant switch');
principal = null;
assert.equal(context.active(5, 'merchant-a'), false, 'old response rejected after sign out');
principal = { id: 'merchant-a', category: 'other' };
assert.equal(context.active(5, 'merchant-a'), false, 'old response rejected when merchant becomes ineligible');
principal = { id: 'merchant-a', category: 'market' };
assert.equal(context.active(4, 'merchant-a'), false, 'old request epoch rejected');
context.location.hash = '#market-stock';
assert.equal(context.active(5, 'merchant-a'), false, 'response rejected after route change');
console.log('Market catalog async response auth guard regression passed');
