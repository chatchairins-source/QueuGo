const fs=require('fs'),assert=require('assert/strict'),{JSDOM}=require('jsdom');
const tick=async()=>{for(let i=0;i<14;i++)await new Promise(r=>setImmediate(r))};
(async()=>{
 const html=fs.readFileSync('admin/index.html','utf8');
 const start=html.indexOf(' let qgAdminUGCCursor=null');
 const end=html.indexOf('\n})();',start);
 assert(start>0&&end>start,'UGC paging block missing');
 const block=html.slice(start,end);
 const d=new JSDOM('<main></main>',{url:'https://isolated.invalid/admin/#admin',runScripts:'outside-only'}),w=d.window;
 const uid=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
 let actor={id:uid(1),type:'admin',status:'approved'},reads=[],writes=[],holdNext=false,releaseHeld=null,toasts=[],checks=0;
 const report=(i,at='2026-10-20T04:00:00.000Z')=>({id:uid(1000+i),reporter_user_id:uid(2000+i),reported_user_id:uid(3000+i),order_id:null,message_id:null,reason:'other',details:'report <'+i+'>',status:'pending',admin_id:null,admin_note:null,content_snapshot:'snapshot <'+i+'>',created_at:at,updated_at:at});
 const first=Array.from({length:100},(_,i)=>report(i));
 const second=[report(100,'2026-10-19T03:59:59.000Z'),report(101,'2026-10-19T03:59:58.000Z')];
 const rpcRead=async(name,args)=>{
   reads.push({name,args});
   if(name!=='qg_admin_ugc_reports_page')throw Error('unexpected read '+name);
   if(holdNext){holdNext=false;await new Promise(r=>{releaseHeld=r})}
   return args?.p_cursor_id?{items:second,limit:100,more:false}:{items:first,limit:100,more:true};
 };
 const req=async(path,method='GET',body=null)=>{
   writes.push({path,method,body});
   if(path==='rpc/qg_admin_ugc_report_action'&&method==='POST')return {ok:true};
   throw Error('unexpected mutation/read '+method+' '+path);
 };
 const safe=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 w.currentUser=()=>actor;w.toast=x=>toasts.push(x);w.qgAdminTrace=()=>{};w.__ugcRead=rpcRead;w.__ugcReq=req;w.__ugcSafe=safe;
 w.eval('(function(){const active=()=>currentUser()?.type==="admin"&&currentUser()?.status==="approved";const safe=v=>window.__ugcSafe(v);const req=(...args)=>window.__ugcReq(...args);const qgAdminRpcRead=(...args)=>window.__ugcRead(...args);'+block+'\n})();');
 w.qgAdminUGCReports();await tick();
 assert.equal(reads.length,1);assert.equal(reads[0].name,'qg_admin_ugc_reports_page');assert.equal(reads[0].args.p_limit,100);assert.equal(reads[0].args.p_cursor_id,null);checks+=4;
 assert.equal(w.document.querySelectorAll('#qg-v22-admin-ugc article').length,100);assert.equal(w.document.querySelectorAll('#qg-v22-admin-ugc script').length,0);checks+=2;
 assert.equal(w.document.getElementById('qg-ugc-newer').disabled,true);assert.equal(w.document.getElementById('qg-ugc-older').disabled,false);checks+=2;
 w.document.getElementById('qg-ugc-older').click();await tick();
 const older=reads.at(-1);assert.equal(older.args.p_cursor_created_at,'2026-10-20T04:00:00.000Z');assert.equal(older.args.p_cursor_id,uid(1099));checks+=2;
 assert.equal(w.document.querySelectorAll('#qg-v22-admin-ugc article').length,2);assert.equal(w.document.getElementById('qg-ugc-newer').disabled,false);assert.equal(w.document.getElementById('qg-ugc-older').disabled,true);checks+=3;
 // Moderation mutation must keep the current older cursor and use the existing action RPC.
 reads=[];writes=[];const btn=w.document.querySelector('#qg-v22-admin-ugc [data-ugc-action]');const sel=w.document.querySelector('#qg-v22-admin-ugc select[data-ugc-status]');sel.value='resolved';btn.click();await tick();
 assert(writes.some(x=>x.path==='rpc/qg_admin_ugc_report_action'&&x.method==='POST'&&x.body.p_status==='resolved'));assert.equal(reads.at(-1).args.p_cursor_id,uid(1099));checks+=2;
 w.document.getElementById('qg-ugc-newer').click();await tick();assert.equal(reads.at(-1).args.p_cursor_id,null);checks++;
 // Old-account response must not repaint the modal.
 holdNext=true;const before=w.document.querySelector('.qg-content').textContent;const pending=w.qgAdminLoadUGCReports();await tick();actor={id:uid(9),type:'customer',status:'approved'};assert.equal(typeof releaseHeld,'function');releaseHeld();await pending;await tick();assert.equal(w.document.querySelector('.qg-content').textContent,before);checks+=2;
 assert(block.includes("qgAdminRpcRead('qg_admin_ugc_reports_page'"));assert(!block.includes("req('rpc/qg_admin_ugc_reports'"));assert(!block.includes('qg_ugc_reports?'));checks+=3;
 const sql=fs.readFileSync('supabase/migrations/20261020044500_admin_ugc_report_paging.sql','utf8');
 for(const required of ['qg_private.admin_ugc_reports_page','qg_private.assert_live_session()','public.is_active_admin()','public.qg_admin_ugc_reports_page','security invoker','qg_ugc_reports_recent_idx','limit v_limit+1'])assert(sql.toLowerCase().includes(required.toLowerCase()),required);
 assert(/revoke all on function public\.qg_admin_ugc_reports_page[\s\S]*from public,anon/i.test(sql));assert(/grant execute on function public\.qg_admin_ugc_reports_page[\s\S]*to authenticated,service_role/i.test(sql));checks+=9;
 d.window.close();
 console.log(JSON.stringify({checks,failures:0,scope:'Admin UGC moderation uses guarded cancellable 100-row keyset pages, same-page action refresh, stale-account fencing and no direct private-table client access'}));
})().catch(e=>{console.error(e);process.exit(1)});
