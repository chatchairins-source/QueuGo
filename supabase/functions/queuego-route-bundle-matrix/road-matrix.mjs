// Routing adapter for QueueGo's existing Longdo provider. No geometry/ETA guesses on failure.
export const validPoint=p=>p&&Number.isFinite(p.lat)&&Number.isFinite(p.lng)&&Math.abs(p.lat)<=90&&Math.abs(p.lng)<=180;
export function parseRoadLeg(body){
 if(!body||Number(body.meta?.status??200)!==200||!Array.isArray(body.features)||!body.features.length)throw Error('routing unavailable');
 const sections=body.features.map(f=>f.properties??{});
 const hasGeometry=body.features.some(f=>['LineString','MultiLineString'].includes(f.geometry?.type)&&Array.isArray(f.geometry?.coordinates)&&f.geometry.coordinates.length>0);
 if(!hasGeometry)throw Error('road geometry missing');
 const distances=sections.map(s=>s.distance),durations=sections.map(s=>s.interval);
 const positive=n=>typeof n==='number'&&Number.isFinite(n)&&n>0;
 const nonnegative=n=>typeof n==='number'&&Number.isFinite(n)&&n>=0;
 const meters=positive(body.data?.distance)?body.data.distance:distances.every(nonnegative)?distances.reduce((a,b)=>a+b,0):NaN;
 const seconds=positive(body.data?.interval)?body.data.interval:durations.every(nonnegative)?durations.reduce((a,b)=>a+b,0):NaN;
 if(!positive(meters)||!positive(seconds)||meters>1_000_000||seconds>86_400)throw Error('verified road distance and duration required');
 return {km:meters/1000,minutes:seconds/60};
}
export async function buildRoadMatrix(points,key,{fetcher=fetch,signal}={}){
 if(typeof key!=='string'||!key)throw Error('routing not configured');
 if(Object.keys(points).length!==5||!Object.values(points).every(validPoint))throw Error('invalid route points');
 const tasks=Object.entries(points).flatMap(([a,from])=>Object.entries(points).filter(([b])=>b!==a).map(([b,to])=>({id:a+'|'+b,from,to})));
 const edges={};let cursor=0;
 await Promise.all(Array.from({length:3},async()=>{
  while(cursor<tasks.length){
   const {id,from,to}=tasks[cursor++];
   signal?.throwIfAborted();
   if(from.lat===to.lat&&from.lng===to.lng){edges[id]={km:0,minutes:0};continue;}
   const url=new URL('https://api.longdo.com/RouteService/geojson/route');
   for(const [name,value] of Object.entries({flon:from.lng,flat:from.lat,tlon:to.lng,tlat:to.lat,type:1,locale:'th',restrict:1,key}))url.searchParams.set(name,String(value));
   const response=await fetcher(url,{signal:signal?AbortSignal.any([signal,AbortSignal.timeout(12000)]):AbortSignal.timeout(12000)});
   if(!response.ok)throw Error('routing unavailable');
   const raw=await response.text();if(raw.length>4_000_000)throw Error('routing response too large');
   edges[id]=parseRoadLeg(JSON.parse(raw));
  }
 }));
 return edges;
}
