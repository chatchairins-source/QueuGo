'use strict';
const fs=require('fs'),path=require('path');

const RIGHTS_EVIDENCE='https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorials/license-be-on-the-legal-side/';
const ATTRIBUTION='Open Food Facts contributors';

function barcode(value){
 const clean=String(value??'').replace(/\D/g,'');
 return clean.length>=8&&clean.length<=14?clean:null;
}
function httpsUrl(value){
 if(typeof value!=='string'||!value.trim())return null;
 try{const u=new URL(value);return u.protocol==='https:'?u.href:null}catch{return null}
}
function productCode(product){return barcode(product?.code??product?._id)}
function languageImage(value){
 if(typeof value==='string')return httpsUrl(value);
 if(!value||typeof value!=='object')return null;
 for(const lang of ['th','en','fr','de','es']){const url=httpsUrl(value[lang]);if(url)return url}
 for(const url of Object.values(value)){const safe=httpsUrl(url);if(safe)return safe}
 return null;
}
function frontImage(product){
 const direct=httpsUrl(product?.image_front_url);if(direct)return direct;
 const front=product?.selected_images?.front;
 if(!front||typeof front!=='object')return null;
 for(const size of ['display','small','thumb']){const url=languageImage(front[size]);if(url)return url}
 return null;
}
function candidate(row,payload){
 const expected=barcode(row?.barcode);if(!expected)return null;
 const product=payload?.product;
 if(!product||productCode(product)!==expected)return null;
 const image=frontImage(product);if(!image)return null;
 return {
   normalized_key:row.normalized_key,
   barcode:expected,
   brand:row.brand??null,
   product_name_th:row.product_name_th??null,
   image_url:image,
   source_name:'Open Food Facts',
   source_url:'https://world.openfoodfacts.org/product/'+expected,
   identity_status:'barcode_exact',
   rights_status:'cc-by-sa',
   rights_evidence:RIGHTS_EVIDENCE,
   attribution:ATTRIBUTION,
   share_alike_required:true,
   production_approved:false
 };
}
function collect(rows,directory){
 const items=[];
 for(const row of rows){
   const code=barcode(row.barcode);
   if(!code){items.push({...row,off_status:'missing_barcode',candidate:null});continue}
   const file=path.join(directory,code+'.json');
   if(!fs.existsSync(file)){items.push({...row,off_status:'snapshot_missing',candidate:null});continue}
   let payload=null;try{payload=JSON.parse(fs.readFileSync(file,'utf8'))}catch{}
   const image=candidate(row,payload);
   items.push({...row,off_status:image?'rights_candidate':'no_exact_front',candidate:image});
 }
 const count=status=>items.filter(x=>x.off_status===status).length;
 return {
   version:1,
   created_at:new Date().toISOString(),
   policy:'Barcode-exact Open Food Facts front images carry CC BY-SA reuse evidence, attribution and ShareAlike metadata. Candidates remain unapproved until QueueGo image review confirms the package photo is the intended sellable item.',
   license:{rights_status:'cc-by-sa',rights_evidence:RIGHTS_EVIDENCE,attribution:ATTRIBUTION,share_alike_required:true},
   statistics:{total:items.length,rights_candidates:count('rights_candidate'),missing_barcode:count('missing_barcode'),snapshot_missing:count('snapshot_missing'),no_exact_front:count('no_exact_front'),approved:0},
   items
 };
}
module.exports={RIGHTS_EVIDENCE,ATTRIBUTION,barcode,httpsUrl,frontImage,candidate,collect};
if(require.main===module){
 const [directory,out]=process.argv.slice(2);
 if(!directory||!out)throw Error('Usage: node collect-openfoodfacts-image-candidates.cjs OFF_SNAPSHOT_DIRECTORY OUTPUT_JSON');
 const rows=require('../../catalog/thai-grocery-import.json');
 const result=collect(rows,directory);
 fs.mkdirSync(path.dirname(out),{recursive:true});
 fs.writeFileSync(out,JSON.stringify(result,null,2)+'\n');
 console.log(JSON.stringify(result.statistics));
}
