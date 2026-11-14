'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../merchant/market-catalog.js'), 'utf8');
const cardMatch = source.match(/function card\(r\)\{[\s\S]*?\n\}\nfunction paint\(/);
assert.ok(cardMatch, 'catalog card renderer must exist');
const context = {
  E: v => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])),
  rowImage: () => '',
  deliveryNote: () => '',
  Number,
};
vm.createContext(context);
vm.runInContext(cardMatch[0].replace(/\nfunction paint\($/, ''), context);
const maliciousId = "product');window.injected=true;//";
const html = vm.runInContext('card(r)', Object.assign(context, { r: { id: maliciousId, name_th: 'test', category: 'ผักสด' } }));
const onclick = html.match(/onclick="([^"]+)"/g) || [];
assert.ok(onclick.some(x => x.includes("qgMarketCatalogChoose(this.closest('[data-market-id]').getAttribute('data-market-id'))")), 'product selection reads id from DOM instead of interpolating it into executable JavaScript');
assert.ok(!onclick.some(x => x.includes('window.injected')), 'untrusted product ID must not appear in inline JavaScript');
assert.ok(html.includes('data-market-id="product&#39;);window.injected=true;//"'), 'product ID remains escaped in the HTML attribute');
console.log('Market catalog product ID inline-handler injection regression passed');
