// Pure read-only planner for a short-lived exact-revision stack matrix.
// It never assigns Orders, changes prices, calls the provider or writes cache state.
const ACTIVE=new Set(['searching_rider','rider_assigned','preparing','ready','assigned','picked_up','in_progress']);
const PICKUP_DONE=new Set(['picked_up','in_progress']);

function permutations(values){
 if(values.length<2)return [values.slice()];
 const out=[];
 values.forEach((value,index)=>{
  const rest=values.slice(0,index).concat(values.slice(index+1));
  for(const tail of permutations(rest))out.push([value,...tail]);
 });
 return out;
}
function edge(edges,from,to){
 const value=edges?.[from+'|'+to];
 const km=Number(value?.km),minutes=Number(value?.minutes);
 if(!Number.isFinite(km)||km<0||!Number.isFinite(minutes)||minutes<0)throw Error('incomplete road matrix');
 return {km,minutes};
}
function routeScore(sequence,edges){
 let from='R',km=0,minutes=0;
 for(const to of sequence){
  const leg=edge(edges,from,to);km+=leg.km;minutes+=leg.minutes;from=to;
 }
 return {km,minutes,key:sequence.join('>')};
}
function better(a,b){
 if(!b)return true;
 if(a.minutes!==b.minutes)return a.minutes<b.minutes;
 if(a.km!==b.km)return a.km<b.km;
 return a.key<b.key;
}
export function planStackRoute(input){
 const orderIds=input?.orderIds,pointMap=input?.pointMap,edges=input?.edges,orders=input?.orders;
 if(!Array.isArray(orderIds)||orderIds.length<2||orderIds.length>5||new Set(orderIds).size!==orderIds.length)throw Error('invalid stack orders');
 if(!pointMap||typeof pointMap!=='object'||pointMap.R?.kind!=='rider'||!edges||typeof edges!=='object'||!orders||typeof orders!=='object')throw Error('invalid stack evidence');
 const entries=Object.entries(pointMap);
 const mapped=orderIds.map(orderId=>{
  const status=orders[orderId]?.status;
  if(!ACTIVE.has(status))throw Error('invalid stack order state');
  const pickups=entries.filter(([,v])=>v?.orderId===orderId&&v?.kind==='pickup').map(([key])=>key);
  const drops=entries.filter(([,v])=>v?.orderId===orderId&&v?.kind==='dropoff').map(([key])=>key);
  if(pickups.length!==1||drops.length!==1)throw Error('invalid stack point map');
  return {orderId,status,pickup:pickups[0],dropoff:drops[0]};
 });
 const pickupKeys=mapped.filter(o=>!PICKUP_DONE.has(o.status)).map(o=>o.pickup);
 const dropKeys=mapped.map(o=>o.dropoff);
 let best=null,bestSequence=null;
 for(const pickups of permutations(pickupKeys))for(const drops of permutations(dropKeys)){
  const sequence=pickups.concat(drops),score=routeScore(sequence,edges);
  if(better(score,best)){best=score;bestSequence=sequence;}
 }
 if(!best||!bestSequence)throw Error('route plan unavailable');
 const stops=bestSequence.map((key,index)=>{
  const point=pointMap[key];
  return {rank:index+1,key,orderId:point.orderId,kind:point.kind};
 });
 return {
  orders:orderIds.length,
  pickupStops:pickupKeys.length,
  dropoffStops:dropKeys.length,
  stops,
  totalKm:Math.round(best.km*1000)/1000,
  totalMinutes:Math.round(best.minutes*100)/100,
  policy:'all-pickups-before-dropoffs-v1'
 };
}
