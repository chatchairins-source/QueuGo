const fs=require('fs'),path=require('path'),assert=require('assert'),{JSDOM}=require('jsdom');
(async()=>{
 let checks=0;
 const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
 const d=new JSDOM('',{url:'https://queuego.test/admin/#admin-market',runScripts:'outside-only'}),w=d.window;
 w.HTMLDialogElement.prototype.showModal=function(){};
 let user={id:'admin',type:'admin',status:'approved'},reads=0,calls=[],fail=true,release,gate=null,map,destroyed=0;
 class StubMap{
  constructor(){this.center={lat:14,lng:103};this.events={}}
  on(name,fn){this.events[name]=fn}
  getCenter(){return this.center}
 }
 w.maplibregl={};
 w.QueueGoMapWeb={
  create(){map=new StubMap();return map},
  marker(_map,p){return {p,remove(){}}},
  setCenter(_map,lat,lng){_map.center={lat,lng}},
  search:async()=>[],
  locate:async(_map,onPoint)=>{onPoint(14,103);return {lat:14,lng:103}},
  destroy(){destroyed++}
 };
 w.currentUser=()=>user;
 w.qtGetAccessToken=async()=> 'isolated';
 w.qgAdminShopCategory=()=> 'ตลาดสด';
 w.toast=()=>{};
 w.qgLoadMarketOperations=async()=>{};
 w.qtSupabaseTable=async()=>{reads++;if(gate)await gate;return [{id:'market',name:'ตลาดทดสอบ',latitude:14,longitude:103}]};
 w.qtSupabaseRpc=async(name,p)=>{calls.push({name,p});if(fail)throw Error('lost reply');return {id:'market',changed:false}};

 const source=fs.readFileSync(path.resolve(__dirname,'../admin/locations.js'),'utf8');
 eq(/google|name="lat"|name="lng"/.test(source),false);
 eq(source.includes('QueueGoMapWeb'),true);
 w.eval(source);

 await w.qgAdminEditLocation('market','market');
 const f=w.document.querySelector('form');
 eq(!!f,true);
 map.center={lat:15,lng:103};
 map.events.click({lngLat:{lat:15,lng:103}});
 f.elements.reason.value='แก้ทางเข้า';
 eq(w.document.querySelector('output').textContent,'15.000000, 103.000000');

 await f.onsubmit({preventDefault(){}});
 eq(calls.length,1);
 eq(calls[0].name,'qg_admin_update_location');
 eq(calls[0].p.p_expected_lat,14);
 eq(calls[0].p.p_lat,15);
 eq(f.elements.reason.disabled,false);
 eq(w.document.querySelector('[role=status]').textContent.includes('lost reply'),true);

 fail=false;
 await f.onsubmit({preventDefault(){}});
 eq(calls.length,2);
 eq(calls[0].p,calls[1].p);
 eq(w.document.querySelector('dialog'),null);
 eq(destroyed,1);

 w.QueueGoMapWeb=undefined;
 await w.qgAdminEditLocation('market','market');
 const disabled=w.document.querySelector('form');
 disabled.elements.reason.value='ทดสอบแผนที่ไม่พร้อม';
 eq(disabled.querySelector('[type=submit]').disabled,true);
 await disabled.onsubmit({preventDefault(){}});
 eq(calls.length,2);

 gate=new Promise(r=>release=r);
 const pending=w.qgAdminEditLocation('shop','shop');
 user={id:'other',type:'customer',status:'approved'};
 release();
 await pending;
 eq(w.document.querySelector('dialog'),null);
 const before=reads;
 await w.qgAdminEditLocation('market','market');
 eq(reads,before);

 d.window.close();
 console.log(JSON.stringify({checks,failures:0,scope:'isolated QueueGo Map selection, unavailable-map lock, retry payload, cleanup, ownership race'}));
})().catch(e=>{console.error(e);process.exit(1)});
