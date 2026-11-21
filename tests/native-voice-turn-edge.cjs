const fs=require('fs');
const assert=require('assert');
const code=fs.readFileSync('supabase/functions/queuego-turn/index.ts','utf8');

function has(re,msg){assert(re.test(code),msg)}
has(/CLOUDFLARE_TURN_KEY_ID/,'TURN key id secret missing');
has(/CLOUDFLARE_TURN_KEY_API_TOKEN/,'TURN API token secret missing');
has(/SUPABASE_ANON_KEY/,'user-context Supabase key missing');
has(/\/rest\/v1\/rpc\/qg_call_ice_config/,'server authorization RPC missing');
has(/authorization,\s*['"]content-type['"]/s,'caller Authorization header must be forwarded to RPC');
assert(!/SUPABASE_SERVICE_ROLE_KEY/.test(code),'service role must not authorize user call access');
has(/generate-ice-servers/,'Cloudflare credential endpoint missing');
has(/JSON\.stringify\(\{ttl:7200\}\)/,'TURN credentials must be short lived and bounded');
has(/Cache-Control['"]?:['"]no-store|['"]Cache-Control['"]\s*:\s*['"]no-store/i,'TURN response must not be cached');
has(/turnReady:true/,'successful response must positively identify TURN readiness');
has(/turnReady:false/,'failure response must never claim TURN readiness');
has(/stun:stun\.cloudflare\.com:3478/,'real STUN fallback missing');
has(/\^\(\?:stun\|stuns\|turn\|turns\):/,'ICE URL scheme allowlist missing');
has(/some\(\(row:any\)=>row\.urls\.some\(\(url:string\)=>\/\^turns\?:/,'success must include a TURN relay');
assert(!/console\.(log|error|warn)\([^)]*(turnApiToken|authorization|credential)/i.test(code),
  'TURN/JWT credentials must not be logged');
assert(!/Deno\.env\.get\([^)]*\).*(return|respond)/s.test(code.match(/Deno\.serve[\s\S]*/)?.[0]||''),
  'environment secrets must not be returned');
console.log('Native voice TURN edge contract: PASS');
