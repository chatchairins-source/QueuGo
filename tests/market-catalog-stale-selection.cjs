'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../merchant/market-catalog.js'), 'utf8');
const match = source.match(/window\.qgMarketCatalogChoose=id=>\{[\s\S]*?\n\};\n\}\)\(\);/);
assert.ok(match, 'catalog selection handler exists');
const handler = match[0].replace(/\n\}\)\(\);$/, '');
let principal = { type: 'shop', id: 'a', category: 'market' };
let finds = 0;
const context = {
  window: {},
  shop: () => principal,
  permitted: u => u?.category === 'market',
  profileId: u => u?.id,
  state: { shop: 'a', rows: { find: () => { finds++; return { product_id: 'already-linked' }; } } },
  location: { hash: '#market-catalog' }
};
vm.createContext(context);
vm.runInContext(handler, context);
context.window.qgMarketCatalogChoose('id');
assert.equal(finds, 1, 'current merchant may select catalog item');
principal = { type: 'shop', id: 'b', category: 'market' };
context.window.qgMarketCatalogChoose('id');
assert.equal(finds, 1, 'switched merchant cannot select old rows');
principal = null;
context.window.qgMarketCatalogChoose('id');
assert.equal(finds, 1, 'signed-out merchant cannot select old rows');
principal = { type: 'shop', id: 'a', category: 'other' };
context.window.qgMarketCatalogChoose('id');
assert.equal(finds, 1, 'ineligible merchant cannot select old rows');
principal = { type: 'shop', id: 'a', category: 'market' };
context.location.hash = '#market-stock';
context.window.qgMarketCatalogChoose('id');
assert.equal(finds, 1, 'stale route cannot select old rows');
console.log('Market catalog stale merchant selection regression passed');
