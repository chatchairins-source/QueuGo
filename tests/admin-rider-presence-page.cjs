const fs=require('fs'),assert=require('assert/strict'),{JSDOM}=require('jsdom');
const tick=async()=>{for(let i=0;i<12;i++)await new Promise(r=>setImmediate(r))};
(async()=>{
 const html=fs.readFileSync('admin/index.html','utf8');
 const scripts=[...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)].map(m=>m[1]);
 const code=scripts.find(x=>x.includes('async function qtRenderAdminRiderPresence'));
 assert(code,'presence script missing');
 let actor='admin-auth',calls=[],pageRows=new Map(),profileRows=new Map(),heldResolve=null;
 const d=new JSDOM('<div id="qt-admin-rider-online-panel"></div>',{url:'https://isolated.invalid/admin/#admin-governance',runScripts:'outside-only'}),w=d.window;
 Object.defineProperty(w.document,'hidden',{value:false,configurable:true});
 w.qtAdminCurrentSection=()=> 'qt-sec-delivery';
 w.qtSessionRead=()=>actor?{authUserId:actor,role:'admin'}:null;
 w.qtGetAccessToken=async()=> actor?'token':null;
 w.qtUuidLike=v=>typeof v==='string'&&v.startsWith('u');
 w.escapeHtml=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const users0=Array.from({length:101},(_,i)=>({id:'u'+String(i).padStart(3,'0'),name:'Rider '+i,status:'active'}));
 const users1=[{id:'u100',name:'Rider 100',status:'active'},{id:'u101',name:'Rider 101',status:'suspended'}];
 pageRows.set(0,users0);pageRows.set(100,users1);
 for(const u of [...users0,...users1])profileRows.set(u.id,{id:'p'+u.id,user_id:u.id,status:'active',metadata:{online:Number(u.id.slice(1))%2===0}});
 w.qtSupabaseTable=async path=>{
  calls.push(path);
  assert(!path.includes('qg_rider_presence_events'),'must not replay presence history');
  if(path.startsWith('users?')){
   const m=path.match(/offset=(\d+)/);return pageRows.get(Number(m?.[1]||0))||[];
  }
  if(path.startsWith('rider_profiles?')){
   const ids=(path.match(/user_id=in\.\(([^)]+)\)/)?.[1]||'').split(',').filter(Boolean);
   if(heldResolve){const r=heldResolve;heldResolve=null;await new Promise(resolve=>r(resolve))}
   return ids.map(id=>profileRows.get(id)).filter(Boolean);
  }
  throw Error('unexpected path '+path);
 };
 w.eval(code);
 let checks=0;
 await w.qtRenderAdminRiderPresence();await tick();
 assert.equal(calls.length,2);checks++;
 assert.match(calls[0],/role=eq\.rider/);assert.match(calls[0],/limit=101/);assert.match(calls[0],/offset=0/);checks+=3;
 assert.match(calls[1],/rider_profiles\?select=id,user_id,status,metadata/);assert.match(calls[1],/limit=100/);checks+=2;
 assert.equal((calls[1].match(/u099/g)||[]).length,1);assert(!calls[1].includes('u100'));checks+=2;
 assert.equal(w.document.querySelectorAll('.qt-rider-online-card').length,100);checks++;
 assert.match(w.document.querySelector('.qt-rider-online-card span').textContent,/ออนไลน์/);checks++;
 const next=w.document.querySelector('#qg-rider-presence-next');assert(next&&!next.disabled);next.onclick();await tick();checks++;
 assert(calls.some(x=>x.startsWith('users?')&&x.includes('offset=100')));assert.equal(w.QG_ADMIN_RIDER_PRESENCE_PAGE,1);checks+=2;
 assert.equal(w.document.querySelectorAll('.qt-rider-online-card').length,2);assert(w.document.querySelector('#qg-rider-presence-next').disabled);assert(!w.document.querySelector('#qg-rider-presence-prev').disabled);checks+=3;
 const texts=[...w.document.querySelectorAll('.qt-rider-online-card span')].map(x=>x.textContent);assert(texts.includes('พักสิทธิ์'));checks++;
 // A response from the old actor must not overwrite the current panel.
 w.QG_ADMIN_RIDER_PRESENCE_PAGE=0;calls=[];let release;
 heldResolve=resolve=>{release=resolve};
 const pending=w.qtRenderAdminRiderPresence(0);await tick();actor='other-admin';release();await pending;await tick();
 assert.equal(w.document.getElementById('qt-admin-rider-online-panel').textContent,'กำลังโหลดสถานะไรเดอร์…');checks++;
 assert(!code.includes('limit=2000'));assert(!code.includes('qg_rider_presence_events'));checks+=2;
 d.window.close();
 console.log(JSON.stringify({checks,failures:0,scope:'Admin Rider presence uses bounded 100+1 users and page-scoped profiles; no historical presence replay; actor/page fencing'}));
})().catch(e=>{console.error(e);process.exit(1)});
