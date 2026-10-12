(function(global){
'use strict';

const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const CALL_TOPIC=/^qg-call:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const ACTIVE_CALL_STATUSES=new Set(['ringing','accepted']);
const SIGNAL_EVENTS=new Set(['ready','offer','answer','ice','hangup']);
const DEVICE_KEY='qg_voice_web_device_id_v1';

function svg(name){
  const common='viewBox="0 0 24 24" aria-hidden="true"';
  if(name==='phone')return `<svg ${common}><path d="M7.2 3.5 10 7.7 8.4 9.3c1.3 2.7 3.4 4.8 6.1 6.1l1.6-1.6 4.2 2.8-.8 3.1c-.2.8-.9 1.3-1.7 1.3C10 20.6 3.4 14 3 6.2c0-.8.5-1.5 1.3-1.7Z"/></svg>`;
  if(name==='mute')return `<svg ${common}><path d="M12 5a3 3 0 0 0-3 3v3a3 3 0 0 0 6 0V8a3 3 0 0 0-3-3Z"/><path d="M5 11a7 7 0 0 0 12.1 4.8M12 18v3M8 21h8M4 4l16 16"/></svg>`;
  if(name==='mic')return `<svg ${common}><path d="M12 5a3 3 0 0 0-3 3v3a3 3 0 0 0 6 0V8a3 3 0 0 0-3-3Z"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3M8 21h8"/></svg>`;
  if(name==='end')return `<svg ${common}><path d="M5 15.5c4.6-3.3 9.4-3.3 14 0"/><path d="M7 14l-1.5 4M17 14l1.5 4"/></svg>`;
  return '';
}

function safeText(value){return String(value==null?'':value);}
function uuid(){return global.crypto?.randomUUID?.()||'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g,c=>{const r=Math.random()*16|0,v=c==='x'?r:(r&3|8);return v.toString(16)});}
function sleep(ms){return new Promise(resolve=>setTimeout(resolve,ms));}
function deadline(promise,ms,label){
  let timer;
  return Promise.race([
    promise,
    new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error(label||'หมดเวลาการเชื่อมต่อ')),ms)})
  ]).finally(()=>clearTimeout(timer));
}

class QueueGoVoiceWebController{
  constructor(config){
    this.config=config||{};
    if(!this.config.url||!this.config.key)throw new Error('QueueGo Voice config ไม่ครบ');
    if(typeof this.config.getToken!=='function'||typeof this.config.getSession!=='function'||typeof this.config.setSession!=='function')throw new Error('QueueGo Voice session adapter ไม่ครบ');
    this.phase='idle';this.call=null;this.caller=false;this.muted=false;this.media=null;this.peer=null;this.remoteAudio=null;this.socket=null;this.socketConnected=false;this.socketRef=2;this.joinRef='1';this.heartbeatTimer=null;this.pendingIce=[];this.remoteDescriptionReady=false;this.offerSent=false;this.watchTimer=null;this.authTimer=null;this.operation=0;this.overlay=null;this.elapsedTimer=null;this.connectedAt=0;this.incomingBusy=false;this.destroyed=false;this.sessionValidation={id:'',at:0};
    this._onVisibility=()=>{if(!document.hidden)this.refreshIncoming().catch(()=>{})};
  }

  notify(message){try{if(typeof this.config.toast==='function')this.config.toast(message);else console.warn(message)}catch(_){console.warn(message)}}
  currentUserId(){return safeText(this.config.getUserId?.()||this.config.getSession()?.userId||'');}
  getSession(){return this.config.getSession()||null;}
  setSession(next){this.config.setSession(next);}
  headers(token){const h={apikey:this.config.key,'Content-Type':'application/json','Accept':'application/json'};if(token)h.Authorization='Bearer '+token;return h;}

