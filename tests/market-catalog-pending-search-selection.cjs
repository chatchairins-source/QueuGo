'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../merchant/market-catalog.js'), 'utf8');
const match = source.match(/window\.qgMarketCatalogChoose=id=>\{[\s\S]*?\n\};\n\}\)\(\);/);
assert.ok(match, 'selection handler exists');
let query = 'หมู';
let lookups = 0;
const context = {
  window: {},
  shop: () => ({ type: 'shop', id: 'shop-1', category: 'market' }),
  permitted: () => true,
  profileId: () => 'shop-1',
  state: { shop: 'shop-1', query: 'หมู', rows: { find: () => { lookups++; return null; } } },
  location: { hash: '#market-catalog' },
  document: { getElementById: id => id === 'market-catalog-search' ? { value: query } : null }
};
vm.createContext(context);
vm.runInContext(match[0].replace(/\n\}\)\(\);$/, ''), context);
context.window.qgMarketCatalogChoose('product');
assert.equal(lookups, 1, 'settled query permits selection');
query = 'ไก่';
context.window.qgMarketCatalogChoose('product');
assert.equal(lookups, 1, 'pending typed query blocks selection of stale rows');
query = '  หมู  ';
context.window.qgMarketCatalogChoose('product');
assert.equal(lookups, 2, 'whitespace-normalized matching query remains selectable');
query = 'ไก่';
context.document.getElementById = () => null;
context.window.qgMarketCatalogChoose('product');
assert.equal(lookups, 3, 'unmounted search input does not block an otherwise valid selection');
console.log('Market catalog pending search selection regression passed');
