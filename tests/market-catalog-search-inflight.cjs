'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../merchant/market-catalog.js'), 'utf8');
const match = source.match(/window\.qgMarketCatalogQueueSearch=value=>\{[^\n]+\};/);
assert.ok(match, 'catalog search handler must exist');
const state = { loading: true, query: 'old', offset: 100 };
const calls = [];
const timers = [];
const context = {
  window: {}, state, searchTimer: null,
  clearTimeout: id => { if (id) id.cancelled = true; },
  setTimeout: (fn, delay) => { const t = { fn, delay, cancelled: false }; timers.push(t); return t; },
  load: () => calls.push({ query: state.query, offset: state.offset, loading: state.loading })
};
vm.runInNewContext(match[0], context);
context.window.qgMarketCatalogQueueSearch('first');
context.window.qgMarketCatalogQueueSearch('  fresh product  ');
assert.equal(timers[0].cancelled, true, 'debounce must cancel obsolete searches');
assert.equal(timers[1].delay, 300, 'search remains debounced');
timers[1].fn();
assert.equal(state.query, 'fresh product', 'new query must be normalized');
assert.equal(state.offset, 0, 'search must reset pagination');
assert.equal(calls.length, 1, 'new search must load even while previous request is pending');
assert.equal(calls[0].loading, true, 'regression must exercise in-flight request');
assert.equal(calls[0].query, 'fresh product');
console.log('Market catalog in-flight search regression passed');
