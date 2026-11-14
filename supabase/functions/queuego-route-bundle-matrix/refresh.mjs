// Bounded evidence replenishment only. The existing dispatcher remains the only assigner.
export async function refreshRouteMatrices(base, headers, fetcher, produce, {includeStack=false}={}) {
 const rpc=async(name,body)=>{
  const response=await fetcher(base+'/rest/v1/rpc/'+name,{method:'POST',headers,
   body:JSON.stringify(body),signal:AbortSignal.timeout(8000)});
  if(!response.ok)throw Error('refresh coordination unavailable');
  return response.json();
 };
 let refreshed=0,failed=0,claimed=0;
 const run=async(name)=>{
  const work=await rpc(name,{p_limit:2});
  if(!work?.leaseId)return;
  claimed++;
  try{
   if(!Array.isArray(work.pairs)||work.pairs.length>2)throw Error('invalid refresh work');
   const results=await Promise.allSettled(work.pairs.map(pair=>produce(pair)));
   for(const result of results){if(result.status==='fulfilled'&&result.value.ok)refreshed++;else failed++;}
  }finally{
   await rpc('queuego_route_matrix_refresh_release',{p_lease:work.leaseId});
  }
 };
 await run('queuego_route_matrix_refresh_claim');
 if(includeStack){
  try{
   await run('queuego_route_bundle_stack_refresh_claim');
  }catch{
   // Generalized stack replenishment is an additive rollout path. A missing,
   // stale-deployment or temporarily unavailable stack coordinator must never
   // break the established pair evidence refresh or existing Rider jobs.
  }
 }
 return claimed===0
  ? {refreshed,failed,busyOrDisabled:true}
  : {refreshed,failed};
}

export async function workerTokenMatches(supplied, expected) {
 if(typeof supplied!=='string'||typeof expected!=='string'||expected.length<32)return false;
 const encoder=new TextEncoder();
 const [left,right]=await Promise.all([supplied,expected].map(value=>crypto.subtle.digest('SHA-256',encoder.encode(value))));
 const a=new Uint8Array(left),b=new Uint8Array(right);let difference=0;
 for(let i=0;i<a.length;i++)difference|=a[i]^b[i];
 return difference===0;
}