  async rpc(name,body,token){
    token=token||await this.config.getToken();if(!token)throw new Error('กรุณาเข้าสู่ระบบใหม่');
    const r=await fetch(this.config.url.replace(/\/$/,'')+'/rest/v1/rpc/'+name,{method:'POST',headers:this.headers(token),body:JSON.stringify(body||{}),signal:AbortSignal.timeout(15000)});
    const data=await r.json().catch(()=>null);
    if(!r.ok){const e=new Error((data&&(data.message||data.hint||data.error))||'VOICE_RPC_'+r.status);e.status=r.status;throw e;}
    return data;
  }

  deviceId(){try{let id=localStorage.getItem(DEVICE_KEY);if(!id){id=uuid();localStorage.setItem(DEVICE_KEY,id)}return id}catch(_){return uuid()}}

  async ensureSession(){
    let session=this.getSession();if(!session)throw new Error('กรุณาเข้าสู่ระบบใหม่');
    const token=await this.config.getToken();if(!token)throw new Error('กรุณาเข้าสู่ระบบใหม่');
    if(UUID.test(String(session.sessionId||''))){
      const id=String(session.sessionId),fresh=this.sessionValidation.id===id&&Date.now()-this.sessionValidation.at<15000;
      if(!fresh){
        const ok=await this.rpc('check_active_session',{p_session_id:id},token);
        const valid=ok===true||(Array.isArray(ok)&&ok[0]===true)||(ok&&ok.check_active_session===true);
        if(!valid){
          const error=new Error('บัญชีนี้ถูกใช้งานบนอุปกรณ์อื่น กรุณาเข้าสู่ระบบใหม่');
          error.status=403;throw error;
        }
        this.sessionValidation={id,at:Date.now()};
        this.rpc('touch_active_session',{p_session_id:id},token).catch(()=>{});
      }
      return {...session,accessToken:token};
    }
    const sessionId=uuid();
    await this.rpc('claim_active_session',{p_session_id:sessionId,p_device_id:this.deviceId()},token);
    session={...session,sessionId,accessToken:token};this.sessionValidation={id:sessionId,at:Date.now()};this.setSession(session);return session;
  }

