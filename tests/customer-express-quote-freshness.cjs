'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'customer-express.js'), 'utf8');
const packageJson = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'package.json'), 'utf8'));
assert.ok(packageJson.scripts.test.includes('node tests/customer-express-quote-freshness.cjs'), 'the freshness regression must run in npm test');
function extractFunction(name) {
  const start = source.indexOf(`function ${name}(`);
  assert.notEqual(start, -1, `missing ${name}`);
  const open = source.indexOf('{', start);
  let depth = 0;
  for (let i = open; i < source.length; i++) {
    if (source[i] === '{') depth++;
    else if (source[i] === '}' && --depth === 0) return source.slice(start, i + 1);
  }
  throw new Error(`unterminated ${name}`);
}
const functionSources = ['clearExpressQuote', 'bindForm', 'expressQuoteMatches', 'acceptExpressQuote', 'saveExpressPoint']
  .map(extractFunction);
assert.match(source, /body:source,signal:AbortSignal\.timeout/);
assert.match(source, /acceptExpressQuote\(source,data\)/);
assert.match(source, /if\(!quote\|\|!expressQuoteMatches\(quoteSource\)\)/);
assert.match(source, /saveExpressPoint\(which,next\);picker\?\.destroy\(\);picker=null;render\(\)/);
assert.match(source, /if\(quote\)html\+='[^\n]*ค่าส่งเงินสด/);
assert.match(source, /if\(p\|\|!current&&quote\)html\+='[^\n]*เรียกไรเดอร์ส่งด่วน/);

const fieldsById = {
  expressSenderName: 'senderName',
  expressSenderPhone: 'senderPhone',
  expressReceiverName: 'receiverName',
  expressReceiverPhone: 'receiverPhone',
  expressItemType: 'itemType',
  expressWeight: 'weight',
  expressSize: 'size',
  expressVehicle: 'vehicleType',
  expressInstructions: 'instructions'
};
const initialForm = {
  senderName: 'Sender',
  senderPhone: '0812345678',
  receiverName: 'Receiver',
  receiverPhone: '0891234567',
  itemType: 'Parcel',
  weight: '1',
  size: 'medium',
  vehicleType: 'motorcycle',
  instructions: ''
};
let activeFields = {};
let renderCount = 0;
const focusEvents = [];
const rangeEvents = [];
const ui = {
  form: { ...initialForm },
  quote: { quote_id: 'old-quote', fare: 125 },
  quoteSource: 'old-payload',
  busy: false,
  onExpress: () => true,
  $: id => activeFields[id],
  render() {
    renderCount++;
    ui.lastRenderedQuote = ui.quote;
    activeFields = makeFields();
    ui.bindForm();
  }
};
function makeFields() {
  return Object.fromEntries(Object.entries(fieldsById).map(([id, key]) => [id, {
    id,
    value: ui.form[key],
    selectionStart: 2,
    selectionEnd: 4,
    selectionDirection: 'forward',
    focus(options) { focusEvents.push({ id, preventScroll: options?.preventScroll }); },
    setSelectionRange(start, end, direction) { rangeEvents.push({ id, start, end, direction }); }
  }]));
}
functionSources.slice(0, 2).forEach(code => vm.runInNewContext(code, ui));
activeFields = makeFields();
ui.bindForm();

for (const [id, key, value] of [
  ['expressSenderPhone', 'senderPhone', '0899999999'],
  ['expressReceiverPhone', 'receiverPhone', '0811111111'],
  ['expressWeight', 'weight', '2.5'],
  ['expressVehicle', 'vehicleType', 'car']
]) {
  ui.quote = { quote_id: 'old-quote', fare: 125 };
  ui.quoteSource = 'old-payload';
  const previous = activeFields[id];
  const beforeRender = renderCount;
  previous.value = value;
  previous.selectionStart = 3;
  previous.selectionEnd = 5;
  previous.oninput();
  assert.equal(ui.form[key], value, `${id} edit must update the form`);
  assert.equal(ui.quote, null, `${id} edit must invalidate the old quote immediately`);
  assert.equal(ui.quoteSource, '', `${id} edit must clear its payload fingerprint`);
  assert.equal(renderCount, beforeRender + 1, `${id} input must repaint without waiting for blur/change`);
  assert.equal(ui.lastRenderedQuote, null, `${id} repaint must not render an old fare or call-to-action`);
  assert.notEqual(activeFields[id], previous, 'render replaces the stale form node');
  assert.equal(focusEvents.at(-1).id, id, 'focus must return to the edited control');
  assert.equal(focusEvents.at(-1).preventScroll, true);
  assert.equal(activeFields[id].value, value, 'the typed value must survive repaint');
  if (id !== 'expressVehicle') {
    const selection = rangeEvents.at(-1);
    assert.equal(selection.id, id);
    assert.equal(selection.start, 3);
    assert.equal(selection.end, 5);
    assert.equal(selection.direction, 'forward');
  }
}

// A route edit uses the same quote invalidation before the picker returns to the form.
const routes = {
  pickup: { lat: 14, lng: 102, address: 'Old pickup' },
  destination: { lat: 15, lng: 103, address: 'Old destination' },
  quote: { quote_id: 'old-quote', fare: 125 },
  quoteSource: 'old-payload'
};
const routeHarness = { ...routes };
vm.runInNewContext(
  functionSources[0] + '\n' + functionSources[4],
  routeHarness
);
for (const [which, next] of [
  ['pickup', { lat: 14.1, lng: 102.1, address: 'New pickup' }],
  ['destination', { lat: 15.1, lng: 103.1, address: 'New destination' }]
]) {
  routeHarness.quote = { quote_id: 'old-quote', fare: 125 };
  routeHarness.quoteSource = 'old-payload';
  routeHarness.saveExpressPoint(which, next);
  assert.deepEqual(routeHarness[which], next);
  assert.equal(routeHarness.quote, null, `${which} edit must invalidate the old quote`);
  assert.equal(routeHarness.quoteSource, '');
}

// Simulate a quote API response arriving after each relevant field changed.
const originalPayload = {
  pickup: { lat: 14, lng: 102, address: 'A' },
  destination: { lat: 15, lng: 103, address: 'B' },
  sender: { name: 'Sender', phone: '0812345678' },
  receiver: { name: 'Receiver', phone: '0891234567' },
  item: { type: 'Parcel', weightKg: 1, size: 'medium', vehicleType: 'motorcycle', instructions: '' }
};
let livePayload = structuredClone(originalPayload);
const apiHarness = {
  formPayload: () => livePayload,
  quote: null,
  quoteSource: ''
};
vm.runInNewContext(functionSources[2] + '\n' + functionSources[3], apiHarness);
const requestFingerprint = JSON.stringify(originalPayload);
for (const [label, edit] of [
  ['sender phone', payload => ({ ...payload, sender: { ...payload.sender, phone: '0899999999' } })],
  ['receiver phone', payload => ({ ...payload, receiver: { ...payload.receiver, phone: '0811111111' } })],
  ['weight', payload => ({ ...payload, item: { ...payload.item, weightKg: 2.5 } })],
  ['vehicle', payload => ({ ...payload, item: { ...payload.item, vehicleType: 'car' } })],
  ['pickup', payload => ({ ...payload, pickup: { ...payload.pickup, lat: 14.1 } })],
  ['destination', payload => ({ ...payload, destination: { ...payload.destination, lng: 103.1 } })]
]) {
  livePayload = edit(structuredClone(originalPayload));
  apiHarness.quote = null;
  apiHarness.quoteSource = '';
  assert.equal(apiHarness.expressQuoteMatches(requestFingerprint), false, `${label} changed while the quote request was pending`);
  assert.throws(
    () => apiHarness.acceptExpressQuote(requestFingerprint, { quote_id: 'stale', fare: 125 }),
    /ข้อมูลพัสดุเปลี่ยนแล้ว/,
    `${label} change must reject the late quote response`
  );
  assert.equal(apiHarness.quote, null, `${label} change must not republish a stale quote`);
  assert.equal(apiHarness.quoteSource, '');
}
livePayload = structuredClone(originalPayload);
assert.equal(apiHarness.expressQuoteMatches(requestFingerprint), true);
apiHarness.acceptExpressQuote(requestFingerprint, { quote_id: 'fresh', fare: 130 });
assert.equal(apiHarness.quote.quote_id, 'fresh');
assert.equal(apiHarness.quoteSource, requestFingerprint);

console.log('Customer Express quote freshness: input UI invalidation, caret restoration, route changes and six late-response cases PASS');
