'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const css = fs.readFileSync(path.join(__dirname, '../merchant/printer-v1.css'), 'utf8');
function rule(selector, source = css) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const matches = [...source.matchAll(new RegExp(escaped + '\\s*\\{([^}]*)\\}', 'g'))];
  assert.ok(matches.length, 'Missing CSS rule: ' + selector);
  return matches.at(-1)[1];
}
function px(declarations, property) {
  const match = declarations.match(new RegExp('(?:^|;)\\s*' + property + '\\s*:\\s*(\\d+)px\\s*(?:;|$)'));
  assert.ok(match, 'Missing explicit ' + property + ' in ' + declarations);
  return Number(match[1]);
}
const close = rule('.qgp-head button');
for (const property of ['min-width', 'min-height', 'width', 'height']) {
  assert.ok(px(close, property) >= 44, 'Printer modal close target must be at least 44px: ' + property);
}
const mobile = css.match(/@media\(max-width:650px\)\s*\{\s*#qg-printer-btn\s*\{([^}]*)\}/g);
assert.ok(mobile?.length, 'Expected mobile printer launcher override');
const finalMobile = mobile.at(-1).match(/#qg-printer-btn\s*\{([^}]*)\}/)[1];
for (const property of ['min-width', 'min-height', 'width', 'height']) {
  assert.ok(px(finalMobile, property) >= 44, 'Final mobile printer launcher override must not shrink target: ' + property);
}
console.log('POS printer touch target regression passed');
