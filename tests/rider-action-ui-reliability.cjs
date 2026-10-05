const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const rider=fs.readFileSync(path.join(root,'rider/index.html'),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

ok(rider.includes("function qgRiderRefreshAfterCommit(label)"),'rider must have non-fatal post-commit refresh helper');
ok(rider.includes("qgRiderRefreshAfterCommit('rider claim refresh failed')"),'normal claim refresh must be isolated after commit');
ok(rider.includes("qgRiderRefreshAfterCommit('rider market claim refresh failed')"),'market claim refresh must be isolated after commit');
ok(rider.includes("qgRiderRefreshAfterCommit('rider arrive-shop refresh failed')"),'arrive-shop refresh must be isolated after commit');
ok(rider.includes("qgRiderRefreshAfterCommit('rider market advance refresh failed')"),'market transition refresh must be isolated after commit');
ok(rider.includes("qgRiderRefreshAfterCommit('rider completion refresh failed')"),'completion refresh must be isolated after commit');
ok(rider.includes("console.warn('rider completion render failed',e)"),'completion render failure must not become a mutation failure');
ok(!rider.includes("toast('รับงานสำเร็จ');await refreshData()"),'successful claim must not be followed by fatal refresh in same try block');
ok(!rider.includes("toast('รับงานตลาดหลายร้านสำเร็จ');await refreshData()"),'successful market claim must not be followed by fatal refresh in same try block');
ok(!rider.includes("toast('แจ้งร้านค้าแล้วว่าไรเดอร์ถึงร้าน');await refreshData()"),'arrive-shop success must not be followed by fatal refresh in same try block');
ok(!rider.includes("toast('ส่งสำเร็จ');document.getElementById('summary-screen')?.remove();const stage=document.querySelector('.stage');if(stage)stage.style.display='block';await refreshData();renderSheet()"),'completion UI refresh must not be allowed to report a false failure');
console.log(JSON.stringify({checks,failures:0,scope:'rider single-tap post-commit UI error isolation'}));
