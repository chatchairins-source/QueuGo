const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{parse}=require('acorn');
const root=path.resolve(__dirname,'..');let scripts=0,refs=0;
function scan(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>['.git','node_modules','tests'].includes(e.name)?[]:e.isDirectory()?scan(path.join(dir,e.name)):[path.join(dir,e.name)])}
for(const file of scan(root)){
 const s=fs.readFileSync(file,'utf8');
 if(file.endsWith('.js')){try{parse(s,{ecmaVersion:'latest'});scripts++}catch(e){throw new Error('JavaScript syntax: '+path.relative(root,file)+': '+e.message,{cause:e})}}
 if(!file.endsWith('.html'))continue;
 for(const m of s.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)){if(m[1].trim()){parse(m[1],{ecmaVersion:'latest'});scripts++}}
 for(const m of s.matchAll(/(?:src|href)\s*=\s*['"]([^'"]+)['"]/gi)){
  const ref=m[1];if(/^(?:https?:|data:|blob:|javascript:|mailto:|tel:|#)/.test(ref)||/[${}]/.test(ref))continue;
  const relative=ref.split(/[?#]/)[0];if(!relative)continue;
  const target=path.resolve(path.dirname(file),relative);assert(fs.existsSync(target),`Broken reference: ${path.relative(root,file)} -> ${ref}`);refs++;
 }
}
for(const dead of ['market.js','market.css','queuego-market-ai-banner.jpg','merchant/pos.js','merchant/pos.css','merchant/pos-qr-v1.js','merchant/pos-qr-v1.css','laundry/customer-integration.js','laundry/merchant-integration.js'])assert(!fs.existsSync(path.join(root,dead)),`Deleted implementation returned: ${dead}`);
assert(!/marketHeroClean|สดใหม่ใกล้คุณ/.test(fs.readFileSync(path.join(root,'index.html'),'utf8')));
console.log(JSON.stringify({scriptsParsed:scripts,localReferencesChecked:refs,failures:0}));
