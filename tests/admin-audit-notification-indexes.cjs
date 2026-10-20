const fs=require('fs'),assert=require('assert/strict');
const sql=fs.readFileSync('supabase/migrations/20261020022520_admin_audit_notification_paging_indexes.sql','utf8');
let checks=0;
for(const expected of [
  /create index if not exists audit_logs_created_id_idx\s+on public\.audit_logs \(created_at desc, id desc\)/i,
  /create index if not exists notifications_user_created_id_idx\s+on public\.notifications \(user_id, created_at desc, id desc\)/i
]){assert.match(sql,expected);checks++}
assert(!/security definer|grant\s+(insert|update|delete)|disable row level security/i.test(sql));checks++;
console.log(JSON.stringify({checks,failures:0,scope:'source mirror for already-deployed bounded Admin audit/notification keyset ordering indexes'}));
