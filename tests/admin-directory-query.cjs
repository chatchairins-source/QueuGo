const fs=require('fs'),vm=require('vm'),assert=require('assert/strict');
(async()=>{const html=fs.readFileSync('admin/index.html','utf8'),start=html.indexOf('let QT_ADMIN_DIRECTORY_PROMISE=null'),end=html.indexOf('async function qtAdminRefreshScoped(',start);let actor='admin',calls=[],gate,release,overviewMissing=false,checks=0;
const uid=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0'),ctx={window:null,QT_DB_CACHE:{},QT_SYNC:{},qtSessionRead:()=>({role:actor==='admin'?'admin':'customer',authUserId:actor}),qtGetAccessToken:async()=> 'fixture',qtAccessToken:()=> 'fixture',qtUuidLike:v=>/^[a-f0-9-]{36}$/.test(v),qtMarkClean:()=>{},currentUser:()=>({id:uid(1),type:'admin'}),qtUserFromRow:x=>({...x,type:x.role,authUserId:x.auth_user_id})};ctx.window=ctx;
const qOf=path=>new URL('https://isolated.invalid/'+path).searchParams;
ctx.qtSupabaseTable=async(path,options)=>{
 calls.push({path,options});
 if(path.startsWith('users?')){
   const p=qOf(path),select=p.get('select')||'',or=p.get('or')||'',id=p.get('id')||'';
   if(select==='id'){
     if(or.includes('old')&&gate)await gate;
     if(or.includes('new'))return [{id:uid(3)}];
     if(or.includes('วิชัย'))return [{id:uid(2)}];
     return [];
   }
   if(id.startsWith('in.(')){
     const ids=id.slice(4,-1).split(',').filter(Boolean);
     return ids.map(x=>({id:x,role:x===uid(4)?'shop':'customer',name:x===uid(4)?'บัญชีร้านทอง':'วิชัย',metadata:{}}));
   }
   return [{id:uid(2),role:'customer',name:'page',metadata:{}}];
 }
 if(path.startsWith('shop_profiles?')&&qOf(path).get('select')==='user_id'){
   const or=qOf(path).get('or')||'';
   return or.includes('ร้านทอง')?[{user_id:uid(4)}]:[];
 }
 if(path.startsWith('rpc/queuego_admin_shop_overview')){
   if(overviewMissing)throw Error('PGRST202 Could not find the function queuego_admin_shop_overview(p_user_ids)');
   assert(Array.isArray(options?.body?.p_user_ids));assert(options.body.p_user_ids.length<=200);checks+=2;return [];
 }
 return [];
};
vm.createContext(ctx);vm.runInContext(html.slice(start,end),ctx);
assert.equal(ctx.qgAdminDirectoryNormalizeQuery(' วิชัย  081-123 '),'วิชัย 081-123');assert(!/[(),.%*]/.test(ctx.qgAdminDirectoryNormalizeQuery('x),status.eq.active%*')));assert.equal(ctx.qgAdminDirectoryNormalizeQuery('x'.repeat(100)).length,80);assert.equal(ctx.qgAdminDirectoryNormalizeQuery('กำพล'),'กำพล');checks+=4;
ctx.QG_ADMIN_DIRECTORY_QUERY='วิชัย';await Promise.all([ctx.qtHydrateAdminDirectory(),ctx.qtHydrateAdminDirectory()]);
assert.equal(calls.length,7);assert(calls[0].path.includes('users?select=id&'));assert(calls[1].path.includes('shop_profiles?select=user_id'));assert(decodeURIComponent(calls[0].path).includes('(name.ilike.*วิชัย*,phone.ilike.*วิชัย*)'));checks+=4;
assert(calls.slice(3).every(x=>x.path.includes(uid(2))));assert.equal(ctx.QT_DB_CACHE.qt_users.length,2);assert.deepEqual(Array.from(ctx.QG_ADMIN_DIRECTORY_PAGE.ids),[uid(2)]);checks+=3;
ctx.QG_ADMIN_DIRECTORY_QUERY='ร้านทอง';ctx.QG_ADMIN_DIRECTORY_CURSOR='';calls=[];await ctx.qtHydrateAdminDirectory();
assert(calls[1].path.includes('shop_name.ilike')||decodeURIComponent(calls[1].path).includes('shop_name.ilike'));assert.equal(ctx.QG_ADMIN_DIRECTORY_PAGE.ids[0],uid(4));assert.equal(ctx.QT_DB_CACHE.qt_users[0].id,uid(4));checks+=3;
ctx.QG_ADMIN_DIRECTORY_QUERY='วิชัย';ctx.QG_ADMIN_DIRECTORY_CURSOR=uid(2);calls=[];await ctx.qtHydrateAdminDirectory();assert(calls[0].path.includes('&id=gt.'+uid(2)));assert(calls[1].path.includes('&user_id=gt.'+uid(2)));checks+=2;
gate=new Promise(r=>release=r);ctx.QG_ADMIN_DIRECTORY_CURSOR='';ctx.QG_ADMIN_DIRECTORY_QUERY='old';const old=ctx.qtHydrateAdminDirectory();await new Promise(r=>setImmediate(r));ctx.QG_ADMIN_DIRECTORY_QUERY='new';const newer=ctx.qtHydrateAdminDirectory();await newer;assert.equal(ctx.QT_DB_CACHE.qt_users[0].id,uid(3));release();await old;assert.equal(ctx.QT_DB_CACHE.qt_users[0].id,uid(3));checks+=2;
ctx.QG_ADMIN_DIRECTORY_QUERY='';ctx.QG_ADMIN_DIRECTORY_CURSOR=uid(9);calls=[];overviewMissing=true;
await assert.rejects(()=>ctx.qtHydrateAdminDirectory(),/PGRST202/);
const overviewCalls=calls.filter(x=>x.path.startsWith('rpc/queuego_admin_shop_overview'));
assert.equal(overviewCalls.length,1);assert(Array.isArray(overviewCalls[0].options?.body?.p_user_ids));assert(!calls.some(x=>x.path==='rpc/queuego_admin_shop_overview'&&Object.keys(x.options?.body||{}).length===0));checks+=3;
overviewMissing=false;
actor='customer';calls=[];await assert.rejects(()=>ctx.qtHydrateAdminDirectory(),/Admin/);assert.equal(calls.length,0);checks+=2;
assert(html.includes('qgAdminDirectorySearch(this.elements.${name}.value)'));checks++;assert(html.includes('qg-admin-inline-search'));checks++;assert(!html.includes('name="directorySearch"'));checks++;
assert(!html.includes("qtSupabaseTable('rpc/queuego_admin_shop_overview',{method:'POST',accessToken:token,body:{}})"));checks++;
const searchIndexSql=fs.readFileSync('supabase/migrations/20261020022140_admin_directory_search_trgm_indexes.sql','utf8');
for(const index of ['users_name_trgm_idx','users_phone_trgm_idx','shop_profiles_current_name_trgm_idx','shop_profiles_current_phone_trgm_idx']){assert(searchIndexSql.includes(index));checks++}
assert((searchIndexSql.match(/gin_trgm_ops/g)||[]).length===4);checks++;
console.log(JSON.stringify({checks,failures:0,scope:'actual directory function: Unicode Thai normalization, bounded server account/shop search, scoped profiles, coalescing, keyset cursor, stale-query fencing and non-admin denial'}));})().catch(e=>{console.error(e);process.exit(1)});
