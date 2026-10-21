const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const root=path.resolve(__dirname,'..');
const admin=fs.readFileSync(path.join(root,'admin/index.html'),'utf8');
const adminCss=fs.readFileSync(path.join(root,'admin/interface.css'),'utf8');
const merchant=fs.readFileSync(path.join(root,'merchant/index.html'),'utf8');
const customer=fs.readFileSync(path.join(root,'index.html'),'utf8');
const rider=fs.readFileSync(path.join(root,'rider/index.html'),'utf8');

assert(admin.includes('qg-brand-queue')&&admin.includes('qg-brand-go'));
assert(adminCss.includes('.qg-brand-queue{color:var(--ink)}')&&adminCss.includes('.qg-brand-go{color:var(--red)}'));
assert(!admin.includes('qg-brand-mark'));

assert(merchant.includes('qgm-wordmark-queue')&&merchant.includes('qgm-wordmark-go'));
assert(merchant.includes('.qgm-wordmark-queue{color:#17191d}')&&merchant.includes('.qgm-wordmark-go{color:var(--qg-red)}'));

assert(customer.includes('class="brand-queue">Queue</span><span class="brand-go">Go</span>'));
assert(customer.includes('.brand-queue{color:var(--ink)}.brand-go{color:var(--red)}'));

assert(rider.includes('class="qg-brand-pill" aria-label="QueueGo"'));
assert(rider.includes('.qg-brand-queue{color:#17171b}')&&rider.includes('.qg-brand-go{color:var(--qg-red)}'));
assert(adminCss.startsWith(':root{--red:#e6002d;--red-dark:#b20b29;'));
assert(merchant.includes('--qg-red:#e6002d')&&merchant.includes('--qg-red-dark:#b20b29'));
assert(customer.includes(':root{--red:#e6002d;--rd:#b20b29;'));
assert(!merchant.includes('.qgm-top.red{'));
assert(merchant.includes('.qgm-chiprow{display:flex;gap:7px;flex-wrap:nowrap;overflow-x:auto'));
assert(adminCss.includes('.qt-admin-broadcast-targets{flex-wrap:nowrap;overflow-x:auto'));

for(const source of [admin,merchant,customer,rider]){
  assert(!source.includes('>คิว<span>โก</span><'));
}
console.log(JSON.stringify({checks:19,failures:0,scope:'QueueGo English header wordmark; Queue dark, Go red across web roles'}));