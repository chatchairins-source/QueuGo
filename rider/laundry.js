(()=>{
'use strict';
// Laundry transport is shop-managed. QueueGo general Riders must not see,
// accept or mutate Laundry jobs. Keep this compatibility shim only so stale
// cached UI entry points fail closed instead of calling retired Rider RPCs.
function removeLegacyLaundryRiderUi(){
  document.getElementById('qg-laundry-rider-entry')?.remove();
  document.getElementById('qg-laundry-rider-panel')?.remove();
}
const observer=new MutationObserver(removeLegacyLaundryRiderUi);
function boot(){
  removeLegacyLaundryRiderUi();
  const app=document.getElementById('app');
  if(app)observer.observe(app,{childList:true,subtree:true});
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',boot);else boot();
window.qgOpenLaundryRider=function(){
  removeLegacyLaundryRiderUi();
  try{toast('งานฝากซักรับ–ส่งโดยพนักงานของร้าน')}catch(_){}
};
})();