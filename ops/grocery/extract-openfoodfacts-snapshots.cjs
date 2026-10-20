'use strict';
const fs=require('fs'),path=require('path'),readline=require('readline');
const {barcode}=require('./collect-openfoodfacts-image-candidates.cjs');

function targetBarcodes(rows){
 const out=new Set();
 for(const row of rows){const code=barcode(row?.barcode);if(code)out.add(code)}
 return out;
}
function compactProduct(raw){
 const product=raw?.product&&typeof raw.product==='object'?raw.product:raw;
 const code=barcode(product?.code??product?._id);
 if(!code)return null;
 return {product:{
   code,
   image_front_url:typeof product.image_front_url==='string'?product.image_front_url:null,
   selected_images:product.selected_images&&typeof product.selected_images==='object'?product.selected_images:null
 }};
}
async function extract(jsonlPath,directory,rows){
 const wanted=targetBarcodes(rows),written=new Set();
 fs.mkdirSync(directory,{recursive:true});
 const stream=fs.createReadStream(jsonlPath,{encoding:'utf8'});
 const rl=readline.createInterface({input:stream,crlfDelay:Infinity});
 let scanned=0,invalid=0;
 for await(const line of rl){
   if(!line.trim())continue; scanned++;
   let raw;try{raw=JSON.parse(line)}catch{invalid++;continue}
   const compact=compactProduct(raw),code=compact?.product?.code;
   if(!code||!wanted.has(code)||written.has(code))continue;
   fs.writeFileSync(path.join(directory,code+'.json'),JSON.stringify(compact)+'\n');
   written.add(code);
   if(written.size===wanted.size)break;
 }
 return {catalog_barcodes:wanted.size,scanned,matched:written.size,missing:wanted.size-written.size,invalid};
}
module.exports={targetBarcodes,compactProduct,extract};
if(require.main===module){
 const [jsonlPath,directory,catalogPath]=process.argv.slice(2);
 if(!jsonlPath||!directory)throw Error('Usage: node extract-openfoodfacts-snapshots.cjs OPENFOODFACTS.jsonl SNAPSHOT_DIRECTORY [CATALOG_JSON]');
 const rows=require(path.resolve(catalogPath||path.join(__dirname,'../../catalog/thai-grocery-import.json')));
 extract(jsonlPath,directory,rows).then(result=>console.log(JSON.stringify(result))).catch(error=>{console.error(error);process.exitCode=1});
}
