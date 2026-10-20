const fs=require('fs'),assert=require('assert/strict');
const html=fs.readFileSync('admin/index.html','utf8');
const loader=fs.readFileSync('admin/section-loader.js','utf8');
const mapSql=fs.readFileSync('supabase/migrations/20261014074523_rider_map_feedback_v1.sql','utf8');
const alertSql=fs.readFileSync('supabase/migrations/20261014110355_rider_road_alerts_v1.sql','utf8');
const fkSql=fs.readFileSync('supabase/migrations/20261014110429_rider_road_alerts_fk_index_v2.sql','utf8');
let checks=0;
for(const needle of [
 'qg-admin-map-feedback-road-alerts',
 'qt-admin-rider-map-feedback-panel',
 "readRpc('qg_admin_rider_map_feedback_queue'",
 "qtSupabaseRpc('qg_admin_review_rider_map_feedback'",
 "qtSupabaseRpc('qg_admin_publish_rider_road_alert'",
 "qtSupabaseRpc('qg_admin_disable_rider_road_alert'",
 'qgAdminViewLocation',
 'ดูบน QueueGo Map',
 'accepted_for_review'
]){assert(html.includes(needle),needle);checks++}
const feature=html.slice(html.indexOf('qg-admin-map-feedback-road-alerts'),html.indexOf('QueueGo V5.51'));
assert(!/google\.com\/maps|maps\.google/i.test(feature));checks++;
assert(!/qg_private\.(?:rider_map_feedback|rider_road_alerts)/.test(html));checks++;
assert(loader.includes('qtRenderAdminRiderMapFeedback'));checks++;
assert(loader.includes('options.readOnly'));checks++;
for(const [sql,name] of [[mapSql,'qg_admin_rider_map_feedback_queue'],[mapSql,'qg_admin_review_rider_map_feedback'],[alertSql,'qg_admin_publish_rider_road_alert'],[alertSql,'qg_admin_disable_rider_road_alert']]){
 assert(sql.includes('public.'+name));checks++;
 assert.match(sql,new RegExp('revoke all on function public\\.'+name,'i'));checks++;
 assert.match(sql,/security invoker/i);checks++;
}
assert.match(mapSql,/least\(coalesce\(p_limit,100\),200\)/i);checks++;
assert.match(alertSql,/v_duration not between 15 and 10080/i);checks++;
assert(alertSql.includes("('road_closed','bad_road','unsafe_turn')"));checks++;
assert.match(fkSql,/create index/i);checks++;
console.log(JSON.stringify({checks,failures:0,scope:'Admin Map Feedback/Road Alerts reuses Production RPCs and QueueGo Map without private-table browser access'}));
