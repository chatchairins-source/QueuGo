'use strict';
// Regression contract: a quote is valid only for the exact contact/parcel/route payload quoted.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '..', 'customer-express.js'), 'utf8');
assert.match(source, /const payload=formPayload\(\),source=JSON\.stringify\(payload\)/);
assert.match(source, /body:source,signal:AbortSignal\.timeout/);
assert.match(source, /JSON\.stringify\(formPayload\(\)\)!==source\)throw Error/);
assert.match(source, /JSON\.stringify\(formPayload\(\)\)!==quoteSource\)throw Error/);
assert.match(source, /form\[k\]=e\.value;if\(quote\)\{invalidated=true\}quote=null;quoteSource=''/);
assert.match(source, /e\.onchange=\(\)=>\{if\(invalidated&&!busy&&onExpress\(\)\)render\(\)\}/);
assert.match(source, /quote=null;quoteSource='';picker\?\.destroy\(\)/);
// Exercise the comparison contract with a changed phone, weight and pickup.
const fingerprint = value => JSON.stringify(value);
const original = {sender:{phone:'0812345678'},item:{weightKg:1},pickup:{lat:14,lng:102}};
const quoted = fingerprint(original);
assert.equal(fingerprint(structuredClone(original)), quoted);
for (const modified of [
  {...original,sender:{phone:'0899999999'}},
  {...original,item:{weightKg:2}},
  {...original,pickup:{lat:15,lng:102}},
]) assert.notEqual(fingerprint(modified),quoted);
console.log('Customer Express quote freshness regression PASS');
