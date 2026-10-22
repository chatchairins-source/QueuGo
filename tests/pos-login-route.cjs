const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict'),{JSDOM}=require('jsdom');
const root=path.resolve(__dirname,'..');
(async()=>{
  const dom=new JSDOM('<div id="app"><form><input id="qt-shop-login" value="owner@example.test"><input id="qt-shop-password" value="not-a-real-password"><button type="submit">เข้าสู่ระบบ</button></form></div>',{url:'https://queuego.test/merchant/#pos',runScripts:'outside-only'}),w=dom.window,navigated=[];
  w.toast=()=>{};w.QT_DB_CACHE={};w.QT_SYNC={};w.qgmLoginPending=false;w.qtSessionRead=()=>({});w.qtAuthEmailFromPhone=x=>x;w.qtSupabaseAuth=async()=>({user:{id:'owner-id'},access_token:'test-token',refresh_token:'refresh',expires_at:1900000000});
  w.qtLoadOwnMerchantIdentity=async()=>({profile:{role:'shop',status:'active',auth_user_id:'owner-id'},user:{id:'owner-id',type:'shop',shopProfileId:'shop-id'},shop:{id:'shop-id'}});
  w.qtSupabaseTable=async()=>[];w.setSession=()=>{};w.qtMarkClean=()=>{};w.qtJwtExpiryMs=()=>1900000000000;w.qtBackgroundHydrate=async()=>{};w.navigate=page=>{navigated.push(page);w.location.hash='#'+page};w.message=()=>{};
  vm.runInContext(fs.readFileSync(path.join(root,'merchant/pos-table-v2.js'),'utf8'),dom.getInternalVMContext());
  const form=w.document.querySelector('form'),button=form.querySelector('button');
  await w.qtShopDoLogin({preventDefault(){},currentTarget:form,submitter:button});
  assert.deepEqual(navigated,['pos']);assert.equal(w.location.hash,'#pos');
  w.location.hash='#dashboard';navigated.length=0;
  await w.qtShopDoLogin({preventDefault(){},currentTarget:form,submitter:button});
  assert.deepEqual(navigated,['dashboard']);assert.equal(w.location.hash,'#dashboard');
  dom.window.close();console.log(JSON.stringify({checks:4,failures:0,scope:'owner login preserves explicit POS destination while ordinary shop login still opens Dashboard'}));
})().catch(e=>{console.error(e);process.exit(1)});
