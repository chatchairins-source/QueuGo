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
function readyMinutes(input,orderId){
 const at=input?.financialOrders?.[orderId]?.merchantReadyEstimateAt;
 const base=input?.financialEvaluatedAt;
 if(!at||!base)return 0;
 const wait=(Date.parse(at)-Date.parse(base))/60000;
 return Number.isFinite(wait)&&wait>0?wait:0;
}
function routeScore(sequence,edges,input){
 let from='R',km=0,minutes=0;
 const finishMinutesByKey={};
 const operational=Boolean(input?.financialOrders&&input?.financialEvaluatedAt);
 for(const to of sequence){
  const leg=edge(edges,from,to);km+=leg.km;minutes+=leg.minutes;
  const point=input?.pointMap?.[to];
  if(operational&&point?.kind==='pickup')minutes=Math.max(minutes,readyMinutes(input,point.orderId));
  if(operational)minutes+=2;
  finishMinutesByKey[to]=minutes;
  from=to;
 }
 return {km,minutes,key:sequence.join('>'),finishMinutesByKey};
}
function better(a,b){
 if(!b)return true;
 if(a.minutes!==b.minutes)return a.minutes<b.minutes;
 if(a.km!==b.km)return a.km<b.km;
 return a.key<b.key;
}
function planOrders(input,selectedOrderIds,minOrders){
 const orderIds=selectedOrderIds,pointMap=input?.pointMap,edges=input?.edges,orders=input?.orders;
 if(!Array.isArray(orderIds)||orderIds.length<minOrders||orderIds.length>5||new Set(orderIds).size!==orderIds.length)throw Error('invalid stack orders');
 if(!pointMap||typeof pointMap!=='object'||pointMap.R?.kind!=='rider'||!edges||typeof edges!=='object'||!orders||typeof orders!=='object')throw Error('invalid stack evidence');
 const bundleId=input.bundleId??null,routeRevision=input.routeRevision??null;
 const committedPrefix=input.committedPrefix??[],lockedNext=input.lockedNext??null,lockedNextStop=input.lockedNextStop??null;
 if(!Array.isArray(committedPrefix)||
    (bundleId!==null&&(!Number.isSafeInteger(routeRevision)||routeRevision<0||typeof lockedNext!=='string'||!lockedNextStop))||
    (lockedNext!==null&&(!pointMap[lockedNext]||pointMap[lockedNext].orderId!==lockedNextStop?.order_id||pointMap[lockedNext].kind!==lockedNextStop?.kind)))throw Error('invalid stack commitment');
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
 const selectedKeys=new Set([...pickupKeys,...dropKeys]);
 if(lockedNext!==null&&!selectedKeys.has(lockedNext))throw Error('locked next outside selected orders');
 let best=null,bestSequence=null;
 for(const pickups of permutations(pickupKeys))for(const drops of permutations(dropKeys)){
  const sequence=pickups.concat(drops);
  if(lockedNext!==null&&sequence[0]!==lockedNext)continue;
  const score=routeScore(sequence,edges,input);
  if(better(score,best)){best=score;bestSequence=sequence;}
 }
 if(!best||!bestSequence)throw Error('route plan unavailable');
 const stops=bestSequence.map((key,index)=>{
  const point=pointMap[key];
  return {rank:index+1,key,orderId:point.orderId,kind:point.kind};
 });
 return {
  bundleId,routeRevision,
  committedPrefix:committedPrefix.map(stop=>({...stop})),
  lockedNext,lockedNextStop:lockedNextStop?{...lockedNextStop}:null,
  orders:orderIds.length,
  pickupStops:pickupKeys.length,
  dropoffStops:dropKeys.length,
  stops,
  finishMinutesByKey:{...best.finishMinutesByKey},
  totalKm:Math.round(best.km*1000)/1000,
  totalMinutes:Math.round(best.minutes*100)/100,
  policy:'all-pickups-before-dropoffs-v1'
 };
}
export function planStackRoute(input){
 const orderIds=input?.orderIds;
 if(!Array.isArray(orderIds)||orderIds.length<2||orderIds.length>5)throw Error('invalid stack orders');
 return planOrders(input,orderIds,2);
}
export function planExistingStackRoute(input){
 const candidate=input?.candidateOrderId;
 if(typeof candidate!=='string')throw Error('invalid candidate order');
 const orderIds=input?.orderIds?.filter(orderId=>orderId!==candidate);
 if(!Array.isArray(orderIds)||orderIds.length<1||orderIds.length>4)throw Error('invalid existing stack orders');
 return planOrders(input,orderIds,1);
}
