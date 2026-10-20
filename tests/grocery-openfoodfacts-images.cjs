'use strict';
const assert=require('node:assert/strict'),fs=require('fs'),os=require('os'),path=require('path');
const I=require('../ops/grocery/collect-openfoodfacts-image-candidates.cjs');
let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
const row={barcode:'8850000000001',normalized_key:'demo',brand:'ทดสอบ',product_name_th:'สินค้าทดสอบ'};
eq(I.barcode('885-0000000001'),'8850000000001');
eq(I.barcode('123'),null);
eq(I.httpsUrl('http://images.example/a.jpg'),null);
eq(I.httpsUrl('https://images.example/a.jpg'),'https://images.example/a.jpg');
const payload={product:{code:'8850000000001',image_front_url:'https://images.openfoodfacts.org/demo.jpg'}};
const c=I.candidate(row,payload);
eq(c.identity_status,'barcode_exact');eq(c.rights_status,'cc-by-sa');eq(c.rights_evidence,I.RIGHTS_EVIDENCE);eq(c.attribution,I.ATTRIBUTION);eq(c.share_alike_required,true);eq(c.production_approved,false);
eq(I.candidate(row,{product:{code:'8850000000002',image_front_url:'https://images.openfoodfacts.org/wrong.jpg'}}),null);
eq(I.candidate(row,{product:{code:'8850000000001',image_front_url:'http://images.openfoodfacts.org/insecure.jpg'}}),null);
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'qg-off-'));
try{
 fs.writeFileSync(path.join(dir,'8850000000001.json'),JSON.stringify(payload));
 const result=I.collect([row,{...row,barcode:'8850000000002',normalized_key:'missing'},{...row,barcode:null,normalized_key:'no-code'}],dir);
 eq(result.statistics.total,3);eq(result.statistics.rights_candidates,1);eq(result.statistics.snapshot_missing,1);eq(result.statistics.missing_barcode,1);eq(result.statistics.approved,0);
 eq(result.items[0].candidate.production_approved,false);
}finally{fs.rmSync(dir,{recursive:true,force:true})}
console.log(JSON.stringify({checks,failures:0,scope:'Open Food Facts barcode identity + CC BY-SA evidence; no automatic Production approval'}));
