'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');
const feedback = require('../queuego-auth-feedback.js');
const passwords = require('../queuego-password-policy.js');
let checks = 0;
function ok(value, label) {assert.ok(value, label); checks++}
function equal(value, expected, label) {assert.equal(value, expected, label); checks++}

equal(feedback.validateLogin({identifier: '', password: ''}).field, 'lp', 'Missing login identifier points at correct field');
equal(feedback.validateLogin({identifier: 'not-an-email@', password: 'example'}).field, 'lp', 'Invalid login email is rejected before request');
equal(feedback.validateLogin({identifier: '0812345', password: 'example'}).field, 'lp', 'Short phone is rejected before request');
equal(feedback.validateLogin({identifier: '0812345678', password: ''}).field, 'lw', 'Missing login password points at password');
equal(feedback.validateLogin({identifier: 'hello@example.com', password: 'password'}), null, 'Valid email login passes field validation');
equal(feedback.validateLogin({identifier: '081-234-5678', password: 'password'}), null, 'Valid formatted phone is accepted');
equal(feedback.cleanPhone('081-234 5678'), '0812345678', 'Phone normalization matches Auth email-from-phone logic');

function registration(data) {
  return feedback.validateRegistration(Object.assign({
    mode: 'phone', name: 'ลูกค้า ทดสอบ', phone: '0812345678',
    email: '', password: 'QueueGo#2026Secure', confirm: 'QueueGo#2026Secure'
  }, data), passwords);
}
equal(registration({name: '   '}).field, 'rn', 'Missing name is field-specific');
equal(registration({phone: '08123'}).field, 'rp', 'Phone registration rejects invalid phone');
equal(registration({mode: 'email', email: 'broken@', phone: ''}).field, 'register-email', 'Email mode validates email');
equal(registration({mode: 'email', email: 'valid@example.org', phone: '123'}).field, 'rp', 'Optional phone must be valid if supplied');
equal(registration({password: '1234'}).field, 'rw', 'Weak password identifies password field');
equal(registration({confirm: 'different'}).field, 'rw-confirm', 'Mismatched confirmation identifies confirm field');
equal(registration({mode: 'email', email: 'valid@example.org', phone: ''}), null, 'Email registration preserves optional phone');
equal(registration({}), null, 'Valid registration passes');

const mapCases = [
  [{code: 'customer_wrong_role'}, 'login', /ประเภทบัญชี/],
  [{code: 'customer_inactive'}, 'login', /ยังไม่พร้อมใช้งาน/],
  [{code: 'invalid_credentials', status: 400, message: 'Invalid login credentials'}, 'login', /ไม่ถูกต้อง/],
  [{message: 'HTTP 400 Bad Request', status: 400}, 'login', /ไม่ถูกต้อง/],
  [{code: 'email_not_confirmed', message: 'Email not confirmed'}, 'login', /ยืนยันบัญชี/],
  [{code: 'user_already_exists', message: 'User already registered'}, 'register', /มีบัญชีแล้ว/],
  [{code: 'weak_password', message: 'weak password'}, 'register', /รหัสผ่าน/],
  [{code: 'email_address_invalid'}, 'register', /อีเมล/],
  [{status: 429, message: 'Too many requests'}, 'login', /ลองใหม่/],
  [{status: 503, message: 'Internal Server Error'}, 'login', /ระบบขัดข้อง/],
  [{name: 'TimeoutError', message: 'TimeoutError'}, 'login', /เชื่อมต่อ/],
  [{message: 'Failed to fetch'}, 'register', /อินเทอร์เน็ต/],
  [{message: 'rpc secret diagnostic: DB_ERROR_400', status: 400}, 'register', /สมัครสมาชิกไม่สำเร็จ/]
];
for (const [error, operation, expected] of mapCases) {
  const actual = feedback.errorMessage(error, operation);
  ok(expected.test(actual), 'Auth error must be actionable: ' + String(error.code || error.message));
  ok(!/HTTP\s*\d{3}|DB_ERROR|rpc secret diagnostic|AUTH_ERROR|Bad Request/i.test(actual),
    'Never expose raw backend technical error to user');
}
equal(feedback.locationMessage({code: 1}).includes('อนุญาต'), true, 'GPS permission guidance');
equal(feedback.locationMessage({code: 3}).includes('นานเกินไป'), true, 'GPS timeout guidance');

const dom = new JSDOM('<!doctype html><input id="rn"><button id="rb">สมัคร</button>');
const doc = dom.window.document;
ok(feedback.fieldError(doc, 'rn', 'กรุณากรอกชื่อ'), 'Attach feedback to field');
equal(doc.getElementById('rn').getAttribute('aria-invalid'), 'true', 'Screen-reader invalid state');
equal(doc.getElementById('rn').getAttribute('aria-describedby'), 'rn-error', 'Screen-reader error reference');
equal(doc.getElementById('rn-error').textContent, 'กรุณากรอกชื่อ', 'Specific error is displayed');
feedback.clearFieldError(doc, 'rn');
equal(doc.getElementById('rn-error'), null, 'Input edit clears old field error');
equal(doc.getElementById('rn').hasAttribute('aria-invalid'), false, 'Input edit clears invalid state');
ok(feedback.formError(doc, 'rb', 'สมัครสมาชิกไม่ได้'), 'Attach general error before submit button');
equal(doc.getElementById('rb-form-error').getAttribute('role'), 'alert', 'General error is announced');
feedback.clearFormError(doc, 'rb');
equal(doc.getElementById('rb-form-error'), null, 'Clear stale general error');
ok(feedback.formNotice(doc, 'rb', 'ตรวจอีเมลเพื่อยืนยันบัญชี'), 'Signup confirmation has a separate notice');
equal(doc.getElementById('rb-form-notice').getAttribute('role'), 'status', 'Confirmation is announced as status');
feedback.clearFormError(doc, 'rb');
equal(doc.getElementById('rb-form-notice'), null, 'Clear old success/pending notice on retry');
dom.window.close();

const source = fs.readFileSync('index.html', 'utf8');
ok(source.includes('queuego-auth-feedback.js?v='), 'Customer page must load shared feedback before inline controller');
ok(source.indexOf('queuego-auth-feedback.js?v=') < source.indexOf('function bind(pg){'),
  'Helper is loaded before login/register handlers execute');
ok(source.includes('feedback.validateLogin(') && source.includes('feedback.validateRegistration('),
  'Both customer forms validate before network calls');
ok(source.includes("error.code=String(d.code||d.error||'')"), 'Auth fetch retains Supabase error code for safe translation');
ok(source.includes("feedback.formError(document,'lb',feedback.errorMessage(error,'login'))"),
  'Login shows Thai error near action instead of toast(x.message)');
ok(source.includes('feedback.fieldError(document,validation.field,validation.message)'),
  'Submit focuses specific invalid field');
ok(source.includes("feedback.errorMessage(error,'register')"),
  'Signup translates server errors too');
ok(source.includes("if(email && signed?.user && !signed.session)"), 'Email signup must respect confirmation before attempting login');
ok(source.includes("feedback.formNotice(document,'rb',text)"), 'Successful pending email verification is not displayed as failure');
ok(source.includes('aria-invalid="true"'), 'Invalid input styling exists');

console.log(JSON.stringify({checks, failures:0, scope:'Customer web signup/login field validation, Thai auth errors, A11y and safe Supabase error handling'}));
