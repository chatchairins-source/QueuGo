const fs=require('fs'),path=require('path'),assert=require('assert');

let checks=0;
const ok=(value,message)=>{assert.ok(value,message);checks++};
const html=fs.readFileSync(path.resolve(__dirname,'../admin/index.html'),'utf8');
const realtime=fs.readFileSync(path.resolve(__dirname,'../role-realtime.js'),'utf8');

const controlStart=html.indexOf('async function qtHydrateAdminControlData()');
const toastStart=html.indexOf('\n\n\nfunction toast(msg)',controlStart);
ok(controlStart>=0&&toastStart>controlStart,'Admin control hydrator must exist');
const control=html.slice(controlStart,toastStart);
ok(control.includes("users?select=id,auth_user_id,role,name,phone,status,metadata,created_at,updated_at"),'control load uses a bounded users projection');
ok(control.includes("shop_profiles?select=id,user_id,shop_name,phone,address,latitude,longitude,status,onboarding_status"),'control load uses a bounded shop projection');
ok(!control.includes('orders?select=*'),'control load must not fetch all orders');
ok(!control.includes('order_items?select=*'),'control load must not fetch all order items');
ok(!control.includes('products?select=*'),'control load must not fetch the full product inventory');
ok(!control.includes('order_chat_messages?select=*'),'control load must not fetch all chat messages');
ok(control.includes('Promise.all(['),'independent Admin control reads run in parallel');

const restoreStart=html.indexOf('<script id="qg-admin-control-restore">');
const restoreEnd=html.indexOf('</script>',restoreStart);
const restore=html.slice(restoreStart,restoreEnd);
ok(restore.includes('controlRefresh=qtHydrateAdminControlData().then(() =>')||restore.includes('controlRefresh=qtHydrateAdminControlData().then(()=>'),'Approval refresh must use the control hydrator');
ok(!restore.includes('controlRefresh=qtHydrateDatabase({light:false})'),'Approval refresh must not use the full hydrator');
ok(html.includes("if(target==='governance') await qtHydrateAdminControlData();"),'manual governance refresh uses the control hydrator');
ok(html.includes("adminControl&&window.QT_REALTIME_STATUS==='SUBSCRIBED'&&!force"),'legacy polling pauses while Admin realtime is subscribed');

ok(realtime.includes('function adminControlRelevant(reason)'),'Admin realtime filters unrelated table events');
ok(realtime.includes("'qt-sec-approval':['users','shop_profiles','technician_profiles','rider_profiles']"),'Approval realtime watches only provider identity/profile tables');
ok(realtime.includes("const controlFast=admin&&location.hash==='#admin-governance'&&typeof qtHydrateAdminControlData==='function'"),'Admin governance realtime uses the control hydrator');

console.log(JSON.stringify({checks,failures:0,scope:'Admin approval/control fast-path avoids full operational-table hydration and unrelated realtime refreshes'}));
