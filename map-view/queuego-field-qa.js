/* QueueGo Map field-evidence recorder.
 * Enabled only with ?field-qa=1. Raw GPS never uploads automatically.
 */
(function(root){
  "use strict";
  if(new URLSearchParams(location.search).get("field-qa")!=="1")return;

  const MAX_FIXES=2000;
  const SCENARIOS=[
    ["village_lane","ซอย/ถนนในหมู่บ้าน"],
    ["frontage_road","ถนนคู่ขนาน/ถนนหน้า"],
    ["u_turn","จุดกลับรถ"],
    ["parallel_road","ถนนคู่ขนาน GPS ใกล้กัน"],
    ["signal_loss","สัญญาณ GPS หาย"],
    ["background_foreground","สลับพื้นหลัง → กลับเข้าแอป"],
    ["generic","ทดสอบทั่วไป"]
  ];
  const state={watchId:null,recording:false,session:null,lastMatchAt:0,matching:false,matchContext:{},lastFixAt:null};

  function nowIso(){return new Date().toISOString()}
  function finite(value){return typeof value==="number"&&Number.isFinite(value)}
  function cleanFix(position){
    const c=position.coords||{};
    return {
      at:new Date(position.timestamp||Date.now()).toISOString(),
      lat:Number(c.latitude),
      lon:Number(c.longitude),
      accuracy:finite(c.accuracy)?Number(c.accuracy):null,
      altitude:finite(c.altitude)?Number(c.altitude):null,
      altitude_accuracy:finite(c.altitudeAccuracy)?Number(c.altitudeAccuracy):null,
      heading:finite(c.heading)?Number(c.heading):null,
      speed:finite(c.speed)?Number(c.speed):null,
      accepted:finite(c.accuracy)&&c.accuracy<=50
    };
  }
  function sessionId(){
    const t=new Date().toISOString().replace(/[-:.TZ]/g,"").slice(0,14);
    return "qg-field-"+t+"-"+Math.random().toString(36).slice(2,7);
  }
  function style(){
    const tag=document.createElement("style");
    tag.textContent=`
      .qg-field-qa{position:fixed;z-index:60;left:8px;right:8px;bottom:calc(8px + env(safe-area-inset-bottom,0px));background:rgba(255,255,255,.98);border:1px solid #e9e5e7;border-radius:18px;box-shadow:0 14px 36px rgba(0,0,0,.2);padding:10px 11px;font-family:-apple-system,BlinkMacSystemFont,"Noto Sans Thai","Segoe UI",sans-serif;color:#171519}
      .qg-field-head{display:flex;align-items:center;justify-content:space-between;gap:8px}.qg-field-head b{font-size:13px}.qg-field-head small{display:block;color:#777;font-size:8px;margin-top:2px}
      .qg-field-qa select{width:100%;margin-top:8px;border:1px solid #e2dfe1;border-radius:11px;background:#faf9f9;padding:9px;font-size:16px}
      .qg-field-actions{display:grid;grid-template-columns:1fr 1fr 1fr;gap:6px;margin-top:8px}.qg-field-actions button{border:0;border-radius:11px;padding:9px 7px;font-size:10px;font-weight:800}.qg-field-start{background:#e6002d;color:#fff}.qg-field-stop{background:#191619;color:#fff}.qg-field-export{background:#efedef;color:#2e292e}
      .qg-field-stats{margin-top:7px;font-size:9px;line-height:1.45;color:#5c565c}.qg-field-note{margin-top:6px;font-size:8px;line-height:1.4;color:#898389}
      .qg-field-close{border:0;background:#f1eef0;border-radius:999px;width:30px;height:30px;font-size:16px}
    `;
    document.head.appendChild(tag);
  }
  function panel(){
    const host=document.createElement("section");
    host.className="qg-field-qa";
    host.innerHTML=`
      <div class="qg-field-head">
        <div><b>QueueGo Map · Field QA</b><small>บันทึกหลักฐาน GPS ในเครื่องนี้เท่านั้น</small></div>
        <button class="qg-field-close" type="button" aria-label="ปิด">×</button>
      </div>
      <select id="qgFieldScenario" aria-label="ประเภทการทดสอบ"></select>
      <div class="qg-field-actions">
        <button id="qgFieldStart" class="qg-field-start" type="button">เริ่มบันทึก</button>
        <button id="qgFieldStop" class="qg-field-stop" type="button" disabled>หยุด</button>
        <button id="qgFieldExport" class="qg-field-export" type="button" disabled>ส่งออก</button>
      </div>
      <div id="qgFieldStats" class="qg-field-stats">ยังไม่ได้บันทึก</div>
      <div class="qg-field-note">ไม่มีการอัปโหลดอัตโนมัติ · อย่าบันทึกขณะขับรถด้วยการจับโทรศัพท์ ให้ผู้โดยสาร/ผู้ทดสอบเป็นคนควบคุม หรือเริ่มก่อนออกเดินทาง</div>
    `;
    document.body.appendChild(host);
    const select=host.querySelector("#qgFieldScenario");
    SCENARIOS.forEach(([value,label])=>{
      const option=document.createElement("option");option.value=value;option.textContent=label;select.appendChild(option);
    });
    host.querySelector(".qg-field-close").onclick=()=>{if(!state.recording)host.hidden=true};
    host.querySelector("#qgFieldStart").onclick=start;
    host.querySelector("#qgFieldStop").onclick=stop;
    host.querySelector("#qgFieldExport").onclick=exportEvidence;
    return host;
  }

  function currentStats(){
    const fixes=state.session&&state.session.fixes||[];
    const accepted=fixes.filter(f=>f.accepted);
    const accuracies=accepted.map(f=>f.accuracy).filter(finite).sort((a,b)=>a-b);
    const median=accuracies.length?accuracies[Math.floor(accuracies.length/2)]:null;
    return {fixes:fixes.length,accepted:accepted.length,median_accuracy_m:median,events:(state.session&&state.session.events||[]).length};
  }
  function render(){
    const host=document.querySelector(".qg-field-qa");if(!host)return;
    const stats=currentStats(),s=state.session;
    host.querySelector("#qgFieldStats").textContent=s
      ?(state.recording?"กำลังบันทึก · ":"หยุดแล้ว · ")+stats.fixes+" fixes · ผ่าน accuracy "+stats.accepted+(stats.median_accuracy_m!=null?" · median ±"+Math.round(stats.median_accuracy_m)+" ม.":"")+" · events "+stats.events
      :"ยังไม่ได้บันทึก";
    host.querySelector("#qgFieldStart").disabled=state.recording;
    host.querySelector("#qgFieldStop").disabled=!state.recording;
    host.querySelector("#qgFieldExport").disabled=!s||!s.fixes.length;
  }
  async function packageMetadata(){
    try{return await root.QueueGoMapService.call("health",{}, {timeout:3000})}catch(_){return null}
  }
  async function start(){
    if(state.recording)return;
    if(!navigator.geolocation){alert("อุปกรณ์นี้ไม่รองรับ GPS");return}
    const scenario=document.getElementById("qgFieldScenario").value;
    const mapState=root.QueueGoMapView&&root.QueueGoMapView.state;
    const activeRoute=mapState&&mapState.routes&&mapState.routes[mapState.selectedRoute];
    state.session={
      schema:"queuego-map-field-evidence/v1",
      session_id:sessionId(),
      scenario,
      started_at:nowIso(),
      stopped_at:null,
      package:await packageMetadata(),
      navigation:{
        active:Boolean(mapState&&mapState.navigationActive),
        route_id:activeRoute&&activeRoute.route_id||null,
        route_profile:activeRoute&&activeRoute.profile||null,
        route_distance_m:activeRoute&&Number(activeRoute.distance)||null
      },
      fixes:[],
      events:[{type:"recording_started",at:nowIso(),visibility:document.visibilityState}],
      summary:null
    };
    state.recording=true;state.lastMatchAt=0;state.matchContext={};state.lastFixAt=null;
    state.watchId=navigator.geolocation.watchPosition(onFix,onError,{enableHighAccuracy:true,maximumAge:1000,timeout:12000});
    render();
  }
  function stop(){
    if(state.watchId!=null&&navigator.geolocation)navigator.geolocation.clearWatch(state.watchId);
    state.watchId=null;state.recording=false;
    if(state.session){
      state.session.stopped_at=nowIso();
      state.session.events.push({type:"recording_stopped",at:state.session.stopped_at,visibility:document.visibilityState});
      state.session.summary=currentStats();
    }
    render();
  }
  function onError(error){
    if(!state.session)return;
    state.session.events.push({type:"geolocation_error",at:nowIso(),code:error&&error.code||null,message:error&&error.message||""});
    render();
  }
  async function onFix(position){
    if(!state.recording||!state.session)return;
    const fix=cleanFix(position);
    if(!finite(fix.lat)||!finite(fix.lon))return;
    const fixAt=Date.parse(fix.at);
    if(state.lastFixAt!=null&&Number.isFinite(fixAt)&&fixAt-state.lastFixAt>15000){
      state.session.events.push({type:"gps_gap",at:fix.at,gap_ms:fixAt-state.lastFixAt});
      state.matchContext={};
    }
    if(Number.isFinite(fixAt))state.lastFixAt=fixAt;
    if(state.session.fixes.length>=MAX_FIXES){stop();return}
    state.session.fixes.push(fix);
    render();
    const now=Date.now();
    if(!root.QueueGoMapService||state.matching||now-state.lastMatchAt<3000)return;
    state.lastMatchAt=now;state.matching=true;
    const raw=state.session.fixes.slice(-12).filter(f=>f.accepted).map(f=>({
      lat:f.lat,lon:f.lon,accuracy:f.accuracy,timestamp:Date.parse(f.at)/1000,
      speed:f.speed,heading:f.heading
    }));
    if(!raw.length){state.matching=false;return}
    try{
      const nav=state.session.navigation||{};
      const request={points:raw,profile:nav.route_profile||"motorcycle"};
      if(nav.route_id){request.route_id=nav.route_id;request.context=state.matchContext}
      const match=await root.QueueGoMapService.call("match",request,{timeout:5000});
      if(match.context)state.matchContext=match.context;
      fix.matching_method=match.matching_method||null;
      fix.snapped_position=Array.isArray(match.snapped_position)?match.snapped_position:null;
      fix.matching_available=match.matching_available!==false;
      fix.route_id=nav.route_id||null;
      fix.off_route_distance_m=finite(match.off_route_distance_m)?match.off_route_distance_m:null;
      fix.remaining_distance_m=finite(match.remaining_distance_m)?match.remaining_distance_m:null;
      fix.remaining_duration_s=finite(match.remaining_duration_s)?match.remaining_duration_s:null;
      fix.distance_to_maneuver_m=finite(match.distance_to_maneuver_m)?match.distance_to_maneuver_m:null;
      fix.reroute=Boolean(match.reroute);
      fix.arrived=Boolean(match.arrived);
      const matching=Array.isArray(match.matchings)&&match.matchings[0];
      fix.matched_way_ids=matching&&Array.isArray(matching.osm_way_ids)?matching.osm_way_ids.slice(0,80):[];
    }catch(_){
      fix.matching_method=null;fix.snapped_position=null;fix.matching_available=false;
    }finally{state.matching=false}
  }
  function visibility(){
    if(!state.recording||!state.session)return;
    state.session.events.push({type:"visibility",at:nowIso(),state:document.visibilityState});
    render();
  }
  async function exportEvidence(){
    if(!state.session||!state.session.fixes.length)return;
    if(state.recording)stop();
    state.session.summary={...currentStats(),
      reroute_count:state.session.fixes.filter(f=>f.reroute).length,
      arrived_count:state.session.fixes.filter(f=>f.arrived).length,
      matched_fix_count:state.session.fixes.filter(f=>f.matching_available&&Array.isArray(f.snapped_position)).length,
      gps_gap_count:state.session.events.filter(e=>e.type==="gps_gap").length
    };
    const json=JSON.stringify(state.session,null,2);
    const file=new File([json],state.session.session_id+".json",{type:"application/json"});
    if(navigator.share&&navigator.canShare&&navigator.canShare({files:[file]})){
      try{await navigator.share({title:"QueueGo Map Field QA",files:[file]});return}catch(error){if(error&&error.name==="AbortError")return}
    }
    try{
      await navigator.clipboard.writeText(json);
      alert("คัดลอกหลักฐาน Field QA เป็น JSON แล้ว");
      return;
    }catch(_){}
    const url=URL.createObjectURL(file),a=document.createElement("a");
    a.href=url;a.download=file.name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
  }

  style();panel();render();
  document.addEventListener("visibilitychange",visibility);
  root.QueueGoFieldQA=Object.freeze({
    start,stop,exportEvidence,
    get recording(){return state.recording},
    get evidence(){return state.session?JSON.parse(JSON.stringify(state.session)):null}
  });
})(globalThis);
