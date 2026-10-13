(function(root){
'use strict';
const text=v=>String(v??'').normalize('NFKC').replace(/\u0e4d([\u0e48-\u0e4b]?)\u0e32/g,'$1\u0e33').replace(/[\u200b-\u200d\ufeff]/g,'').replace(/[๐-๙]/g,c=>String(c.charCodeAt(0)-3664)).trim().replace(/\s+/g,' ');
const folded=v=>text(v).toLowerCase().replace(/[\s.\-_]/g,'');
const aliases={'coke':'coca-cola','โค้ก':'coca-cola','cocacola':'coca-cola','coca-cola':'coca-cola','มาม่า':'mama','mama':'mama'};
function gtin(v){const s=text(v).replace(/[ -]/g,'');if(!s)return null;if(!/^(\d{8}|\d{12}|\d{13}|\d{14})$/.test(s))throw Error('invalid GTIN length');let n=0;for(let i=s.length-2,w=3;i>=0;i--,w=4-w)n+=Number(s[i])*w;if((10-n%10)%10!==Number(s.at(-1)))throw Error('invalid GTIN check digit');return s.padStart(14,'0');}
function size(value,unit){let n=Number(text(value).replace(/,/g,''));const k=folded(unit),map={ml:['ml',1],มล:['ml',1],มิลลิลิตร:['ml',1],l:['ml',1000],ล:['ml',1000],ลิตร:['ml',1000],g:['g',1],ก:['g',1],กรัม:['g',1],gram:['g',1],kg:['g',1000],กก:['g',1000],กิโลกรัม:['g',1000],ชิ้น:['piece',1],piece:['piece',1],ฟอง:['egg',1],egg:['egg',1],แผ่น:['sheet',1],sheet:['sheet',1]};if(!map[k]||!Number.isFinite(n)||n<=0||n>1000000)throw Error('invalid size');return {size_value:Math.round(n*map[k][1]*1000)/1000,size_unit:map[k][0]};}
function normalize(row){
 if(!row||typeof row!=='object'||Array.isArray(row))throw Error('invalid row');
 const brand=text(row.brand),name=text(row.product_name_th).replace(/\d+(?:[.,]\d+)*\s*(?:มิลลิลิตร|กิโลกรัม|กรัม|มล\.?|กก\.?|ลิตร|ml\.?|kg\.?|gram\b|g\b|l\b)/gi,'').trim(),variant=text(row.variant),category=text(row.category),sales=text(row.sales_unit);
 if(!brand||!name||!category||!sales||[brand,name,variant,category,sales].some(v=>v.length>250))throw Error('brand/name/category/sales unit required');
 const s=size(row.size_value,row.size_unit),pack=Number(row.pack_size??1);if(!Number.isInteger(pack)||pack<1||pack>10000)throw Error('invalid pack size');
 const source=new URL(row.source_url);if(source.protocol!=='https:')throw Error('HTTPS source required');
 if(!['manufacturer','distributor','retailer','government','merchant'].includes(row.source_type))throw Error('invalid source type');
 const retrieved=new Date(row.retrieved_at);if(!Number.isFinite(retrieved.getTime()))throw Error('retrieved_at required');
 const barcode=gtin(row.barcode),b=aliases[folded(brand)]||folded(brand);let pn=folded(name);for(const [alias,canonical]of Object.entries(aliases)){if(canonical===b&&pn.startsWith(folded(alias)))pn=pn.slice(folded(alias).length);}
 if(b==='coca-cola'&&['','น้ำอัดลม','cola'].includes(pn))pn='cola';
 const normalized_key=[b,pn,folded(variant),s.size_value,s.size_unit,pack,folded(sales)].join('|');
 // Public origin is never proof of image licensing. Import cannot approve an image.
 return {barcode,brand,product_name_th:name,product_name_en:text(row.product_name_en)||null,variant,...s,pack_size:pack,sales_unit:sales,category,subcategory:text(row.subcategory),normalized_key,source_url:source.href,source_type:row.source_type,retrieved_at:retrieved.toISOString(),source_product_id:text(row.source_product_id)||null,image:null,image_status:'pending',active:row.active===true};
}
function batch(rows){if(!Array.isArray(rows)||rows.length>50000)throw Error('expected <=50000 rows');const items=[],rejects=[],seen=new Map();rows.forEach((r,i)=>{try{const p=normalize(r),k=p.barcode?'gtin:'+p.barcode:p.normalized_key;if(seen.has(k)){const prior=items[seen.get(k)];if(prior.normalized_key!==p.normalized_key&&p.barcode)throw Error('GTIN conflicts with different identity');return;}seen.set(k,items.length);items.push(p);}catch(e){rejects.push({row:i+1,error:e.message});}});return {items,rejects};}
function csv(input){let rows=[],row=[],field='',quoted=false;for(let i=0;i<input.length;i++){const c=input[i];if(c==='"'){if(quoted&&input[i+1]==='"'){field+='"';i++;}else quoted=!quoted;}else if(!quoted&&(c===','||c==='\n')){row.push(field.replace(/\r$/,''));field='';if(c==='\n'){rows.push(row);row=[];}}else field+=c;}if(quoted)throw Error('unterminated CSV quote');if(field||row.length){row.push(field.replace(/\r$/,''));rows.push(row);}const headers=rows.shift()?.map(text);if(!headers?.length||new Set(headers).size!==headers.length)throw Error('invalid CSV headers');return rows.filter(r=>r.some(Boolean)).map(r=>{if(r.length!==headers.length)throw Error('CSV column mismatch');return Object.fromEntries(headers.map((h,i)=>[h,r[i]]));});}
const api={text,gtin,size,normalize,batch,csv};if(typeof module==='object')module.exports=api;else root.QGGroceryNormalize=api;
})(globalThis);
