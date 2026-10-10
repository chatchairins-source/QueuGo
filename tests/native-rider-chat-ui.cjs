const fs=require('fs'),assert=require('assert');
const read=p=>fs.readFileSync(p,'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const chat=read('native-android/rider/src/main/java/com/queuego/rider/RiderChat.kt');
const quick=read('native-android/rider/src/main/java/com/queuego/rider/RiderQuickMessages.kt');
const photo=read('native-android/rider/src/main/res/drawable/qg_rider_chat_photo.xml');
const send=read('native-android/rider/src/main/res/drawable/qg_rider_chat_send.xml');

ok(quick.includes('"ข้อความด่วน"'),'Rider quick-message shelf must use the compact professional label');
ok(quick.includes('"จัดการ"'),'Rider quick-message shelf must expose a clear manage action');
ok(quick.includes('RoundedCornerShape(18.dp)')&&quick.includes('widthIn(max = 240.dp)'),'Rider quick messages must render as compact pill chips');
ok(quick.includes('TextOverflow.Ellipsis'),'Long quick messages must not break the chat composer layout');
ok(quick.includes('"จัดการข้อความด่วน"'),'Quick-message editor must use the dedicated management title');

ok(chat.includes('fillMaxWidth(0.78f)'),'Rider chat bubbles must remain compact rather than full-width cards');
ok(chat.includes('riderChatTimeLabel(chat.createdAt)'),'Rider chat must show compact local clock labels instead of raw server timestamps');
ok(chat.includes('rememberLazyListState()')&&chat.includes('scrollToItem(messages.lastIndex)'),'Rider chat must move to the newest message when a new message arrives');
ok(chat.includes('LaunchedEffect(messages.size, messages.lastOrNull()?.id)'),'Rider chat auto-scroll must react only to actual tail-message changes');
ok(chat.includes('RoundedCornerShape(topStart = 18.dp'),'Rider chat bubbles must retain directional bubble corners');
ok(chat.includes('R.drawable.qg_rider_chat_photo'),'Rider composer must use the native photo icon');
ok(chat.includes('R.drawable.qg_rider_chat_send'),'Rider composer must use the native send icon');
ok(!chat.includes('Text("➤"'),'Rider composer must not regress to the text-glyph send button');
ok(chat.includes('"รายงาน"')&&!chat.includes('"รายงานข้อความ"'),'Incoming-message report action must stay compact');

ok(photo.includes('<vector')&&photo.includes('android:viewportWidth="24"'),'Rider chat photo icon must remain a vector asset');
ok(send.includes('<vector')&&send.includes('android:viewportWidth="24"'),'Rider chat send icon must remain a vector asset');

console.log(JSON.stringify({checks,failures:0,scope:'Native Rider chat presentation, compact quick-message chips and professional composer controls'}));
