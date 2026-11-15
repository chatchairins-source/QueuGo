'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../merchant/market-catalog.js'),'utf8');
const start=source.indexOf('    const currentInput=focusedSearch?document.getElementById(\'market-catalog-search\'):null;');
const end=source.indexOf('    const selection=currentInput&&Number.isInteger',start);
assert.ok(start>0&&end>start,'response guard precedes result publication');
const guarded=source.slice(start,end);
assert.ok(guarded.indexOf('if(currentInput&&currentInput.value.trim()!==state.query)return;')<guarded.indexOf('state.rows=rows.slice(0,50)'), 'stale query must be rejected before publishing rows');
function run(typed,query){
  const state={query,rows:[{id:'previous'}],categories:[{category:'previous'}],hasMore:false,gpRate:7};
  const ctx={state,focusedSearch:true,document:{getElementById:()=>({value:typed})},gp:5,cats:[{category:'ผักสด',n:1}],page:[{id:'new',category:'ผักสด'}],MARKET_MASTER_CATEGORIES:new Set(['ผักสด'])};
  vm.createContext(ctx);
  vm.runInContext('(function(){'+guarded+'})()',ctx);
  return state;
}
let s=run('newly typed','old');
assert.equal(s.rows[0].id,'previous','old response must not replace visible rows');
assert.equal(s.gpRate,7,'old response must not publish stale GP');
assert.equal(s.categories[0].category,'previous','old response must not publish categories');
s=run('current','current');
assert.equal(s.rows[0].id,'new','matching query may publish new rows');
assert.equal(s.gpRate,5);
assert.equal(s.categories[0].category,'ผักสด');
console.log('Market catalog stale response publication regression passed');
