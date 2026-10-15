'use strict';
// Read public source snapshots. This creates a review queue, never approves images
// or writes Production. Identity evidence and reuse evidence are separate fields.
const fs=require('fs'),path=require('path'),crypto=require('crypto'),{JSDOM,VirtualConsole}=require('jsdom'),N=require('../../catalog/normalize.js');
const clean=s=>N.text(s),fold=s=>clean(s).toLowerCase().replace(/[\s.\-_]/g,'');
function imageUrl(value,base){if(typeof value!=='string'||!value.trim())return null;try{const u=new URL(value,base);return u.protocol==='https:'&&!/\.svg(?:[?#]|$)/i.test(u.href)?u.href:null}catch{return null}}
function sizes(label){return [...clean(label).matchAll(/(\d+(?:[.,]\d+)*)\s*(มิลลิลิตร|กิโลกรัม|กรัม|มล\.?|กก\.?|ลิตร|ml\.?|kg\.?|gram\b|g\b|l\b)/gi)].flatMap(m=>{try{return [N.size(m[1],m[2])]}catch{return []}})}
function spc(doc,url){return [...doc.querySelectorAll('div.item')].flatMap(item=>{const label=clean(item.querySelector('p')?.textContent||''),image=imageUrl(item.querySelector('img')?.getAttribute('src'),url);return label&&image?[{label,image_url:image,selector:'div.item img + p',explicit_sizes:sizes(label)}]:[]})}
function generic(doc,url){
 const title=clean(doc.querySelector('h1')?.textContent||doc.title||'');let nodes=[];
 if(/hiqfood\.com/.test(url))nodes=[...doc.querySelectorAll('img.card-img-top')].filter(n=>n.parentElement.classList.contains('item'));
 else if(/foremostthailand\.com/.test(url))nodes=[...doc.querySelectorAll('img.preload-img.wp-post-image')];
 else if(/mitrpholsugar\.com/.test(url))nodes=[...doc.querySelectorAll('.woocommerce-product-gallery img,.product-gallery img')];
 else if(/shop\.deksomboon\.com|shop\.ip-one\.com/.test(url))nodes=[...doc.querySelectorAll('img')].filter(n=>/product|gallery|zoom/i.test(n.className+' '+(n.getAttribute('src')||'')));
 const result=nodes.map(n=>({label:title,image_url:imageUrl(n.getAttribute('data-src')||n.getAttribute('src'),url),selector:n.className,explicit_sizes:sizes(title)})).filter(x=>x.image_url);
 // Open Graph may be a banner/brand logo; it is a candidate only, never an exact match.
 const og=imageUrl(doc.querySelector('meta[property="og:image"]')?.content,url);if(og&&!/logo|icon|default|opengraph/i.test(og)&&!result.some(x=>x.image_url===og))result.push({label:title,image_url:og,selector:'meta[property="og:image"]',explicit_sizes:[],generic_og:true});
 return result;
}
function match(row,candidates){return candidates.map(c=>{
 let exact=false;for(const size of c.explicit_sizes){try{const observed=N.normalize({...row,...size,product_name_th:c.label});if(observed.normalized_key===row.normalized_key&&Number(row.pack_size)===1&&(!row.variant||fold(c.label).includes(fold(row.variant))))exact=true}catch{}}
 const name=fold(row.product_name_th),label=fold(c.label),sameFamily=label.includes(name)||name.includes(label);
 return {...c,match:exact?'label_size_exact':sameFamily?'family_review':'page_review',rights_status:'unverified',rights_evidence:null};
 }).sort((a,b)=>({label_size_exact:0,family_review:1,page_review:2}[a.match]-{label_size_exact:0,family_review:1,page_review:2}[b.match]));}
function collect(rows,directory){
 const snapshots=new Map(),items=[];const alternateFile=path.resolve(__dirname,'../../catalog/image-alternate-sources-20261015.json'),alternatives=fs.existsSync(alternateFile)?JSON.parse(fs.readFileSync(alternateFile,'utf8')).observations:[];for(const row of rows){
  const url=row.source_url;let source=snapshots.get(url);
  if(!source){const file=path.join(directory,crypto.createHash('sha256').update(url).digest('hex').slice(0,20)+'.html');source={source_url:url,source_type:row.source_type,status:fs.existsSync(file)?'fetched':'unavailable',candidates:[]};
   // Retailer references do not grant image reuse; keep them out of this collector.
   if(row.source_type==='retailer')source.status='retailer_excluded';
   else if(fs.existsSync(file)){const dom=new JSDOM(fs.readFileSync(file,'utf8'),{virtualConsole:new VirtualConsole()});source.candidates=url.includes('sahapat.co.th/product/')?spc(dom.window.document,url):generic(dom.window.document,url);dom.window.close()}
   snapshots.set(url,source);
  }
  const candidates=match(row,source.candidates.map(c=>({...c,source_url:url})).concat(alternatives.filter(c=>c.normalized_key===row.normalized_key))),exact=candidates.filter(c=>c.match==='label_size_exact');
  items.push({normalized_key:row.normalized_key,brand:row.brand,product_name_th:row.product_name_th,variant:row.variant,size_value:row.size_value,size_unit:row.size_unit,pack_size:row.pack_size,source_url:url,source_status:source.status,identity_status:exact.length===1?'exact_candidate':exact.length>1?'ambiguous':candidates.length?'review':'missing',candidates:exact.length?exact:candidates,production_approved:false});
 }
 const count=key=>items.filter(x=>x.identity_status===key).length;
 return {version:1,created_at:new Date().toISOString(),policy:'Review candidates only. A matching public photo is not a reuse grant. Never auto-approve or hotlink these URLs in Production.',statistics:{total:items.length,exact_candidates:count('exact_candidate'),ambiguous:count('ambiguous'),review:count('review'),missing:count('missing'),approved:0},items};
}
module.exports={imageUrl,sizes,spc,match,collect};
if(require.main===module){const [directory,out]=process.argv.slice(2);if(!directory||!out)throw Error('Usage: node collect-image-candidates.cjs SOURCE_SNAPSHOT_DIRECTORY OUTPUT_JSON');const result=collect(require('../../catalog/thai-grocery-import.json'),directory);fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result.statistics));}
