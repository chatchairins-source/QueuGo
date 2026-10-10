const fs=require('fs'),assert=require('assert');

const read=p=>fs.readFileSync(p,'utf8');
const migration=read('supabase/migrations/20261010165500_merchant_order_item_substitution_idempotency.sql');
const backup=read('ops/qg-merchant-action-once-backup-20261010.sql');
const api=read('native-android/merchant/src/main/java/com/queuego/merchant/MerchantApi.kt');

let checks=0;
const ok=(v,m)=>{assert.ok(v,m);checks++};

ok(backup.includes("if v_kind='order' then")&&!backup.includes("v_kind='order_items'"),
  'Backup snapshot must preserve the pre-extension order-only wrapper');
ok(migration.includes("elsif v_kind='order_items' then"),
  'Shared Merchant idempotency wrapper must add order_items kind');
ok(migration.includes("perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,0))"),
  'Order-item edits must reuse the existing advisory request lock');
ok(migration.includes('from public.qg_merchant_action_receipts')&&migration.includes('where request_id=p_request_id'),
  'Order-item edits must reuse the existing receipt table');
ok(migration.includes("v_existing.kind<>v_kind")&&migration.includes("v_existing.payload<>v_payload"),
  'Receipt replay must reject request-id reuse with different kind/payload');
ok(migration.includes("return v_existing.result || jsonb_build_object('replayed',true)"),
  'Exact replay must return the stored result without executing the edit again');
ok(migration.includes('public.qg_merchant_edit_order_items('),
  'order_items wrapper must delegate to the existing Production substitution implementation');
ok(migration.includes("jsonb_build_object('replayed',false)"),
  'First execution must explicitly mark a non-replayed result');
ok(migration.includes('insert into public.qg_merchant_action_receipts(request_id,user_id,kind,payload,result)'),
  'First substitution execution must persist an idempotency receipt');
ok(migration.includes('grant execute on function public.qg_merchant_action_once(uuid,text,jsonb) to authenticated'),
  'Shared wrapper execute permission must remain authenticated-only');

ok(api.includes('"qg_merchant_action_once"'),'Native substitution must use the shared Merchant idempotency RPC');
ok(api.includes('.put("p_kind", "order_items")'),'Native substitution must select order_items receipt kind');
ok(api.includes('"merchant-items:" + auth.user.id + ":" + orderId'),'Native substitution request identity must bind merchant and order');
ok(api.includes('payload.toString() + ":" + normalizedReason'),'Native substitution request identity must bind the desired payload/reason');
ok(api.includes('UUID.nameUUIDFromBytes'),'Native substitution must derive a stable retry request id');
ok(!/http\.rpc\(\s*"qg_merchant_edit_order_items"/.test(api),
  'Native client must not bypass the idempotency wrapper with a direct substitution RPC');

console.log(JSON.stringify({checks,failures:0,scope:'Merchant order-item substitution retry/idempotency extension using the existing receipt system'}));
