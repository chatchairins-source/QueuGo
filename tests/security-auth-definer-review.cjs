const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const review=JSON.parse(read('docs/security-auth-definer-review.json'));
const anon=JSON.parse(read('docs/security-anon-definer-allowlist.json'));
const migration=read('supabase/migrations/20261007021517_security_scope_authenticated_definer_helpers.sql');
const cash=read('QueueGo-Market-Checkout-Migration.sql');

ok(review.authenticated_security_definer_total===123,'review must record current authenticated SECURITY DEFINER total');
ok(review.direct_identity_or_role_guard_count===113,'review must record directly guarded functions');
ok(review.no_direct_identity_guard_count===10,'review must account for every no-direct-guard function');
ok(review.classification.intentional_public_or_guest.length===9,'exactly 9 reviewed public/guest functions may lack account identity guard');
ok(JSON.stringify([...review.classification.intentional_public_or_guest].sort())===JSON.stringify(anon.rules.map(x=>x.signature).sort()),'authenticated no-direct-guard public set must equal anonymous reviewed allowlist');
ok(review.classification.downstream_guarded_authenticated_wrapper.length===1,'cash checkout must be the only downstream-guarded authenticated wrapper');
ok(review.classification.downstream_guarded_authenticated_wrapper[0].signature.startsWith('queuego_place_cash_order('),'cash order wrapper must be documented');
ok(/auth\.uid\(\) is null/.test(cash)&&/u\.role='customer'/.test(cash),'cash checkout core must keep authenticated active-customer guard');
ok(/sp\.user_id=v_user/.test(migration)&&/v_role<>'admin'/.test(migration),'effective GP rate must be own-shop or admin scoped');
ok(/revoke execute on function public\.market_public_catalog\(\) from authenticated/i.test(migration),'legacy market catalog must stay service-only');
ok(/revoke execute on function public\.queuego_nearest_market[\s\S]+from authenticated/i.test(migration),'unused nearest-market helper must stay service-only');
ok(review.conclusion==='PASS_CODE_DATABASE_REVIEW','review conclusion must remain explicit');
ok(/leaked-password protection/i.test(review.boundary),'platform Auth blocker must remain separate from code/database review');

console.log(JSON.stringify({checks,failures:0,scope:'Authenticated SECURITY DEFINER review, public allowlist, downstream checkout guard and scoped GP access'}));
