'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');

const source=fs.readFileSync(path.join(__dirname,'../merchant/market-catalog.js'),'utf8');
const begin=source.indexOf('if(focusedSearch||focusedCategory){',source.indexOf('async function load(){'));
const end=source.indexOf('}else shell(',begin);
assert.ok(begin>=0&&end>begin,'focused RPC failure branch exists');
const branch=source.slice(begin,end+1);
const created=[];
const status={textContent:'',children:[],appendChild(node){this.children.push(node)}};
const document={getElementById(id){return id==='market-catalog-status'?status:null},createElement(tag){const node={tag,handlers:{},addEventListener(event,fn){this.handlers[event]=fn}};created.push(node);return node}};
let retries=0;
const run=new Function('document','qgRenderMarketCatalog','focusedSearch','focusedCategory',branch);
for(const [search,category] of [[true,false],[false,true]]){
  status.children=[];status.textContent='';created.length=0;
  run(document,()=>{retries++},search,category);
  assert.match(status.textContent,/โหลดแคตตาล็อกไม่สำเร็จ/);
  assert.equal(created.length,1,'exactly one retry control');
  const button=status.children[0];
  assert.equal(button.tag,'button');
  assert.equal(button.type,'button','retry must not submit forms');
  assert.equal(button.textContent,'ลองโหลดแคตตาล็อกอีกครั้ง');
  assert.equal(typeof button.handlers.click,'function');
  button.handlers.click();
}
assert.equal(retries,2,'both focused search and category retry existing loader');
console.log('market catalog focused RPC retry: 13 checks passed');
