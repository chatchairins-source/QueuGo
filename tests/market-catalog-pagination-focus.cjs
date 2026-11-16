'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../merchant/market-catalog.js'),'utf8');
assert.match(source,/id="market-catalog-prev" onclick="qgMarketCatalogPage\(-1\)"/);
assert.match(source,/id="market-catalog-next" onclick="qgMarketCatalogPage\(1\)"/);
assert.match(source,/const focusedPage=\['market-catalog-prev','market-catalog-next'\]/);
const start=source.indexOf('    }else if(focusedPage){');
const end=source.indexOf('\n    }\n  }catch(e)',start);
assert.ok(start>=0&&end>start,'pagination focus recovery follows repaint');
const snippet=source.slice(start+'    }else if(focusedPage){'.length,end);
function run(focusedPage,prevDisabled,nextDisabled){
  const calls=[];
  const elements={
    'market-catalog-prev':{disabled:prevDisabled,focus:()=>calls.push('prev')},
    'market-catalog-next':{disabled:nextDisabled,focus:()=>calls.push('next')}
  };
  vm.runInNewContext(snippet,{focusedPage,document:{getElementById:id=>elements[id]}});
  return calls;
}
assert.deepEqual(run('market-catalog-next',false,false),['next'],'next page retains focus');
assert.deepEqual(run('market-catalog-next',false,true),['prev'],'last page uses enabled previous');
assert.deepEqual(run('market-catalog-prev',false,true),['prev'],'previous page retains focus');
assert.deepEqual(run('market-catalog-prev',true,false),['next'],'first page uses enabled next');
assert.deepEqual(run('market-catalog-next',true,true),[],'never focus disabled controls');
console.log('market catalog pagination keyboard focus: 9 checks passed');
