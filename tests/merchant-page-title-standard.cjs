const fs=require('fs'),assert=require('assert');
const merchant=fs.readFileSync('merchant/index.html','utf8');
assert(merchant.includes('id="qg-merchant-page-title-standard-v1"'),'Merchant page-title standard style must exist');
assert(merchant.includes('--qgm-page-title-size:19px'),'Merchant page titles must use the 19px standard');
[
  '.qgm-title,',
  '.qgm-account-copy h1,',
  '.qgm-profile-name h2,',
  '.qgm-unified-intro>b,',
  '.qgm-page-title-row h2,',
  '.qgm-revenue-title h1,',
  '.qgm-media-head h2,',
  '.qgm-hours-heading,',
  '.qt-shop-page-title h2'
].forEach(selector=>assert(merchant.includes(selector),selector+' must use the shared Merchant page-title scale'));
assert(merchant.includes('body.qgm-revenue-page .qgm-revenue-title h1{font-size:var(--qgm-page-title-size)!important}'),'Revenue mobile title must not stay oversized');
assert(!merchant.includes('qgm-header-brand-right'),'Merchant secondary headers must show only the page title, without duplicating the QueueGo wordmark');
assert(!merchant.includes('<header class="qt-notif-header">'),'Merchant Notifications must not render a duplicate inner header');

console.log('merchant page title standard + header consistency PASS');
