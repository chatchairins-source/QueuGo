const fs=require('fs'),vm=require('vm'),assert=require('assert/strict');
(async()=>{
 let checks=0,readCalls=[],fetchCalls=[];
 const html=fs.readFileSync('admin/index.html','utf8'),start=html.indexOf('async function qtSupabaseRpc('),end=html.indexOf('async function qtSupabaseTable(',start);
 assert(start>0&&end>start);checks++;
 const ctx={window:null,AbortSignal,AbortController,QT_SUPABASE_CONFIG:{URL:'https://isolated.invalid'},qtGetAccessToken:async()=> 'admin-token',qtSupabaseHeaders:()=>({}),fetch:async(url,options)=>{fetchCalls.push({url,options});return {ok:true,status:200,json:async()=>({ok:true})}}};ctx.window=ctx;
 ctx.qgAdminRead=async(path,options,read)=>{const controller=new AbortController();readCalls.push({path,options,signal:controller.signal});return read(controller.signal)};
 vm.createContext(ctx);vm.runInContext(html.slice(start,end),ctx);
 const result=await ctx.qtSupabaseRpcRead('qg_admin_live_counts',{});
 assert.equal(result.ok,true);assert.equal(readCalls.length,1);assert.equal(readCalls[0].options.method,'POST');assert.equal(readCalls[0].options.readOnly,true);assert(readCalls[0].path.startsWith('rpc/qg_admin_live_counts:'));checks+=5;
 assert.equal(fetchCalls.at(-1).options.signal,readCalls[0].signal);checks++;
 await assert.rejects(()=>ctx.qtSupabaseRpcRead('qg_admin_set_rider_document_expiry',{}),/RPC_NOT_ALLOWLISTED_AS_READ_ONLY/);assert.equal(fetchCalls.length,1);checks+=2;
 await ctx.qtSupabaseRpc('qg_admin_set_rider_document_expiry',{});assert.equal(readCalls.length,1);assert.equal(fetchCalls.length,2);assert.equal(fetchCalls.at(-1).options.method,'POST');checks+=3;

 const sources={
  operations:fs.readFileSync('admin/operations.js','utf8'),
  credit:fs.readFileSync('admin/credit.js','utf8'),
  ride:fs.readFileSync('admin/passenger-ride.js','utf8'),
  index:html
 };
 for(const [file,rpc] of [
  ['operations','qg_admin_live_counts'],['operations','qg_admin_dispatch_overview'],['operations','qg_admin_notification_health'],['operations','qg_admin_rider_review_page'],
  ['credit','queuego_admin_merchant_credit_targets'],['credit','queuego_admin_credit_accounts'],
  ['ride','queuego_ride_config'],['ride','queuego_admin_public_licenses']
 ]){assert(sources[file].includes("qtSupabaseRpcRead('"+rpc+"'"));checks++}
 for(const [file,needle] of [
  ['credit',"qtSupabaseRpc('queuego_admin_grant_merchant_credit'"],['credit',"qtSupabaseRpc('queuego_admin_reverse_credit'"],
  ['ride',"qtSupabaseRpc('queuego_admin_configure_ride'"],['ride',"qtSupabaseRpc('queuego_admin_review_public_license'"],
  ['operations',"qtSupabaseRpc('qg_admin_set_rider_document_expiry'"],['operations','qtSupabaseRpc(q.request'],['operations','qtSupabaseRpc(q.review'],
  ['index',"qtSupabaseRpc('admin_delete_user'"]
 ]){assert(sources[file].includes(needle));checks++}
 assert(!/qtSupabaseRpcRead\(['"](?:queuego_admin_grant_merchant_credit|queuego_admin_reverse_credit|queuego_admin_configure_ride|queuego_admin_review_public_license|qg_admin_set_rider_document_expiry|admin_delete_user)/.test(Object.values(sources).join('\n')));checks++;
 console.log(JSON.stringify({checks,failures:0,scope:'allowlisted Admin read-only RPC cancellation; mutation RPCs remain non-cancellable through the normal mutation helper'}));
})().catch(e=>{console.error(e);process.exit(1)});
