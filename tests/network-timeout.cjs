const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),{parse}=require('acorn');
(async()=>{let checks=0;
for(const [file,names] of [['index.html',['auth','db']],['merchant/index.html',['qtSupabaseAuth','qtSupabaseTable']],['admin/index.html',['qtSupabaseAuth','qtSupabaseTable','qtSupabaseRpc']],['rider/index.html',['sbAuth','sbTable','sbRpc']]]){
 const functions=new Map();for(const m of fs.readFileSync(file,'utf8').matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g))for(const n of parse(m[1],{ecmaVersion:'latest'}).body)if(n.type==='FunctionDeclaration')functions.set(n.id.name,m[1].slice(n.start,n.end));
 for(const name of names){let response;const ctx=vm.createContext({AbortSignal,fetch:async()=>response,QT_SUPABASE_CONFIG:{URL:'https://isolated.invalid'},SB:{URL:'https://isolated.invalid'},qtSupabaseHeaders:()=>({}),sbHeaders:()=>({}),qtGetAccessToken:async()=> 'isolated-token',token:async()=> 'isolated-token',CFG:{URL:'https://isolated.invalid'},hdr:()=>({})});vm.runInContext(functions.get(name),ctx);const call=()=>ctx[name]('isolated',{});
  for(const errorName of ['AbortError','TimeoutError']){response={ok:true,json:async()=>{throw new DOMException('Response body interrupted',errorName)}};await assert.rejects(call(),e=>e.name===errorName);checks++;}
  if(/Table|Rpc/.test(name)||name==='db'){response={ok:true,status:204,json:async()=>{throw new SyntaxError('No body')}};assert.equal(await call(),null);checks++;}
 }
}
console.log(JSON.stringify({checks,failures:0,scope:'isolated transport body interruption; aborted mutations cannot be reported as successful empty responses'},null,2));})().catch(e=>{console.error(e);process.exit(1)});
