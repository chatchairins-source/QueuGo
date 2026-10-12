const assert=require('assert');
const fs=require('fs');
const {pathToFileURL}=require('url');
const path=require('path');
(async()=>{
  const policy=await import(pathToFileURL(path.resolve('supabase/functions/queuego-push/rider-offer-message.mjs')));
  // Pure transport fixtures: no account/order records, writes or FCM calls.
  const metadata={id:'transport-event',user_id:'transport-recipient',reference_id:'transport-reference',type:'order',title:'มีงานใหม่',message:'offer'};
  assert(policy.isRiderOfferNotification({role:'rider'},metadata));
  for(const role of ['customer','shop'])assert(!policy.isRiderOfferNotification({role},metadata));
  assert(!policy.isRiderOfferNotification({role:'rider'},{...metadata,title:'จัดส่งสำเร็จ'}));
  const now=Date.parse('2026-10-12T06:00:00Z');
  const message=policy.buildRiderOfferMessage('transport-token',metadata,'2026-10-12T06:00:30Z','transport-session',now);
  assert(!('notification' in message),'Rider offers must reach the app in the background for expiry/role checks');
  assert.deepEqual(message.android,{priority:'high',collapse_key:'queuego_rider_offer',ttl:'30s'});
  assert.equal(message.data.offerExpiresAt,'2026-10-12T06:00:30.000Z');
  assert.equal(message.data.referenceId,metadata.reference_id);
  assert.equal(message.data.userId,metadata.user_id);
  assert.equal(message.data.sessionId,'transport-session');
  assert.equal(policy.buildRiderOfferMessage('token',metadata,'2026-10-12T06:00:00Z','session',now),null);
  assert.equal(policy.buildRiderOfferMessage('token',metadata,'invalid','session',now),null);
  for(const type of ['order','voice_call','push_test']){
    const event=policy.buildRiderEventMessage('transport-token',{...metadata,type,title:'event',message:'body'},'transport-session');
    assert(!('notification' in event),'All Rider background events must pass cached account/session checks');
    assert.equal(event.data.type,type);assert.equal(event.data.title,'event');assert.equal(event.data.message,'body');
    assert.equal(event.data.userId,metadata.user_id);assert.equal(event.data.sessionId,'transport-session');
    assert.deepEqual(event.android,{priority:'high'});
  }
  const edge=fs.readFileSync('supabase/functions/queuego-push/index.ts','utf8');
  assert(edge.includes("offer.data.rider_id!==profile.data.id"));
  assert(edge.includes('created<offered'));
  assert(edge.includes('message:riderMessage||{'),'Existing other-role notification payload must remain');
  assert(edge.includes("user.role==='rider'?buildRiderEventMessage"),'Other-role payload remains on the existing transport path');
  console.log('Rider offer push expiry, ownership, session and transport policy: PASS');
})().catch(e=>{console.error(e);process.exit(1)});
