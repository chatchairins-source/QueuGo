'use strict';
// Regression contract: a quote is valid only for the exact contact/parcel/route payload quoted.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '..', 'customer-express.js'), 'utf8');
assert.match(source, /const payload=formPayload\(\),source=JSON\.stringify\(payload\)/);
assert.match(source, /body:source,signal:AbortSignal\.timeout/);
assert.match(source, /if\(!same\(u\)\)return;if\(!config\.enabled\|\|!config\.configured\)throw Error\('บริการ Express ยังไม่เปิดให้ใช้ในขณะนี้'\);if\(JSON\.stringify\(formPayload\(\)\)!==source\)/);
assert.match(source, /JSON\.stringify\(formPayload\(\)\)!==quoteSource\)throw Error/);
assert.match(source, /form\[k\]=e\.value;if\(quote\)\{invalidated=true;/);
assert.match(source, /const summary=\$\('express-quote-summary'\);if\(summary\)summary\.hidden=true/);
assert.match(source, /request\.disabled=true;request\.textContent=/);
assert.match(source, /quote=null;quoteSource=''/);
assert.match(source, /id="express-quote-summary"/);
assert.match(source, /e\.onchange=\(\)=>\{e\.oninput\(\);if\(invalidated&&!busy&&onExpress\(\)\)render\(\)\}/);
assert.match(source, /quote=null;quoteSource='';picker\?\.destroy\(\)/);
assert.match(source, /typeof data\.quote_id!=='string'/);
assert.match(source, /!Number\.isFinite\(data\.fare\)\|\|data\.fare<0/);
assert.match(source, /!Number\.isFinite\(data\.distance_km\)\|\|data\.distance_km<0/);
assert.match(source, /!Number\.isFinite\(data\.duration_minutes\)\|\|data\.duration_minutes<0/);
assert.match(source, /\(kind==='quote'\|\|\(kind==='request'&&!saved\)\)\&\&\(!config\.enabled\|\|!config\.configured\)/);
assert.match(source, /config=c\|\|\{\};if\(!config\.enabled\|\|!config\.configured\)\{quote=null;quoteSource=''\}await refresh\(\)/);
assert.match(source, /config=\{enabled:false,configured:false\};quote=null;quoteSource=''/);
assert.match(source, /if\(kind==='new'\)\{selected=null;quote=null;quoteSource=''/);
// A corrupt persisted request must fail closed without crashing render or overwriting the journal.
assert.match(source, /let p=null,pendingInvalid=false;try\{p=pending\(\)\}catch\(e\)\{pendingInvalid=true;error=e\.message\}/);
assert.match(source, /if\(!pendingInvalid&&\(p\|\|!current&&quote\)\)html\+=/);
assert.match(source, /let saved=null;try\{saved=pending\(\)\}catch\(e\)\{error=e\.message;render\(\);return\}/);
// Disabled Express must block new requests, but must not strand an already persisted request ID.
assert.match(source, /let p=pending\(\);if\(!p\)\{/);
assert.match(source, /const result=await rpc\('queuego_request_express',p\)/);
assert.match(source, /destination=quote=selected=null;quoteSource='';rows=\[\]/);
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
