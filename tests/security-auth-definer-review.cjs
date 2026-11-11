const fs=require('fs'),path=require('path'),assert=require('assert'),os=require('os'),{spawnSync}=require('child_process');
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

ok(review.reviewed_surface?.authenticated_security_definer_total===142,'historical review must retain its reviewed authenticated SECURITY DEFINER count');
ok(review.reviewed_surface?.anonymous_security_definer_total===9,'historical review must retain its reviewed anonymous SECURITY DEFINER count');
ok(review.direct_identity_or_role_guard_count===118,'review must record directly/helper guarded functions');
ok(review.direct_auth_uid_count===76,'historical review must record direct auth.uid() count');
ok(review.helper_guarded_without_direct_auth_uid_count===42,'historical review must record helper-guarded count');
ok(review.no_direct_identity_guard_count===24,'review must account for every no-direct-guard function');
ok(review.classification.intentional_public_or_guest.length===9,'exactly 9 functions were classified in the historical review');
ok(JSON.stringify([...review.classification.intentional_public_or_guest].sort())===JSON.stringify(anon.rules.map(x=>x.signature).sort()),'authenticated no-direct-guard public set must equal anonymous reviewed allowlist');
ok(review.classification.downstream_guarded_authenticated_wrapper.length===1,'cash checkout wrapper must remain explicitly downstream-guarded');
ok(review.classification.downstream_guarded_pos_or_table_wrappers.length===14,'all current POS/table wrappers without direct identity checks must be classified');
ok(new Set(review.classification.downstream_guarded_pos_or_table_wrappers).size===14,'POS/table wrapper review must not contain duplicates');
ok(review.live_review?.no_direct_guard_accounting==='9 intentional public/guest + 14 POS/table helper-guarded wrappers + 1 cash-order core-guarded wrapper = 24','no-direct-guard accounting must remain exact');
ok(review.current_live_advisor_snapshot?.counts?.authenticated_security_definer_function_executable===189,'current Production snapshot must record all 189 authenticated SECURITY DEFINER functions');
ok(review.current_live_advisor_snapshot?.counts?.anon_security_definer_function_executable===10,'current Production snapshot must record all 10 anonymous SECURITY DEFINER functions');
ok(review.current_live_advisor_snapshot?.counts?.auth_leaked_password_protection===1,'current Production snapshot must retain the Auth platform warning');
ok(review.current_live_advisor_snapshot?.observed_at==='2026-11-11T06:30:24.358Z','current Production Advisor observation must retain its exact timestamp');
ok(review.current_live_advisor_snapshot?.function_inventory?.status==='NOT_CAPTURED','the snapshot must disclose that complete live function signatures have not been inventoried');
ok(review.review_status==='BLOCKED_UNREVIEWED_LIVE_DELTA','stale classification must remain blocked from release certification');
ok(review.live_review?.anonymous_allowlist_exact_match===false&&review.live_review?.anonymous_allowlist_count===10,'the live anonymous Advisor set must not be reported as matching the 9-function source allowlist');
ok(JSON.stringify(review.live_review?.unexpected_anonymous_security_definers)===JSON.stringify(['queuego_ride_config()']),'the additional anon-executable SECURITY DEFINER function must be named for follow-up review');
ok(review.live_review?.database_changes_required===null,'no Production grant or policy change may be inferred before reviewing the live delta');
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
ok(review.advisor_after_review?.authenticated_security_definer_executable===142,'historical review must retain its authenticated Advisor baseline');
ok(review.advisor_after_review?.anonymous_security_definer_executable===9,'historical review must retain its anonymous Advisor baseline');
ok(review.conclusion==='BLOCKED_UNREVIEWED_LIVE_SECURITY_DELTA','review conclusion must not claim the changed Production surface is certified');
ok(/leaked-password protection/i.test(review.boundary),'platform Auth blocker must remain separate from code/database review');

