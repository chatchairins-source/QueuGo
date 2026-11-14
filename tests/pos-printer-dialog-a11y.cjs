'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(path.join(__dirname, '../merchant/printer-v1.js'), 'utf8');
const checks = [
  ["printer dialog role", /div\.setAttribute\('role','dialog'\)/],
  ["modal semantics", /div\.setAttribute\('aria-modal','true'\)/],
  ["dialog accessible title reference", /div\.setAttribute\('aria-labelledby','qgp-dialog-title'\)/],
  ["matching dialog heading ID", /<b id="qgp-dialog-title">เครื่องพิมพ์ QueueGo<\/b>/],
  ["close button accessible name", /<button type="button" id="qgp-close" aria-label="ปิดหน้าต่างเครื่องพิมพ์">/],
  ["initial keyboard focus", /div\.querySelector\('#qgp-close'\)\.focus\(\)/],
];
for (const [name, pattern] of checks) {
  assert.match(source, pattern, 'Missing printer dialog accessibility contract: ' + name);
}
console.log('POS printer dialog accessibility source regression passed');
