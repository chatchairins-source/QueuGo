'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const root=require('node:path').resolve(__dirname,'..');
const rider=fs.readFileSync(root+'/rider/index.html','utf8');
const merchant=fs.readFileSync(root+'/merchant/index.html','utf8');
const marketMembership=fs.readFileSync(root+'/merchant/market-registration.js','utf8');

assert.match(rider,/<input required id="rr-province" maxlength="100" autocomplete="address-level1"/);
assert.doesNotMatch(rider,/<select[^>]+id="rr-province"/);
assert.match(rider,/province:String\(f\('rr-province'\)\.value\|\|''\)\.trim\(\)/);
assert.match(rider,/serviceProvince:form\.province\|\|null/);
assert.match(rider,/serviceDistrict:form\.district\|\|null/);

const metadataStart=rider.indexOf('function riderRegistrationMetadata(');
const metadataEnd=rider.indexOf('\nfunction ',metadataStart+10);
assert(metadataStart>=0&&metadataEnd>metadataStart,'Rider registration metadata function exists');
const metadataContext=vm.createContext({Date});
vm.runInContext(rider.slice(metadataStart,metadataEnd),metadataContext);
const metadata=metadataContext.riderRegistrationMetadata({id:'rider-test'},{},{province:'ภูเก็ต',district:'เมืองภูเก็ต'});
assert.equal(metadata.serviceProvince,'ภูเก็ต');
assert.equal(metadata.serviceDistrict,'เมืองภูเก็ต');

assert.doesNotMatch(marketMembership,/province=eq\.|const BURIRAM/);
assert.doesNotMatch(merchant,/province=eq\.'\+encodeURIComponent\('บุรีรัมย์'\)/);
assert.doesNotMatch(merchant,/id="qgm-reg-request-province"[^>]*value="บุรีรัมย์"/);
assert.doesNotMatch(merchant,/id="qgm-reg-request-district"[^>]*value="เมืองบุรีรัมย์"/);
assert.match(marketMembership,/active=eq\.true&latitude=not\.is\.null&longitude=not\.is\.null&order=name\.asc/);
assert.match(merchant,/active=eq\.true&latitude=not\.is\.null&longitude=not\.is\.null&order=name\.asc/);

const loaderStart=marketMembership.indexOf('async function loadMarkets(');
const loaderEnd=marketMembership.indexOf('\nfunction applyFilter(',loaderStart);
assert(loaderStart>=0&&loaderEnd>loaderStart,'shared market membership loader exists');
const requestLog=[];
const marketContext=vm.createContext({
  qtGetAccessToken:async()=> 'test-token',
  qtSupabaseTable:async(path,options)=>{requestLog.push({path,options});return [
    {id:'phuket',name:'ตลาดภูเก็ต',province:'ภูเก็ต',latitude:7.8804,longitude:98.3923,assignment_radius_km:3},
    {id:'buriram',name:'ตลาดบุรีรัมย์',province:'บุรีรัมย์',latitude:14.993,longitude:103.103,assignment_radius_km:3}
  ]},
  distanceKm:(lat1,lng1,lat2,lng2)=>{
    const rad=x=>x*Math.PI/180,dLat=rad(lat2-lat1),dLng=rad(lng2-lng1);
    const a=Math.sin(dLat/2)**2+Math.cos(rad(lat1))*Math.cos(rad(lat2))*Math.sin(dLng/2)**2;
    return 6371*2*Math.asin(Math.min(1,Math.sqrt(a)));
  },Number,String,Array,Math
});
vm.runInContext(marketMembership.slice(loaderStart,loaderEnd)+';globalThis.loadMarkets=loadMarkets;',marketContext);
(async()=>{
  const rows=await marketContext.loadMarkets(7.8804,98.3923);
  assert.equal(requestLog.length,1);
  assert.doesNotMatch(requestLog[0].path,/province=eq\./);
  assert.equal(rows[0].market_id,'phuket');
  assert.equal(rows.at(-1).market_id,'buriram');
  console.log(JSON.stringify({checks:15,failures:0,scope:'nationwide Rider province registration and all-province market discovery; no signup or Production mutation'}));
})().catch(error=>{console.error(error);process.exit(1)});
