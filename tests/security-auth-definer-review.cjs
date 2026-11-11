const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const review=JSON.parse(read('docs/security-auth-definer-review.json'));
const anon=JSON.parse(read('docs/security-anon-definer-allowlist.json'));
const live=JSON.parse(read('docs/security-definer-production-audit-20261110.json'));
const migration=read('supabase/migrations/20261007021517_security_scope_authenticated_definer_helpers.sql');
const cash=read('QueueGo-Market-Checkout-Migration.sql');

ok(review.authenticated_security_definer_total===123,'review must record current authenticated SECURITY DEFINER total');
ok(review.direct_identity_or_role_guard_count===113,'review must record directly guarded functions');
ok(review.no_direct_identity_guard_count===10,'review must account for every no-direct-guard function');
ok(review.classification.intentional_public_or_guest.length===9,'exactly 9 reviewed public/guest functions may lack account identity guard');
ok(review.classification.intentional_public_or_guest.every(x=>anon.rules.some(r=>r.signature===x)),'the 2026-10-07 historically reviewed public RPCs must remain allowlisted');
ok(anon.rules.length===review.classification.intentional_public_or_guest.length+1,'new anonymous RPCs must be reviewed separately from the historical snapshot');
ok(anon.rules.some(x=>x.signature==='queuego_ride_config()'&&x.category==='public_ride_config'),'ride fare RPC must be explicitly reviewed in the live allowlist');
ok(review.classification.downstream_guarded_authenticated_wrapper.length===1,'cash checkout must be the only downstream-guarded authenticated wrapper');
ok(review.classification.downstream_guarded_authenticated_wrapper[0].signature.startsWith('queuego_place_cash_order('),'cash order wrapper must be documented');
ok(/auth\.uid\(\) is null/.test(cash)&&/u\.role='customer'/.test(cash),'cash checkout core must keep authenticated active-customer guard');
ok(/sp\.user_id=v_user/.test(migration)&&/v_role<>'admin'/.test(migration),'effective GP rate must be own-shop or admin scoped');
ok(/revoke execute on function public\.market_public_catalog\(\) from authenticated/i.test(migration),'legacy market catalog must stay service-only');
ok(/revoke execute on function public\.queuego_nearest_market[\s\S]+from authenticated/i.test(migration),'unused nearest-market helper must stay service-only');
ok(review.conclusion==='PASS_CODE_DATABASE_REVIEW','review conclusion must remain explicit');
ok(live.snapshot_date==='2026-11-10'&&live.project_ref===review.production_project,'live production security snapshot must identify its source');
ok(live.public_schema_anonymous_security_definer_count===anon.rules.length,'the reviewed anon allowlist must match the live privilege count');
ok(live.public_schema_authenticated_security_definer_count===189&&live.public_schema_authenticated_security_definer_count>review.authenticated_security_definer_total,'live authenticated definer surface exceeds historical review');
ok(live.observed_authenticated_count_increase_since_review===live.public_schema_authenticated_security_definer_count-review.authenticated_security_definer_total,'audit must account for scope growth');
ok(live.status==='PENDING_FULL_CURRENT_SECURITY_REVIEW'&&!live.production_mutations_performed,'historical review cannot certify current production security');
ok(live.remaining_findings.leaked_password_protection_disabled===true,'disabled leaked-password protection remains an explicit platform blocker');
ok(/leaked-password protection/i.test(review.boundary),'platform Auth blocker must remain separate from code/database review');

const triage=JSON.parse(read('docs/security-definer-live-triage-20261111.json'));
ok(triage.snapshot_date==='2026-11-11'&&triage.project_ref===review.production_project,'live triage must identify the current Production snapshot');
ok(triage.classification_method.authenticated_executable_security_definer_count===live.public_schema_authenticated_security_definer_count,'triage count must match live authenticated definer count');
ok(triage.classification_method.definitions_with_inline_identity_or_role_marker+triage.classification_method.without_inline_marker===189,'marker triage must account for all current authenticated definers');
ok(triage.classification_method.without_inline_marker_but_containing_common_authorization_helper_marker+triage.classification_method.without_inline_marker_or_common_helper_marker===triage.classification_method.without_inline_marker,'helper-marker triage groups must account for every definition without inline markers');
ok(triage.anonymous_executable_without_inline_identity_marker.count===anon.rules.length&&triage.anonymous_executable_without_inline_identity_marker.signatures.length===anon.rules.length,'all current anonymous definers without inline identity markers must remain explicitly allowlisted');
ok(triage.anonymous_executable_without_inline_identity_marker.signatures.map(x=>x.replace(/\s+/g,'')).sort().join('|')===anon.rules.map(x=>x.signature.replace(/\s+/g,'')).sort().join('|'),'live anonymous triage signatures must exactly match reviewed allowlist signatures');
ok(triage.authenticated_only_without_inline_identity_marker.length===4&&triage.authenticated_only_without_inline_identity_marker.every(x=>x.anon_executable===false&&x.delegated_guard),'authenticated-only delegated guard cases must remain enumerated');
ok(triage.full_current_security_review_status==='PENDING_FULL_CURRENT_SECURITY_REVIEW'&&!triage.production_mutations_performed,'heuristic triage cannot certify full current Production security');
ok(triage.search_path_assessment.authenticated_executable_security_definer_count===189&&triage.search_path_assessment.functions_without_fixed_search_path===0,'all current authenticated definer functions must have an explicit fixed search_path');
ok(triage.search_path_assessment.functions_with_fixed_search_path===189&&triage.search_path_assessment.functions_with_nondefault_fixed_search_path===2,'search_path triage must account for every elevated function');
ok(triage.search_path_assessment.role_schema_privileges.anon.public_create===false&&triage.search_path_assessment.role_schema_privileges.authenticated.public_create===false,'untrusted API roles must not be able to create objects in public schema');
ok(triage.search_path_assessment.role_schema_privileges.anon.market_private_usage===false&&triage.search_path_assessment.role_schema_privileges.authenticated.market_private_usage===false,'private market helper schema must remain inaccessible to API roles');

console.log(JSON.stringify({checks,failures:0,scope:'Authenticated SECURITY DEFINER review, public allowlist, downstream checkout guard and scoped GP access'}));
