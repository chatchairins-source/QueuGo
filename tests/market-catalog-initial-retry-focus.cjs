'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../merchant/market-catalog.js'),'utf8');

assert.match(source,/id="market-catalog-initial-retry" onclick="qgMarketCatalogRetryInitial\(\)"/,
  'initial failure retry must use the focus-aware handler');
assert.match(source,/window\.qgMarketCatalogRetryInitial=async\(\)=>\{[\s\S]*?await load\(\);[\s\S]*?if\(state\.resultsReady\)\{[\s\S]*?getElementById\('market-catalog-search'\)\?\.focus\(\{preventScroll:true\}\)/,
  'successful retry must restore focus to the search field');
assert.match(source,/else\{[\s\S]*?getElementById\('market-catalog-initial-retry'\)\?\.focus\(\{preventScroll:true\}\)/,
  'failed retry must restore focus to the replacement retry button');
console.log('market catalog initial retry focus contract passed');
