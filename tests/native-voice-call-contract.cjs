const fs = require('fs');
const assert = require('assert');

const sql = fs.readFileSync('ops/native-voice-call-schema-proposal-20261010.sql','utf8');
const lower = sql.toLowerCase();

function has(pattern, message) {
  assert(pattern.test(sql), message);
}

has(/create table if not exists public\.qg_call_sessions/i, 'call session table missing');
has(/alter table public\.qg_call_sessions enable row level security/i, 'call table RLS missing');
has(/revoke all on public\.qg_call_sessions from public,anon,authenticated/i, 'direct client table access must be revoked');
has(/qg_call_sessions_no_client_access[\s\S]*for all to anon,authenticated using\(false\) with check\(false\)/i,
  'deny-all client policy missing');

const tableBody = sql.match(/create table if not exists public\.qg_call_sessions \(([\s\S]*?)\n\);/i)?.[1] || '';
assert(tableBody, 'call table body unavailable');
assert(!/\b(sdp|candidate|audio|recording|media_blob|signal_payload)\b/i.test(tableBody),
  'media/signaling payload must never be persisted in call table');
assert(/caller_session_id uuid not null/i.test(tableBody), 'caller app session must be persisted');
assert(/callee_session_id uuid/i.test(tableBody), 'callee app session must be persisted');
assert(/topic text not null unique/i.test(tableBody), 'private topic must be unique');
assert(/topic = 'qg-call:' \|\| id::text/i.test(tableBody), 'topic must be derived from call UUID');

has(/create unique index if not exists qg_call_sessions_active_pair_uq[\s\S]*where status in \('ringing','accepted'\)/i,
  'concurrent active pair guard missing');

for (const fn of ['qg_call_start','qg_call_active','qg_call_answer','qg_call_decline','qg_call_end','qg_call_ice_config']) {
  has(new RegExp('create or replace function public\\.' + fn, 'i'), fn + ' missing');
}
const startSignature = sql.match(/create or replace function public\.qg_call_start[\s\S]*?\) returns jsonb/i)?.[0] || '';
assert(!/p_(callee|counterpart|target_user|user_id)\s+uuid/i.test(startSignature),
  'caller must not submit arbitrary callee user id');

const activeSessionChecks = (lower.match(/check_active_session\(p_session_id\)/g) || []).length;
assert(activeSessionChecks >= 6, 'every call RPC must validate current app session');
has(/qg_user_blocks[\s\S]*blocker_user_id=v_actor_id[\s\S]*blocked_user_id=v_callee_id/i,
  'two-party block enforcement missing');
has(/created_at>now\(\)-interval '5 minutes'[\s\S]*>= 3/i, 'call rate limit missing');
has(/'voice_call'/i, 'incoming call notification type missing');
has(/insert into public\.notifications/i, 'incoming call must use existing push pipeline');

has(/create or replace function qg_private\.qg_voice_realtime_allowed/i, 'private realtime authorization helper missing');
has(/user_active_sessions[\s\S]*revoked_at is null/i, 'realtime must require a live app session');
has(/caller_session_id=v_current_session/i, 'caller realtime must bind to stored caller session');
has(/callee_session_id=v_current_session/i, 'callee realtime must bind to stored callee session');
assert((lower.match(/join public\.user_active_sessions [cr]s on [cr]s\.user_id=[cr]u\.auth_user_id/g) || []).length >= 4,
  'both accepted participants must keep their app sessions');
has(/status='accepted'[\s\S]*caller_session_id[\s\S]*callee_session_id/i,
  'accepted calls must be ended when either stored app session is no longer live');
assert((lower.match(/lower\(o\.status\) not in \('cancelled','completed','no_rider_available'\)/g) || []).length >= 4,
  'call authorization must be scoped to active orders');
has(/status in \('ringing','accepted'\)[\s\S]*not exists\([\s\S]*public\.orders/i,
  'terminal orders must end active calls');
has(/create policy qg_voice_realtime_select[\s\S]*extension='broadcast'[\s\S]*qg_private\.qg_voice_realtime_allowed/i,
  'private realtime select policy missing');
has(/create policy qg_voice_realtime_insert[\s\S]*extension='broadcast'[\s\S]*qg_private\.qg_voice_realtime_allowed/i,
  'private realtime insert policy missing');

assert(!/create table[^;]*(signal|sdp|ice_candidate)/i.test(sql),
  'signaling must not be stored in a database table');
assert(/stun:stun\.cloudflare\.com:3478/i.test(sql), 'real STUN fallback missing');
assert(/'turn_ready',false/i.test(sql), 'proposal must not claim TURN is release-ready');
assert(/no TURN secret belongs in SQL\/APK/i.test(sql), 'TURN secret safety note missing');

for (const fn of ['qg_call_start','qg_call_active','qg_call_answer','qg_call_decline','qg_call_end','qg_call_ice_config']) {
  has(new RegExp('revoke all on function public\\.' + fn + '[\\s\\S]*? from public,anon', 'i'),
    fn + ' anon revoke missing');
  has(new RegExp('grant execute on function public\\.' + fn + '[\\s\\S]*? to authenticated,service_role', 'i'),
    fn + ' authenticated grant missing');
}

console.log('Native voice call SQL contract: PASS');
