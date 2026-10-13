const fs=require('fs'),path=require('path'),assert=require('assert'),vm=require('vm');
const root=path.resolve(__dirname,'..'),source=fs.readFileSync(path.join(root,'index.html'),'utf8');let checks=0;
const ok=(v,m)=>{assert.ok(v,m);checks++};
const keys=['shopping','mobile_accessories','computer_it','automotive','fashion_accessories','toys','home_decor','laundry'];
for(const key of keys){
 const bytes=fs.readFileSync(path.join(root,'assets/banners',`qg_${key}_banner.webp`));
 ok(bytes.toString('ascii',0,4)==='RIFF'&&bytes.toString('ascii',8,12)==='WEBP',key+' must be valid WebP container');
 ok(bytes.readUInt32LE(4)+8===bytes.length,key+' container length must match');
 ok(bytes.length<=50*1024,key+' exceeds image budget');
}
const ctx=vm.createContext({serviceBannerConfigCache:{},esc:s=>String(s??''),qgOpenHomeBanner:()=>{}});
vm.runInContext(source.slice(source.indexOf('function qgServiceBannerEntry'),source.indexOf('const SHOPPING_SUBCATEGORY_LABELS')),ctx);
for(const key of keys.filter(k=>k!=='laundry')){
 const html=vm.runInContext(`qgServiceBannerMarkup('${key}','','title','subtitle')`,ctx);
 ok(html.includes(`assets/banners/qg_${key}_banner.webp`),key+' shipped default image missing');
}
vm.runInContext("serviceBannerConfigCache={toys:{active:false}}",ctx);
ok(vm.runInContext("qgServiceBannerMarkup('toys','','','')",ctx)==='','Admin hide respected');
vm.runInContext("serviceBannerConfigCache={toys:{image_url:'https://example.invalid/banner.webp'}}",ctx);
const html=vm.runInContext("qgServiceBannerMarkup('toys','','title','subtitle')",ctx);
ok(html.includes('src="https://example.invalid/banner.webp"'),'managed image precedence');
ok(html.includes('data-fallback="assets/banners/qg_toys_banner.webp"'),'managed image failure fallback');
ok(!source.includes('qg-shopping-banner-art'),'retired corrupt sprite path');
const shopping=source.slice(source.indexOf('V.shopping=async'),source.indexOf('async function shareShop'));
ok(!/qg-shopping-category[^>]*>[\s]*<img/.test(shopping),'retain category icons, pictures only in banners');
ok(fs.readFileSync(path.join(root,'laundry/index.html'),'utf8').includes('assets/banners/qg_laundry_banner.webp'),'Laundry banner image');
console.log(JSON.stringify({checks,failures:0,scope:'all category banner WebP files, image budget, Admin override/hide/failure and retained category icons'}));