const gate=path.join(root,'native-android/qa/security_review_gate.py');
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'queuego-security-review-'));
const runGate=(document,allowlist,now='2026-11-11T06:31:00Z')=>{
  const reviewPath=path.join(temp,'review.json'),allowPath=path.join(temp,'allowlist.json');
  fs.writeFileSync(reviewPath,JSON.stringify(document));
  fs.writeFileSync(allowPath,JSON.stringify(allowlist));
  return spawnSync('python3',[gate,'--review',reviewPath,'--allowlist',allowPath,'--now',now],{encoding:'utf8'});
};
try{
  const current=runGate(review,anon);
  ok(current.status===1&&/reviewed authenticated SECURITY DEFINER count 142 does not match current live Advisor count 189/.test(current.stdout),'stale Production function review must fail the release gate on the live authenticated delta');

  const signatures=['public_one()','public_two()'];
  const authSignatures=['direct_one()','direct_two()',...signatures];
  const passing={
    version:3,production_project:'pkypiqhlrmzocysgeqew',reviewed_at:'2026-11-11T06:30:30Z',review_status:'PASS',
    current_live_advisor_snapshot:{observed_at:'2026-11-11T06:30:00Z',counts:{authenticated_security_definer_function_executable:4,anon_security_definer_function_executable:2,auth_leaked_password_protection:1},function_inventory:{observed_at:'2026-11-11T06:30:30Z',authenticated_security_definer_signatures:authSignatures,anonymous_security_definer_signatures:signatures}},
    reviewed_surface:{authenticated_security_definer_total:4,anonymous_security_definer_total:2,authenticated_function_signatures:authSignatures},
    direct_auth_uid_count:2,helper_guarded_without_direct_auth_uid_count:0,
    direct_identity_or_role_guard_count:2,no_direct_identity_guard_count:2,
    classification:{direct_auth_uid_signatures:['direct_one()','direct_two()'],helper_guarded_without_direct_auth_uid_signatures:[],intentional_public_or_guest:signatures.map(signature=>({signature})),downstream_guarded_authenticated_wrapper:[],downstream_guarded_pos_or_table_wrappers:[]},
    live_review:{anonymous_allowlist_exact_match:true,database_changes_required:false,unexpected_anonymous_security_definers:[]},
    conclusion:'PASS_CODE_DATABASE_REVIEW_CURRENT_SURFACE'
  };
  const passingAllowlist={rules:signatures.map(signature=>({signature}))};
  const accepted=runGate(passing,passingAllowlist);
  ok(accepted.status===0&&/PASS current Production/.test(accepted.stdout),'fresh complete security review with exact anonymous allowlist must be accepted');

  const stale={...passing,current_live_advisor_snapshot:{...passing.current_live_advisor_snapshot,observed_at:'2026-10-10T00:00:00Z'}};
  const staleResult=runGate(stale,passingAllowlist);
  ok(staleResult.status===1&&/snapshot is stale/.test(staleResult.stdout),'review snapshots older than 24 hours must fail closed');

  const mismatch={...passing,reviewed_surface:{...passing.reviewed_surface,anonymous_security_definer_total:1}};
  const mismatchResult=runGate(mismatch,passingAllowlist);
  ok(mismatchResult.status===1&&/reviewed anonymous SECURITY DEFINER count 1 does not match current live Advisor count 2/.test(mismatchResult.stdout),'an unmatched anonymous function must block release certification');

  const badClassification={...passing,direct_auth_uid_count:2};
  badClassification.direct_identity_or_role_guard_count=3;
  const badClassificationResult=runGate(badClassification,passingAllowlist);
  ok(badClassificationResult.status===1&&/direct and helper-guarded function counts do not reconcile/.test(badClassificationResult.stdout),'inconsistent authenticated guard classifications must block release certification');

  const missingInventory={...passing,current_live_advisor_snapshot:{...passing.current_live_advisor_snapshot,function_inventory:{...passing.current_live_advisor_snapshot.function_inventory,authenticated_security_definer_signatures:authSignatures.slice(1)}}};
  const missingInventoryResult=runGate(missingInventory,passingAllowlist);
  ok(missingInventoryResult.status===1&&/signature inventory does not match Advisor counts/.test(missingInventoryResult.stdout),'partial live signature inventory must block release certification');

  const wrongInventory={...passing,current_live_advisor_snapshot:{...passing.current_live_advisor_snapshot,function_inventory:{...passing.current_live_advisor_snapshot.function_inventory,authenticated_security_definer_signatures:['unknown()',...authSignatures.slice(1)]}}};
  const wrongInventoryResult=runGate(wrongInventory,passingAllowlist);
  ok(wrongInventoryResult.status===1&&/reviewed authenticated signatures do not match the exact live function inventory/.test(wrongInventoryResult.stdout),'unreviewed live signatures must block release certification even when counts match');

  const futureReview={...passing,reviewed_at:'2026-11-12T00:00:00Z'};
  const futureReviewResult=runGate(futureReview,passingAllowlist);
  ok(futureReviewResult.status===1&&/reviewed_at is from the future/.test(futureReviewResult.stdout),'future-dated reviews must not pass');
}finally{fs.rmSync(temp,{recursive:true,force:true});}

console.log(JSON.stringify({checks,failures:0,scope:'Live Production SECURITY DEFINER freshness gate, exact anon allowlist, historical guard review and scoped GP access'}));
