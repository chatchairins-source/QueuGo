(() => {
  'use strict';
  const SUPABASE_URL = 'https://pkypiqhlrmzocysgeqew.supabase.co';
  const PUBLISHABLE_KEY = 'sb_publishable_Vn2Il4jp-iBtzNsGRNY8Lg_Tpvk2Vre';
  const scriptUrl = document.currentScript?.src || new URL('account-deletion.js', document.baseURI).href;
  const projectRoot = new URL('.', scriptUrl);

  function publicUrl(path) {
    return new URL(path, projectRoot).href;
  }

  function messageFor(payload, status) {
    const code = payload?.error || '';
    if (code === 'ACTIVE_WORK') return 'ยังลบบัญชีไม่ได้ เพราะมีออเดอร์ งานส่ง งานร้านค้า หรือรายการฝากซักที่ยังไม่จบ กรุณาดำเนินการให้เสร็จหรือยกเลิกก่อน';
    if (code === 'ADMIN_ACCOUNT') return 'บัญชีแอดมินไม่รองรับการลบด้วยตนเองจากหน้านี้';
    if (code === 'ACCOUNT_NOT_FOUND') return 'ไม่พบบัญชี QueueGo ที่เชื่อมกับการเข้าสู่ระบบนี้';
    if (code === 'CONFIRMATION_REQUIRED') return 'กรุณายืนยันการลบบัญชีอีกครั้ง';
    if (code === 'LOGIN_REQUIRED' || status === 401) return 'เซสชันหมดอายุ กรุณาเข้าสู่ระบบใหม่';
    if (code === 'ORIGIN_NOT_ALLOWED') return 'หน้านี้ไม่ได้รับอนุญาตให้ลบบัญชี';
    return 'ลบบัญชีไม่สำเร็จ กรุณาลองอีกครั้งหรือติดต่อฝ่ายช่วยเหลือ';
  }

  async function request(accessToken) {
    if (!accessToken) throw new Error('กรุณาเข้าสู่ระบบใหม่');
    const response = await fetch(SUPABASE_URL + '/functions/v1/account-delete', {
      method: 'POST',
      headers: {
        apikey: PUBLISHABLE_KEY,
        Authorization: 'Bearer ' + accessToken,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ confirm: 'DELETE_ACCOUNT' })
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload?.ok !== true) {
      const error = new Error(messageFor(payload, response.status));
      error.code = payload?.error || 'ACCOUNT_DELETE_FAILED';
      error.details = payload?.details || null;
      error.status = response.status;
      throw error;
    }
    return payload;
  }

  function openPrivacy() {
    location.href = publicUrl('docs/privacy.html');
  }

  function openDeletionPage() {
    location.href = publicUrl('docs/account-deletion.html');
  }

  window.QueueGoAccountDeletion = Object.freeze({
    request,
    openPrivacy,
    openDeletionPage,
    privacyUrl: () => publicUrl('docs/privacy.html'),
    deletionUrl: () => publicUrl('docs/account-deletion.html')
  });
})();