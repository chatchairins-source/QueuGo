const fs=require('fs');
const path=require('path');
const root=path.join(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const must=(cond,msg)=>{if(!cond){console.error('FAIL:',msg);process.exit(1)}};

const shared=read('account-deletion.js');
const customer=read('customer-features.js');
const customerHtml=read('index.html');
const merchant=read('merchant/index.html');
const rider=read('rider/index.html');
const privacy=read('docs/privacy.html');
const deletion=read('docs/account-deletion.html');
const sql=read('QueueGo-Pilot-Account-Deletion-Privacy.sql');

must(shared.includes('/functions/v1/account-delete'),'shared client must call account-delete edge function');
must(shared.includes("confirm: 'DELETE_ACCOUNT'"),'shared client must require destructive confirmation token');
must(customerHtml.includes('account-deletion.js'),'customer must load account deletion client');
must(customer.includes('QGCustomer.deleteAccount(this)'),'customer profile must expose in-app deletion');
must(customer.includes('นโยบายความเป็นส่วนตัว'),'customer profile must link privacy policy');
must(merchant.includes('../account-deletion.js'),'merchant must load shared deletion client');
must(merchant.includes('qgmDeleteAccount(this)'),'merchant profile must expose in-app deletion');
must(rider.includes('../account-deletion.js'),'rider must load shared deletion client');
must(rider.includes('qg-delete-account'),'rider profile must expose in-app deletion');
must(privacy.includes('นโยบายความเป็นส่วนตัว'),'privacy policy must be public');
must(privacy.includes('account-deletion.html'),'privacy policy must link deletion page');
must(deletion.includes('../account-deletion.js'),'external deletion page must use same backend');
must(deletion.includes('เข้าสู่ระบบเพื่อยืนยัน'),'external deletion page must authenticate account owner');
must(sql.includes('queuego_account_deletion_eligibility'),'SQL source must include eligibility gate');
must(sql.includes("delivery_address='ข้อมูลถูกลบตามคำขอเจ้าของบัญชี'"),'market order PII scrub must respect NOT NULL');
must(!sql.includes('set delivery_address=null,delivery_latitude=null,delivery_longitude=null\n     where customer_id=v_user.id;'),'market order scrub must not null NOT NULL columns');
must(sql.includes('delete from public.qg_ugc_terms_acceptances where user_id=v_user.id;'),'account deletion must remove UGC terms acceptance');
must(sql.includes('delete from public.qg_user_blocks where blocker_user_id=v_user.id or blocked_user_id=v_user.id;'),'account deletion must remove UGC blocks');
must(sql.includes('content_snapshot=case when reporter_user_id=v_user.id or reported_user_id=v_user.id then null else content_snapshot end'),'account deletion must scrub reported chat snapshots');
must(privacy.includes('รายงานหรือบล็อกคู่สนทนา'),'privacy policy must disclose chat report/block rights');
must(privacy.includes('สำเนาเนื้อหา'),'privacy policy must disclose moderation snapshot retention and cleanup');
must(privacy.includes('อายุเกิน 30 วันโดยอัตโนมัติทุกวัน'),'privacy policy must disclose the enforced daily purge of voice-call metadata older than 30 days');

console.log('account deletion/privacy gate checks passed');
