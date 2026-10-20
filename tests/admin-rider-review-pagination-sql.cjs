const fs=require('fs'),assert=require('assert/strict');
const sql=fs.readFileSync('supabase/migrations/20261020021801_admin_rider_review_pagination.sql','utf8'),ops=fs.readFileSync('admin/operations.js','utf8');let checks=0;
const has=s=>{assert(sql.includes(s),s);checks++};
has('qg_private.admin_rider_review_page(');
has("p_kind not in ('identity','inspection','vehicle','documents')");
has('perform qg_private.assert_live_session()');
has('not public.is_active_admin()');
has('limit v_limit+1 offset v_offset');
has("'more',v_more");
has('public.qg_admin_rider_review_page(');
has('revoke all on function public.qg_admin_rider_review_page(text,integer,integer) from public,anon');
has('grant execute on function public.qg_admin_rider_review_page(text,integer,integer) to authenticated,service_role');
assert(!/create or replace function public\.qg_admin_rider_review_page[\s\S]{0,300}security definer/i.test(sql));checks++;
for(const pair of [["admin_rider_reverification_queue","'identity',200,0"],["admin_rider_vehicle_inspection_queue","'inspection',200,0"],["admin_rider_vehicle_queue","'vehicle',200,0"],["admin_rider_document_queue","'documents',200,0"]]){has(pair[0]+'()');has(pair[1])}
assert(ops.includes("qgAdminOpsReadRpc('qg_admin_rider_review_page',{p_kind:kind,p_limit:reviewPageSize,p_offset:offset})"));checks++;
assert(!ops.includes("qtSupabaseRpc('qg_admin_rider_review_page',{p_kind:kind,p_limit:reviewPageSize,p_offset:offset})"));checks++;
assert(ops.includes('reviewPageSize=50'));checks++;
assert(!ops.includes('(rows||[]).slice(0,200)'));checks++;
console.log(JSON.stringify({checks,failures:0,scope:'Admin Rider review centralized bounded reader, role/session guards, explicit API grants, compatibility delegates and 50-row Web paging'}));