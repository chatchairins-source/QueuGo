const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.join(__dirname,'..'),source=fs.readFileSync(path.join(root,'index.html'),'utf8');
let checks=0;
for(const [category,name] of [['food','FOOD_BANNER'],['cafe','CAFE_BANNER'],['grocery','GROCERY_BANNER']]) {
 const data=source.match(new RegExp("const "+name+"='data:image/webp;base64,([^']+)'"));
 assert.ok(data,category+' web blueprint exists');checks++;
 const original=Buffer.from(data[1],'base64');
 const native=fs.readFileSync(path.join(root,'native-android/customer/src/main/res/drawable',`qg_${category}_banner.webp`));
 assert.ok(native.equals(original),category+' Native must reuse the exact web image bytes');checks++;
 assert.ok(native.subarray(0,4).toString()==='RIFF' && native.subarray(8,12).toString()==='WEBP',category+' actual WebP');checks++;
}
const home=source.match(/const HOME_FALLBACK_BANNER="data:image\/webp;base64,([^"]+)"/);
assert.ok(home,'Home web fallback exists');checks++;
const nativeHome=fs.readFileSync(path.join(root,'native-android/customer/src/main/res/drawable/qg_home_banner.webp'));
assert.ok(nativeHome.equals(Buffer.from(home[1],'base64')),'Home Native uses exact web photo');checks++;
assert.ok(nativeHome.subarray(0,4).toString()==='RIFF' && nativeHome.subarray(8,12).toString()==='WEBP','Home actual WebP');checks++;

const market=source.match(/qgServiceBannerMarkup\('market','data:image\/webp;base64,([^']+)'/);
assert.ok(market,'Market web fallback exists');checks++;
const marketDataUri='data:image/webp;base64,'+market[1];
const nativeMarketDataUri=fs.readFileSync(path.join(root,'native-android/customer/src/main/res/raw/qg_market_banner_data_uri.txt'),'utf8').trim();
assert.ok(nativeMarketDataUri===marketDataUri,'Market Native must reuse the exact web fallback data URI');checks++;
const nativeMarket=Buffer.from(nativeMarketDataUri.split(',')[1],'base64');
assert.ok(nativeMarket.subarray(0,4).toString()==='RIFF' && nativeMarket.subarray(8,12).toString()==='WEBP','Market actual WebP');checks++;
const marketScreen=fs.readFileSync(path.join(root,'native-android/customer/src/main/java/com/queuego/customer/QueueGoMarketScreen.kt'),'utf8');
assert.ok(marketScreen.includes('R.raw.qg_market_banner_data_uri'),'Market Native screen must render the verified Production fallback');checks++;
console.log(JSON.stringify({checks,failures:0,scope:'Customer Home/Food/Drink/Grocery/Market assets are byte-identical to the web blueprint; screenshot parity remains pending'}));
