'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../merchant/market-catalog.js'),'utf8');
const start=source.indexOf("    }else {\n      shell('<p>โหลดแคตตาล็อกไม่สำเร็จ");
const end=source.indexOf('\n    error(e);',start);
assert.ok(start>=0&&end>start,'error branch exposes retry focus recovery');
const snippet=source.slice(start+'    }else '.length,end); // evaluate the complete else block
function simulate(focusedPage){
  const events=[];
  const retry={focus:options=>events.push(['focus',options.preventScroll])};
  const shell=html=>events.push(['shell',html.includes('market-catalog-initial-retry')]);
  vm.runInNewContext(snippet,{focusedPage,shell,document:{getElementById:id=>{
    events.push(['lookup',id]);return retry;
  }}});
  return events;
}
for(const page of ['market-catalog-next','market-catalog-prev']){
  const events=simulate(page);
  assert.deepEqual(events.map(e=>e[0]),['shell','lookup','focus'],page+' focuses after shell');
  assert.equal(events[0][1],true,page+' renders retry');
  assert.equal(events[1][1],'market-catalog-initial-retry',page+' targets retry');
  assert.equal(events[2][1],true,page+' prevents scroll jump');
}
assert.deepEqual(simulate(null).map(e=>e[0]),['shell'],'non-pagination failures do not steal focus');
console.log('market catalog pagination failure focus: 9 checks passed');
