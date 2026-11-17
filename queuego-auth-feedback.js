(function (root, factory) {
  'use strict';
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.QueueGoAuthFeedback = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
  const PHONE = /^0[0-9]{9}$/;
  const DEFAULT_ERROR = 'ดำเนินการไม่สำเร็จ กรุณาลองอีกครั้ง';

  const clean = value => String(value == null ? '' : value).trim();
  const cleanPhone = value => clean(value).replace(/[\s-]/g, '');

  function validateLogin(input) {
    const identifier = clean(input.identifier);
    if (!identifier) return { field: 'lp', message: 'กรุณากรอกเบอร์โทรศัพท์หรืออีเมล' };
    if (identifier.includes('@')) {
      if (!EMAIL.test(identifier)) return { field: 'lp', message: 'กรุณากรอกอีเมลให้ถูกต้อง เช่น name@example.com' };
    } else if (!PHONE.test(cleanPhone(identifier))) {
      return { field: 'lp', message: 'กรุณากรอกเบอร์โทรศัพท์ 10 หลัก เริ่มด้วย 0' };
    }
    if (!String(input.password || '')) return { field: 'lw', message: 'กรุณากรอกรหัสผ่าน' };
    return null;
  }

  function validateRegistration(input, passwordPolicy) {
    const mode = input.mode === 'email' ? 'email' : 'phone';
    if (!clean(input.name)) return { field: 'rn', message: 'กรุณากรอกชื่อและนามสกุล' };
    if (mode === 'email' && !EMAIL.test(clean(input.email))) {
      return { field: 'register-email', message: 'กรุณากรอกอีเมลให้ถูกต้อง เช่น name@example.com' };
    }
    if (mode === 'phone' && !PHONE.test(cleanPhone(input.phone))) {
      return { field: 'rp', message: 'กรุณากรอกเบอร์โทรศัพท์ 10 หลัก เริ่มด้วย 0' };
    }
    if (mode === 'email' && clean(input.phone) && !PHONE.test(cleanPhone(input.phone))) {
      return { field: 'rp', message: 'เบอร์โทรศัพท์ต้องมี 10 หลัก เริ่มด้วย 0 หรือเว้นว่างไว้' };
    }
    if (!String(input.password || '')) return { field: 'rw', message: 'กรุณากำหนดรหัสผ่าน' };
    if (!passwordPolicy || typeof passwordPolicy.validate !== 'function' || !passwordPolicy.validate(input.password)) {
      return { field: 'rw', message: passwordPolicy?.MESSAGE || 'รหัสผ่านยังไม่ตรงตามข้อกำหนดความปลอดภัย' };
    }
    if (!String(input.confirm || '')) return { field: 'rw-confirm', message: 'กรุณายืนยันรหัสผ่าน' };
    if (input.confirm !== input.password) return { field: 'rw-confirm', message: 'รหัสผ่านที่ยืนยันไม่ตรงกัน' };
    return null;
  }

  function errorMessage(error, action) {
    const raw = clean(error && error.message || error);
    const code = clean(error && error.code).toLowerCase();
    const status = Number(error && error.status);
    const lower = raw.toLowerCase();
    const login = action === 'login';

    if (code === 'email_not_confirmed' || /email not confirmed|email.*not.*verified/.test(lower)) {
      return 'กรุณายืนยันอีเมลจากข้อความที่ส่งให้ก่อนเข้าสู่ระบบ';
    }
    if (code === 'invalid_credentials' || /invalid login credentials|invalid credentials|invalid_grant/.test(lower) ||
        (login && status === 400)) {
      return 'เบอร์โทรศัพท์/อีเมล หรือรหัสผ่านไม่ถูกต้อง กรุณาตรวจสอบอีกครั้ง';
    }
    if (code === 'user_already_exists' || code === 'email_exists' ||
        /user already registered|already registered|already been registered|email.*already.*in use/.test(lower)) {
      return 'อีเมลหรือเบอร์โทรนี้อาจมีบัญชีแล้ว ลองเข้าสู่ระบบหรือใช้ข้อมูลอื่น';
    }
    if (code === 'weak_password' || /weak password|password.*(weak|short)/.test(lower)) {
      return 'รหัสผ่านยังไม่ตรงตามข้อกำหนด กรุณาตรวจสอบและลองอีกครั้ง';
    }
    if (code === 'email_address_invalid' || /invalid email|email.*invalid/.test(lower)) {
      return 'กรุณากรอกอีเมลให้ถูกต้อง';
    }
    if (code === 'signup_disabled' || /signup.*disabled|signups.*disabled/.test(lower)) {
      return 'ระบบสมัครสมาชิกปิดให้บริการชั่วคราว กรุณาลองใหม่ภายหลัง';
    }
    if (code === 'over_email_send_rate_limit' || status === 429 ||
        /rate limit|too many requests|email rate limit/.test(lower)) {
      return 'มีการลองหลายครั้งเกินไป กรุณารอสักครู่แล้วลองใหม่';
    }
    if (code === 'otp_expired' || /otp.*expired/.test(lower)) {
      return 'รหัสยืนยันหมดอายุ กรุณาขอรหัสใหม่';
    }
    if (/aborterror|timeout|timed out|network timeout/.test((error && error.name || '').toLowerCase()+' '+lower)) {
      return 'การเชื่อมต่อใช้เวลานานเกินไป กรุณาตรวจอินเทอร์เน็ตแล้วลองอีกครั้ง';
    }
    if (/failed to fetch|networkerror|network request failed|internet connection/.test(lower)) {
      return 'เชื่อมต่อเซิร์ฟเวอร์ไม่ได้ กรุณาตรวจอินเทอร์เน็ตแล้วลองอีกครั้ง';
    }
    if (status >= 500) return 'ระบบขัดข้องชั่วคราว กรุณาลองใหม่อีกครั้ง';
    // Never surface raw HTTP status / database messages / server internals to end users.
    return login ? 'เข้าสู่ระบบไม่สำเร็จ กรุณาตรวจข้อมูลแล้วลองอีกครั้ง' :
      action === 'register' ? 'สมัครสมาชิกไม่สำเร็จ กรุณาตรวจข้อมูลแล้วลองอีกครั้ง' : DEFAULT_ERROR;
  }

  function locationMessage(error) {
    if (error?.code === 1) return 'กรุณาอนุญาตการเข้าถึงตำแหน่งในเบราว์เซอร์ แล้วลองสมัครอีกครั้ง';
    if (error?.code === 2) return 'ยังระบุตำแหน่งไม่ได้ กรุณาเปิด GPS และลองอีกครั้ง';
    if (error?.code === 3) return 'ค้นหาตำแหน่งนานเกินไป กรุณาลองอีกครั้ง';
    return 'ไม่สามารถอ่านตำแหน่งได้ กรุณาตรวจสิทธิ์ GPS แล้วลองอีกครั้ง';
  }

  function clearFieldError(doc, id) {
    const field = doc.getElementById(id);
    if (!field) return;
    field.removeAttribute('aria-invalid');
    field.removeAttribute('aria-describedby');
    doc.getElementById(id + '-error')?.remove();
  }

  function clearFormError(doc, buttonId) {
    doc.getElementById(buttonId + '-form-error')?.remove();
  }

  function fieldError(doc, id, message) {
    const field = doc.getElementById(id);
    if (!field) return false;
    clearFieldError(doc, id);
    field.setAttribute('aria-invalid', 'true');
    field.setAttribute('aria-describedby', id + '-error');
    const error = doc.createElement('p');
    error.id = id + '-error';
    error.className = 'qg-auth-field-error';
    error.setAttribute('role', 'alert');
    error.textContent = message;
    field.insertAdjacentElement('afterend', error);
    field.focus();
    return true;
  }

  function formError(doc, buttonId, message) {
    const button = doc.getElementById(buttonId);
    if (!button) return false;
    clearFormError(doc, buttonId);
    const error = doc.createElement('p');
    error.id = buttonId + '-form-error';
    error.className = 'qg-auth-form-error';
    error.setAttribute('role', 'alert');
    error.textContent = message;
    button.insertAdjacentElement('beforebegin', error);
    return true;
  }

  return Object.freeze({
    validateLogin, validateRegistration, errorMessage, locationMessage,
    clearFieldError, clearFormError, fieldError, formError, cleanPhone
  });
});