  async requestMic(){
    if(this.media?.active)return this.media;
    if(!navigator.mediaDevices?.getUserMedia)throw new Error('เบราว์เซอร์นี้ไม่รองรับการโทรผ่าน QueueGo');
    try{this.media=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true,autoGainControl:true},video:false});return this.media}
    catch(e){if(e?.name==='NotAllowedError'||e?.name==='SecurityError')throw new Error('กรุณาอนุญาตใช้ไมโครโฟนเพื่อโทรผ่าน QueueGo');throw new Error('เปิดไมโครโฟนไม่สำเร็จ')}
  }

  async start(orderId,target,label){
    if(this.phase!=='idle'&&this.phase!=='ended'&&this.phase!=='error')return;
    if(!UUID.test(String(orderId||'')))throw new Error('ไม่พบออเดอร์สำหรับการโทร');
    if(!['shop','rider','customer'].includes(target))throw new Error('ปลายทางสายโทรไม่ถูกต้อง');
    const op=++this.operation;
    try{
      await this.requestMic();const session=await this.ensureSession();if(op!==this.operation)return;
      const call=await this.rpc('qg_call_start',{p_order_id:orderId,p_target:target,p_session_id:session.sessionId});if(op!==this.operation)return;
      this.call=call;this.caller=true;this.phase='ringing_out';this.showOverlay(label||call.callee_name||this.targetLabel(target));this.renderOverlay();
      const until=Date.now()+50000;let accepted=null;
      while(op===this.operation&&Date.now()<until&&this.phase==='ringing_out'){
        await sleep(900);const latestSession=await this.ensureSession();if(op!==this.operation)return;
        const active=await this.rpc('qg_call_active',{p_order_id:orderId,p_session_id:latestSession.sessionId});
        if(!active||!ACTIVE_CALL_STATUSES.has(active.status))break;
        if(active.id!==call.id)throw new Error('พบสายอื่นในออเดอร์เดียวกัน');
        this.call=active;if(active.status==='accepted'){accepted=active;break}
      }
      if(!accepted){await this.safeEndRpc(call.id);this.finish('ended','ไม่มีผู้รับสาย');return}
      await this.connect(accepted,true,op);
    }catch(e){if(op===this.operation)this.fail(e)}
  }

  targetLabel(target){return target==='customer'?'ลูกค้า':target==='shop'?'ร้านค้า':'Rider';}

  async refreshIncoming(){
    if(this.destroyed||document.hidden||this.incomingBusy)return null;
    if(!['idle','ended','error','incoming'].includes(this.phase))return this.call;
    const session=this.getSession();if(!session?.accessToken)return null;
    this.incomingBusy=true;
    try{
      const ready=await this.ensureSession();
      const incoming=await this.rpc('qg_call_incoming',{p_session_id:ready.sessionId});
      if(!incoming||incoming.none||!incoming.id){if(this.phase==='incoming')this.finish('idle');return null}
      if(incoming.callee_user_id&&this.currentUserId()&&String(incoming.callee_user_id)!==this.currentUserId())return null;
      const changed=this.call?.id!==incoming.id||this.phase!=='incoming';
      this.call=incoming;this.caller=false;this.phase='incoming';this.showOverlay(incoming.caller_name||'QueueGo');this.renderOverlay();
      if(changed){try{navigator.vibrate?.([300,180,300,180,300])}catch(_){}}
      return incoming;
    }catch(e){if(e?.status===401||e?.status===403)this.stopIncomingWatch();return null}
    finally{this.incomingBusy=false}
  }

  async answer(){
    if(this.phase!=='incoming'||!this.call)return;
    const op=++this.operation;
    try{
      await this.requestMic();const session=await this.ensureSession();if(op!==this.operation)return;
      const accepted=await this.rpc('qg_call_answer',{p_call_id:this.call.id,p_session_id:session.sessionId});if(op!==this.operation)return;
      this.call=accepted;this.caller=false;await this.connect(accepted,false,op);
    }catch(e){if(op===this.operation)this.fail(e)}
  }

  async decline(){const call=this.call;++this.operation;try{if(call){const session=await this.ensureSession();await this.rpc('qg_call_decline',{p_call_id:call.id,p_session_id:session.sessionId})}}catch(_){}this.finish('ended');}
  async safeEndRpc(callId){if(!callId)return;try{const session=await this.ensureSession();await this.rpc('qg_call_end',{p_call_id:callId,p_session_id:session.sessionId})}catch(_){}}
  async hangup(){const call=this.call;if(this.socket&&this.socketConnected&&call)this.sendSignal('hangup',{call_id:call.id});++this.operation;if(call)this.safeEndRpc(call.id);this.finish('ended');}

  async iceConfig(callId,session){
    const token=await this.config.getToken();
    const r=await fetch(this.config.url.replace(/\/$/,'')+'/functions/v1/queuego-turn',{method:'POST',headers:this.headers(token),body:JSON.stringify({callId,sessionId:session.sessionId}),signal:AbortSignal.timeout(15000)});
    const data=await r.json().catch(()=>null);
    if(!r.ok||!data?.turnReady||!Array.isArray(data.iceServers))throw new Error('TURN relay ยังไม่พร้อม');
    const iceServers=data.iceServers.map(row=>({urls:Array.isArray(row.urls)?row.urls:row.urls?[row.urls]:[],...(row.username?{username:row.username}:{}),...(row.credential?{credential:row.credential}:{})})).filter(row=>row.urls.some(url=>/^(stun|stuns|turn|turns):/i.test(url)));
    if(!iceServers.some(row=>row.urls.some(url=>/^turns?:/i.test(url))))throw new Error('TURN relay ยังไม่พร้อม');
    return iceServers;
  }

  async connect(call,caller,op){
    if(op!==this.operation)return;const session=await this.ensureSession();if(op!==this.operation)return;
    this.phase='connecting';this.call=call;this.caller=caller;this.renderOverlay();
    const iceServers=await this.iceConfig(call.id,session);if(op!==this.operation)return;
    await this.openRealtime(call.topic,session.accessToken||await this.config.getToken(),op);if(op!==this.operation)return;
    await this.openPeer(iceServers,op);if(op!==this.operation)return;
    this.offerSent=false;this.sendSignal('ready',{call_id:call.id});
    clearInterval(this.authTimer);this.authTimer=setInterval(()=>this.verifyActive().catch(()=>{}),10000);
  }

  realtimeUrl(){const base=this.config.url.replace(/^https:/,'wss:').replace(/^http:/,'ws:').replace(/\/$/,'');return base+'/realtime/v1/websocket?apikey='+encodeURIComponent(this.config.key)+'&vsn=1.0.0';}
  envelope(topic,event,payload,ref,joinRef){const row={topic,event,payload,ref:String(ref)};if(joinRef!=null)row.join_ref=String(joinRef);return JSON.stringify(row);}

  async openRealtime(topic,token,op){
    if(!CALL_TOPIC.test(String(topic||'')))throw new Error('Voice call topic ไม่ถูกต้อง');
    this.closeRealtime();const socket=new WebSocket(this.realtimeUrl());this.socket=socket;this.socketConnected=false;this.socketRef=2;this.joinRef='1';
    await deadline(new Promise((resolve,reject)=>{
      let settled=false;const done=(fn,value)=>{if(settled)return;settled=true;fn(value)};
      socket.onopen=()=>{if(op!==this.operation){socket.close();return}const config={broadcast:{ack:true,self:false},presence:{enabled:false},private:true,postgres_changes:[]};socket.send(this.envelope('realtime:'+topic,'phx_join',{config,access_token:token},'1','1'))};
      socket.onerror=()=>done(reject,new Error('เชื่อมต่อช่องสัญญาณเสียงไม่สำเร็จ'));
      socket.onclose=()=>{this.socketConnected=false;if(!settled)done(reject,new Error('ช่องสัญญาณเสียงถูกปิด'));else if(['connecting','connected'].includes(this.phase)&&op===this.operation)this.fail(new Error('ช่องสัญญาณเสียงถูกปิด'))};
      socket.onmessage=event=>{
        let msg;try{msg=JSON.parse(event.data)}catch(_){return}
        if(msg.topic!=='realtime:'+topic)return;
        if(msg.event==='phx_reply'&&String(msg.ref)==='1'){if(msg.payload?.status==='ok'){this.socketConnected=true;clearInterval(this.heartbeatTimer);this.heartbeatTimer=setInterval(()=>{if(this.socket===socket&&socket.readyState===WebSocket.OPEN){try{socket.send(this.envelope('phoenix','heartbeat',{},String(this.socketRef++),null))}catch(_){}}},20000);done(resolve,true)}else done(reject,new Error('Voice Realtime ถูกปฏิเสธ'));return}
        if(msg.event==='broadcast'){const name=msg.payload?.event,payload=msg.payload?.payload;if(SIGNAL_EVENTS.has(name)&&payload&&op===this.operation)this.handleSignal(name,payload).catch(e=>this.fail(e))}
        else if(msg.event==='phx_error'||(msg.event==='system'&&msg.payload?.status==='error')){if(op===this.operation)this.fail(new Error('Voice Realtime ขัดข้อง'))}
      };
    }),12000,'เชื่อมต่อช่องสัญญาณเสียงหมดเวลา');
  }

  sendSignal(event,payload){if(!SIGNAL_EVENTS.has(event)||!this.socket||!this.socketConnected||!this.call)return false;const ref=String(this.socketRef++),topic='realtime:'+this.call.topic;try{this.socket.send(this.envelope(topic,'broadcast',{type:'broadcast',event,payload},ref,this.joinRef));return true}catch(_){return false}}
  updateRealtimeToken(token){if(!this.socket||!this.socketConnected||!this.call||!token)return;try{this.socket.send(this.envelope('realtime:'+this.call.topic,'access_token',{access_token:token},String(this.socketRef++),this.joinRef))}catch(_){}}

  async openPeer(iceServers,op){
    this.closePeer(false);const stream=await this.requestMic();if(op!==this.operation)return;
    const peer=new RTCPeerConnection({iceServers,bundlePolicy:'max-bundle',rtcpMuxPolicy:'require'});this.peer=peer;this.pendingIce=[];this.remoteDescriptionReady=false;
    for(const track of stream.getAudioTracks())peer.addTrack(track,stream);
    const audio=document.createElement('audio');audio.autoplay=true;audio.playsInline=true;audio.setAttribute('playsinline','');audio.style.display='none';document.body.appendChild(audio);this.remoteAudio=audio;
    peer.ontrack=e=>{const remote=e.streams?.[0]||new MediaStream([e.track]);audio.srcObject=remote;audio.play().catch(()=>{})};
    peer.onicecandidate=e=>{if(e.candidate&&this.call)this.sendSignal('ice',{call_id:this.call.id,sdpMid:e.candidate.sdpMid,sdpMLineIndex:e.candidate.sdpMLineIndex,candidate:e.candidate.candidate})};
    peer.onconnectionstatechange=()=>{if(op!==this.operation)return;if(peer.connectionState==='connected'){this.phase='connected';this.connectedAt=Date.now();this.renderOverlay();this.startElapsed()}else if(peer.connectionState==='failed')this.fail(new Error('การเชื่อมต่อเสียงล้มเหลว'));else if(peer.connectionState==='disconnected'&&this.phase==='connected'){this.phase='connecting';this.renderOverlay()}};
  }

  async handleSignal(event,payload){
    if(!this.call||payload.call_id!==this.call.id)return;if(event==='hangup'){this.finish('ended');return}if(!this.peer)return;
    if(event==='ready'){
      if(this.caller&&!this.offerSent){this.offerSent=true;const offer=await this.peer.createOffer({offerToReceiveAudio:true,offerToReceiveVideo:false});await this.peer.setLocalDescription(offer);if(!this.sendSignal('offer',{call_id:this.call.id,type:'offer',sdp:offer.sdp}))throw new Error('ส่ง Voice offer ไม่สำเร็จ')}
      else if(!this.caller)this.sendSignal('ready',{call_id:this.call.id});
      return;
    }
    if(event==='offer'&&!this.caller){if(payload.type!=='offer'||!payload.sdp)throw new Error('Voice offer ไม่ถูกต้อง');await this.peer.setRemoteDescription({type:'offer',sdp:payload.sdp});this.remoteDescriptionReady=true;await this.flushIce();const answer=await this.peer.createAnswer({offerToReceiveAudio:true,offerToReceiveVideo:false});await this.peer.setLocalDescription(answer);if(!this.sendSignal('answer',{call_id:this.call.id,type:'answer',sdp:answer.sdp}))throw new Error('ส่ง Voice answer ไม่สำเร็จ');return}
    if(event==='answer'&&this.caller){if(payload.type!=='answer'||!payload.sdp)throw new Error('Voice answer ไม่ถูกต้อง');await this.peer.setRemoteDescription({type:'answer',sdp:payload.sdp});this.remoteDescriptionReady=true;await this.flushIce();return}
    if(event==='ice'&&payload.candidate){const candidate={candidate:payload.candidate,sdpMid:payload.sdpMid??null,sdpMLineIndex:Number(payload.sdpMLineIndex)};if(!this.remoteDescriptionReady||!this.peer.remoteDescription){this.pendingIce.push(candidate);return}await this.peer.addIceCandidate(candidate)}
  }

  async flushIce(){if(!this.peer||!this.remoteDescriptionReady)return;const rows=this.pendingIce.splice(0);for(const row of rows)await this.peer.addIceCandidate(row)}

  async verifyActive(){
    if(!this.call||!['connecting','connected'].includes(this.phase))return;
    try{const session=await this.ensureSession();const active=await this.rpc('qg_call_active',{p_order_id:this.call.order_id,p_session_id:session.sessionId});if(!active||active.id!==this.call.id||active.status!=='accepted'){this.sendSignal('hangup',{call_id:this.call.id});this.finish('ended');return}this.call=active;this.updateRealtimeToken(await this.config.getToken())}
    catch(e){if(e?.status>=400&&e?.status<500)this.finish('ended')}
  }

  setMuted(muted){this.muted=!!muted;this.media?.getAudioTracks?.().forEach(track=>{track.enabled=!this.muted});this.renderOverlay()}
  startIncomingWatch(){if(this.watchTimer||this.destroyed)return;document.addEventListener('visibilitychange',this._onVisibility);this.refreshIncoming().catch(()=>{});this.watchTimer=setInterval(()=>this.refreshIncoming().catch(()=>{}),2500)}
  stopIncomingWatch(){if(this.watchTimer)clearInterval(this.watchTimer);this.watchTimer=null;document.removeEventListener('visibilitychange',this._onVisibility)}

  showOverlay(label){
    if(!this.overlay){const el=document.createElement('section');el.className='qg-voice-overlay';el.innerHTML=`<div class="qg-voice-card"><div class="qg-voice-mark">Q</div><div class="qg-voice-copy"><small>QueueGo Call</small><h2 data-qg-voice-name></h2><p data-qg-voice-status></p><b data-qg-voice-time hidden>00:00</b></div><div class="qg-voice-actions" data-qg-voice-actions></div></div>`;document.body.appendChild(el);this.overlay=el}
    this.overlay.dataset.label=safeText(label||'QueueGo');
  }

  renderOverlay(){
    if(!this.overlay)return;
    const name=this.overlay.querySelector('[data-qg-voice-name]'),status=this.overlay.querySelector('[data-qg-voice-status]'),actions=this.overlay.querySelector('[data-qg-voice-actions]'),time=this.overlay.querySelector('[data-qg-voice-time]');
    name.textContent=this.overlay.dataset.label||this.call?.caller_name||this.call?.callee_name||'QueueGo';
    const map={ringing_out:'กำลังโทร…',incoming:'สายเรียกเข้าผ่าน QueueGo',connecting:'กำลังเชื่อมต่อเสียง…',connected:'กำลังสนทนา',ended:'วางสายแล้ว',error:'โทรไม่สำเร็จ'};status.textContent=map[this.phase]||'';time.hidden=this.phase!=='connected';actions.innerHTML='';
    const btn=(cls,label,icon,fn)=>{const b=document.createElement('button');b.type='button';b.className='qg-voice-btn '+cls;b.innerHTML=svg(icon)+`<span>${label}</span>`;b.onclick=fn;actions.appendChild(b)};
    if(this.phase==='incoming'){btn('accept','รับสาย','phone',()=>this.answer());btn('decline','ปฏิเสธ','end',()=>this.decline())}
    else if(this.phase==='ringing_out'||this.phase==='connecting'){btn('decline','วางสาย','end',()=>this.hangup())}
    else if(this.phase==='connected'){btn('neutral',this.muted?'เปิดไมค์':'ปิดไมค์',this.muted?'mic':'mute',()=>this.setMuted(!this.muted));btn('decline','วางสาย','end',()=>this.hangup())}
  }

  startElapsed(){clearInterval(this.elapsedTimer);const tick=()=>{if(!this.overlay||this.phase!=='connected')return;const sec=Math.max(0,Math.floor((Date.now()-this.connectedAt)/1000)),m=String(Math.floor(sec/60)).padStart(2,'0'),s=String(sec%60).padStart(2,'0');const el=this.overlay.querySelector('[data-qg-voice-time]');if(el)el.textContent=m+':'+s};tick();this.elapsedTimer=setInterval(tick,1000)}
  fail(error){console.warn('QueueGo Voice:',error);this.finish('error',error?.message||'โทรผ่าน QueueGo ไม่สำเร็จ')}

  finish(phase='ended',message){
    this.phase=phase;clearInterval(this.authTimer);this.authTimer=null;clearInterval(this.elapsedTimer);this.elapsedTimer=null;this.closeRealtime();this.closePeer(true);this.renderOverlay();if(message)this.notify(message);
    const el=this.overlay;setTimeout(()=>{if(this.overlay===el&&['ended','error','idle'].includes(this.phase)){el?.remove();this.overlay=null;if(this.phase!=='idle')this.phase='idle';this.call=null;this.caller=false;this.muted=false}},phase==='idle'?0:1200);
  }

  closeRealtime(){clearInterval(this.heartbeatTimer);this.heartbeatTimer=null;const socket=this.socket;this.socket=null;this.socketConnected=false;if(socket){try{if(socket.readyState===WebSocket.OPEN&&this.call)socket.send(this.envelope('realtime:'+this.call.topic,'phx_leave',{},'leave',this.joinRef))}catch(_){}try{socket.close(1000,'voice call closed')}catch(_){}}}
  closePeer(stopMedia){try{this.peer?.close()}catch(_){}this.peer=null;this.pendingIce=[];this.remoteDescriptionReady=false;if(this.remoteAudio){try{this.remoteAudio.srcObject=null;this.remoteAudio.remove()}catch(_){}this.remoteAudio=null}if(stopMedia&&this.media){try{this.media.getTracks().forEach(track=>track.stop())}catch(_){}this.media=null}}
  destroy(){this.destroyed=true;this.stopIncomingWatch();++this.operation;this.finish('idle')}
}

