// Bounded evidence replenishment only. The existing dispatcher remains the only assigner.
export async function refreshRouteMatrices(base, headers, fetcher, produce) {
 const rpc=async(name,body)=>{
  const response=await fetcher(base+'/rest/v1/rpc/'+name,{method:'POST',headers,
   body:JSON.stringify(body),signal:AbortSignal.timeout(8000)});
  if(!response.ok)throw Error('refresh coordination unavailable');
  return response.json();
 };
 const work=await rpc('queuego_route_matrix_refresh_claim',{p_limit:2});
 if(!work?.leaseId)return {refreshed:0,failed:0,busyOrDisabled:true};
 let refreshed=0,failed=0;
 try{
  if(!Array.isArray(work.pairs)||work.pairs.length>2)throw Error('invalid refresh work');
  const results=await Promise.allSettled(work.pairs.map(pair=>produce(pair)));
  for(const result of results){if(result.status==='fulfilled'&&result.value.ok)refreshed++;else failed++;}
  return {refreshed,failed};
 }finally{
  // Release is fenced by the exact lease. A dead worker expires automatically after90s.
  await rpc('queuego_route_matrix_refresh_release',{p_lease:work.leaseId});
 }
}

export async function workerTokenMatches(supplied, expected) {
 if(typeof supplied!=='string'||typeof expected!=='string'||expected.length<32)return false;
 const encoder=new TextEncoder();
 const [left,right]=await Promise.all([supplied,expected].map(value=>crypto.subtle.digest('SHA-256',encoder.encode(value))));
 const a=new Uint8Array(left),b=new Uint8Array(right);let difference=0;
 for(let i=0;i<a.length;i++)difference|=a[i]^b[i];
 return difference===0;
}
