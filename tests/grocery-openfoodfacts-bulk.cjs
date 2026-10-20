'use strict';
const assert=require('node:assert/strict'),fs=require('fs'),os=require('os'),path=require('path');
const C=require('../ops/grocery/collect-openfoodfacts-image-candidates.cjs');
const E=require('../ops/grocery/extract-openfoodfacts-snapshots.cjs');
let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
eq(C.barcode('034000470693'),'0034000470693');
eq(C.barcode('00012345'),'00012345');
eq(C.barcode('3017624010701'),'3017624010701');
const rows=[{barcode:'034000470693'},{barcode:'3017624010701'},{barcode:null}];
eq([...E.targetBarcodes(rows)].sort(),['0034000470693','3017624010701']);
eq(E.compactProduct({code:'034000470693',image_front_url:'https://images.example/a.jpg',selected_images:{front:{display:{en:'https://images.example/b.jpg'}}},ingredients_text:'do-not-copy'}),{
 product:{code:'0034000470693',image_front_url:'https://images.example/a.jpg',selected_images:{front:{display:{en:'https://images.example/b.jpg'}}}}
});
const root=fs.mkdtempSync(path.join(os.tmpdir(),'qg-off-jsonl-')),dump=path.join(root,'off.jsonl'),out=path.join(root,'snapshots');
fs.writeFileSync(dump,[
 JSON.stringify({code:'9999999999999',image_front_url:'https://images.example/skip.jpg'}),
 '{bad json',
 JSON.stringify({code:'034000470693',image_front_url:'https://images.example/upc.jpg'}),
 JSON.stringify({code:'3017624010701',selected_images:{front:{display:{en:'https://images.example/nutella.jpg'}}}})
].join('\n')+'\n');
E.extract(dump,out,rows).then(result=>{
 eq(result.catalog_barcodes,2);eq(result.matched,2);eq(result.missing,0);eq(result.invalid,1);
 eq(fs.existsSync(path.join(out,'0034000470693.json')),true);
 eq(fs.existsSync(path.join(out,'3017624010701.json')),true);
 const saved=JSON.parse(fs.readFileSync(path.join(out,'0034000470693.json'),'utf8'));
 eq(saved.product.code,'0034000470693');
 eq(Object.prototype.hasOwnProperty.call(saved.product,'ingredients_text'),false);
 fs.rmSync(root,{recursive:true,force:true});
 console.log(JSON.stringify({checks,failures:0,scope:'OFF bulk JSONL filter, barcode normalization and minimal snapshot persistence'}));
}).catch(error=>{fs.rmSync(root,{recursive:true,force:true});throw error});