function installStyles(){
  if(document.getElementById('qg-voice-web-style'))return;
  const style=document.createElement('style');style.id='qg-voice-web-style';style.textContent=`
.qg-voice-overlay{position:fixed;z-index:50000;inset:0;display:flex;align-items:flex-end;justify-content:center;padding:18px 12px calc(18px + env(safe-area-inset-bottom,0px));background:rgba(18,15,17,.30);backdrop-filter:blur(5px);-webkit-backdrop-filter:blur(5px)}
.qg-voice-card{width:min(100%,430px);padding:20px 18px;border-radius:28px;background:#fff;color:#201c1f;box-shadow:0 20px 60px rgba(0,0,0,.22);text-align:center;font-family:-apple-system,BlinkMacSystemFont,"SF Pro Text","Thonburi",system-ui,sans-serif}
.qg-voice-mark{width:58px;height:58px;margin:0 auto 12px;border-radius:50%;display:grid;place-items:center;background:#e6002d;color:#fff;font-size:28px;font-weight:1000}
.qg-voice-copy small{display:block;color:#8a8387;font-size:11px;font-weight:800}.qg-voice-copy h2{margin:5px 0 3px;font-size:22px;line-height:1.2}.qg-voice-copy p{margin:0;color:#777075;font-size:12px}.qg-voice-copy b{display:block;margin-top:8px;font-size:16px;font-variant-numeric:tabular-nums}
.qg-voice-actions{display:flex;justify-content:center;gap:18px;margin-top:20px}.qg-voice-btn{width:78px;border:0;background:none;color:#50494d;display:grid;place-items:center;gap:7px;font:inherit;font-size:10px;font-weight:850}.qg-voice-btn svg{width:54px;height:54px;padding:15px;border-radius:50%;fill:none;stroke:currentColor;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round}.qg-voice-btn.accept{color:#14945e}.qg-voice-btn.accept svg{background:#e6f8ef}.qg-voice-btn.decline{color:#d7193b}.qg-voice-btn.decline svg{background:#fff0f3}.qg-voice-btn.neutral svg{background:#f2eff0}
.qg-voice-launch{display:inline-flex;align-items:center;justify-content:center;gap:6px}.qg-voice-launch svg{width:17px!important;height:17px!important;fill:none;stroke:currentColor;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round}
`;document.head.appendChild(style);
}

installStyles();
global.QueueGoVoiceWeb={create(config){return new QueueGoVoiceWebController(config)},phoneIcon:()=>svg('phone')};
})(window);
