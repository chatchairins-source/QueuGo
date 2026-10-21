const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const root=path.resolve(__dirname,'..');
const admin=fs.readFileSync(path.join(root,'admin/index.html'),'utf8');
const adminCss=fs.readFileSync(path.join(root,'admin/interface.css'),'utf8');
const merchant=fs.readFileSync(path.join(root,'merchant/index.html'),'utf8');
const customer=fs.readFileSync(path.join(root,'index.html'),'utf8');
const customerFeatures=fs.readFileSync(path.join(root,'customer-features.js'),'utf8');
const customerCss=fs.readFileSync(path.join(root,'customer-features.css'),'utf8');
const rider=fs.readFileSync(path.join(root,'rider/index.html'),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};
for(const [name,source] of [['Admin',admin],['Merchant',merchant],['Customer',customerFeatures],['Rider',rider]]){
  ok(!/<button[^>]*>[^<]*(?:✕|＋|↻|←|↪|•••|⋯)<\/button>/.test(source),name+' must not use text glyphs as button icons');
}
ok(!merchant.includes('>×</button>'),'Merchant remove controls must use SVG close icons');
ok(!rider.includes("done?'✓ รับสินค้าแล้ว'"),'Rider completed pickup control must use a real check icon');
ok(admin.includes("close:'M6 6l12 12M18 6 6 18'")&&admin.includes("logout:'M10 17l5-5-5-5"),'Admin must expose shared close/logout icons');
ok(adminCss.includes('.qg-menu-head button svg{width:18px;height:18px}')&&adminCss.includes('.qg-close svg{width:18px;height:18px}'),'Admin icon-only controls must have bounded SVG sizing');
ok(merchant.includes('window.qgmUiIcon=svg'),'Merchant feature overlays must reuse one shared icon helper');
ok(merchant.includes("window.qgmUiIcon('close')")&&merchant.includes("window.qgmUiIcon('refresh')"),'Merchant legacy overlays must use shared SVG controls');
ok(merchant.includes('.qg-v22-dialog form button{border:0;border-radius:12px;background:var(--qg-red)'),'Merchant old dialogs must use QueueGo red for primary actions');
ok(customer.includes("close:'<path d=\"M6 6l12 12M18 6 6 18\"")&&customer.includes("more:'<circle cx=\"5\" cy=\"12\""),'Customer must expose close/more icons');
ok(customerFeatures.includes("${ico('close')}")&&customerFeatures.includes("${ico('more')}"),'Customer fullscreen chat must use SVG close/more controls');
ok(customerCss.includes('.qg-chat-report-close svg{width:19px;height:19px'),'Customer report dialog close icon must be sized consistently');
ok(rider.includes("'close':'<svg class=\"ico\"")&&rider.includes("'more':'<svg class=\"ico\"")&&rider.includes("'check':'<svg class=\"ico\""),'Rider must expose close/more/check icons');
ok(rider.includes("${QTICON.more}</button>")&&rider.includes("QTICON.check+'<span>รับสินค้าแล้ว</span>'"),'Rider chat and multi-pickup controls must use shared icons');
console.log(JSON.stringify({checks,failures:0,scope:'cross-role real SVG controls and QueueGo modal styling'}));
