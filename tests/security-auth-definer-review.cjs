const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const review=JSON.parse(read('docs/security-auth-definer-review.json'));
const anon=JSON.parse(read('docs/security-anon-definer-allowlist.json'));
const migration=read('supabase/migrations/20261007021517_security_scope_authenticated_definer_helpers.sql');
const cash=read('QueueGo-Market-Checkout-Migration.sql');
const pos=read('QueueGo-POS-Migration.sql');
const posWorkflow=read('QueueGo-POS-Workflow-Migration.sql');
const posDelivery=read('QueueGo-POS-Delivery-Opt-In-Migration.sql');
const posPayment=read('QueueGo-POS-Payment-Retry-Migration.sql');
const tableQr=read('QueueGo-Table-QR-v1-Migration.sql');

ok(review.authenticated_security_definer_total===142,'review must record current authenticated SECURITY DEFINER total');
ok(review.direct_identity_or_role_guard_count===118,'review must record directly/helper guarded functions');
ok(review.direct_auth_uid_count===76,'review must record current direct auth.uid() count');
ok(review.helper_guarded_without_direct_auth_uid_count===42,'review must record current helper-guarded count');
ok(review.no_direct_identity_guard_count===24,'review must account for every no-direct-guard function');
ok(review.classification.intentional_public_or_guest.length===9,'exactly 9 reviewed public/guest functions may lack account identity guard');
ok(JSON.stringify([...review.classification.intentional_public_or_guest].sort())===JSON.stringify(anon.rules.map(x=>x.signature).sort()),'authenticated no-direct-guard public set must equal anonymous reviewed allowlist');
ok(review.classification.downstream_guarded_authenticated_wrapper.length===1,'cash checkout wrapper must remain explicitly downstream-guarded');
ok(review.classification.downstream_guarded_pos_or_table_wrappers.length===14,'all current POS/table wrappers without direct identity checks must be classified');
ok(new Set(review.classification.downstream_guarded_pos_or_table_wrappers).size===14,'POS/table wrapper review must not contain duplicates');
ok(review.live_review?.no_direct_guard_accounting==='9 intentional public/guest + 14 POS/table helper-guarded wrappers + 1 cash-order core-guarded wrapper = 24','no-direct-guard accounting must remain exact');
ok(review.live_review?.anonymous_allowlist_exact_match===true&&review.live_review?.anonymous_allowlist_count===9,'live anonymous Advisor set must exactly match the reviewed allowlist');
ok(review.live_review?.database_changes_required===false,'review must not pretend a database hardening migration was needed when live definitions were already guarded');
ok(review.classification.downstream_guarded_authenticated_wrapper[0].signature.startsWith('queuego_place_cash_order('),'cash order wrapper must be documented');
ok(/auth\.uid\(\) is null/.test(cash)&&/u\.role='customer'/.test(cash),'cash checkout core must keep authenticated active-customer guard');
ok(/create or replace function public\.pos_my_shop/.test(pos)&&/auth\.uid\(\)/.test(pos),'POS shop helper must bind the authenticated identity');
ok(/create or replace function public\.pos_is_owner/.test(pos)&&/u\.role='shop'/.test(pos)&&/u\.status='active'/.test(pos),'POS owner helper must require an active shop owner');
ok(/create or replace function public\.pos_allowed/.test(pos)&&/s\.active/.test(pos),'POS permission helper must require active staff or owner access');
for(const source of [pos,posWorkflow,posDelivery,posPayment,tableQr]){
  ok(/pos_allowed|pos_is_owner|pos_my_shop/.test(source),'reviewed POS/table source must delegate authorization through scoped helpers');
}
ok(/qg_table_rotate_qr[\s\S]*pos_allowed\('manage_staff'\)[\s\S]*shop_id=public\.pos_my_shop\(\)/.test(tableQr),'QR rotation must require manage_staff and same-shop ownership');
ok(/sp\.user_id=v_user/.test(migration)&&/v_role<>'admin'/.test(migration),'effective GP rate must be own-shop or admin scoped');
ok(/revoke execute on function public\.market_public_catalog\(\) from authenticated/i.test(migration),'legacy market catalog must stay service-only');
ok(/revoke execute on function public\.queuego_nearest_market[\s\S]+from authenticated/i.test(migration),'unused nearest-market helper must stay service-only');
ok(review.advisor_after_review?.authenticated_security_definer_executable===142,'review must retain the live authenticated Advisor count');
ok(review.advisor_after_review?.anonymous_security_definer_executable===9,'review must retain the live anonymous Advisor count');
ok(review.conclusion==='PASS_CODE_DATABASE_REVIEW_CURRENT_SURFACE','review conclusion must explicitly cover the current Production surface');
ok(/leaked-password protection/i.test(review.boundary),'platform Auth blocker must remain separate from code/database review');

console.log(JSON.stringify({checks,failures:0,scope:'Authenticated SECURITY DEFINER review, public allowlist, downstream checkout guard and scoped GP access'}));
