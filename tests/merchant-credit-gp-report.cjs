const fs=require('fs');
const assert=require('assert');
const merchant=fs.readFileSync('merchant/index.html','utf8');
const migration=fs.readFileSync('supabase/migrations/20261021024500_merchant_credit_gp_report.sql','utf8');

assert(merchant.includes('GP ที่หักจากเครดิต'), 'merchant UI must name the credit deduction');
assert(merchant.includes('gp_credit_deducted'), 'merchant UI must use the credit GP field');
assert(!merchant.includes('<small>GP ที่ต้องชำระ</small>'), 'legacy due must not be presented as current credit GP');
assert(merchant.includes('GP เดิมก่อนระบบเครดิต'), 'legacy pre-credit balance must remain explicitly separated');
assert(migration.includes('gp_credit_deducted numeric'), 'RPC must expose credit GP separately');
assert(migration.includes("e.event_type in ('capture','reversal')"), 'credit GP must come from captured/reversed ledger entries');
assert(migration.includes('filter(where not o.credit_gp_required)'), 'legacy due must exclude credit-mode orders');
assert(migration.includes('e.user_id=v_user'), 'ledger aggregation must stay merchant scoped');

console.log('merchant credit GP report 8 checks passed');
