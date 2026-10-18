import {simulateStackSavingsGroupWeightedV1,STACK_FINANCIAL_POLICY_VERSION} from './stack-financial-simulator.mjs';
import {planExistingStackRoute} from './stack-planner.mjs';

function integer(value,label){if(!Number.isSafeInteger(value)||value<0)throw Error('invalid '+label);return value;}
function edgeMinutes(edges,from,to){const m=Number(edges?.[from+'|'+to]?.minutes);if(!Number.isFinite(m)||m<0)throw Error('incomplete road matrix');return m;}
function pointsFor(input,orderId){
 const pairs=Object.entries(input.pointMap).filter(([,v])=>v?.orderId===orderId);
 const pickup=pairs.find(([,v])=>v.kind==='pickup')?.[0],dropoff=pairs.find(([,v])=>v.kind==='dropoff')?.[0];
 if(!pickup||!dropoff)throw Error('invalid stack point map');
 return {pickup,dropoff};
}
function ceilDistanceCost(rateSatangPerKm,meters){
 return Number((BigInt(rateSatangPerKm)*BigInt(meters)+999n)/1000n);
}
export function simulateStackFinancialPlan(evidence,routePlan){
 if(evidence?.financialPolicyVersion!==STACK_FINANCIAL_POLICY_VERSION)throw Error('unsupported financial policy');
 const ids=evidence?.orderIds,candidateId=evidence?.candidateOrderId,rules=evidence?.financialRules,fin=evidence?.financialOrders;
 if(!Array.isArray(ids)||ids.length<3||ids.length>5||!ids.includes(candidateId))throw Error('generalized finance requires 3-5 orders');
 if(!rules||!fin||!routePlan||!Array.isArray(routePlan.stops))throw Error('invalid financial route evidence');
 const fullKeys=routePlan.stops.map(s=>s.key),fullFinish=routePlan.finishMinutesByKey||{};
 const baseline=planExistingStackRoute(evidence);
 const detourMeters=Math.max(Math.round((Number(routePlan.totalKm)-Number(baseline.totalKm))*1000),0);
 const candidatePoints=pointsFor(evidence,candidateId);
 const candidateRide=Math.max(
   (Number(fullFinish[candidatePoints.dropoff])-Number(fullFinish[candidatePoints.pickup]))-
   (edgeMinutes(evidence.edges,candidatePoints.pickup,candidatePoints.dropoff)+2),
   0
 );
 const existingIds=ids.filter(id=>id!==candidateId);
 const existingDelays=existingIds.map(id=>{
  const {dropoff}=pointsFor(evidence,id);
  return Math.max(Number(fullFinish[dropoff])-Number(baseline.finishMinutesByKey?.[dropoff]),0);
 });
 if([candidateRide,...existingDelays].some(v=>!Number.isFinite(v)))throw Error('missing operational route timing');
 const maxDelayMinutes=Math.max(candidateRide,...existingDelays,0);
 const addedMinutes=Math.ceil(maxDelayMinutes);
 const observedDelaySeconds=Math.ceil(maxDelayMinutes*60);
 const maxDetourMeters=integer(rules.maxDetourMeters,'max detour');
 const maxDelaySeconds=integer(rules.maxDelaySeconds,'max delay');
 const route={detourMeters,observedDelaySeconds,maxDetourMeters,maxDelaySeconds,addedMinutes,fullKeys,baselineKeys:baseline.stops.map(s=>s.key),existingDelayMinutes:existingDelays,candidateRideExtraMinutes:candidateRide};
 if(detourMeters>maxDetourMeters)return {policyVersion:STACK_FINANCIAL_POLICY_VERSION,eligible:false,reason:'route detour exceeds policy',route};
 if(observedDelaySeconds>maxDelaySeconds)return {policyVersion:STACK_FINANCIAL_POLICY_VERSION,eligible:false,reason:'route delay exceeds policy',route};
 const rate=integer(rules.incrementalKmRateSatangPerKm,'incremental km rate');
 const pickup=integer(rules.pickupCostSatang,'pickup cost'),drop=integer(rules.dropoffCostSatang,'dropoff cost'),minute=integer(rules.minuteRateSatangPerMinute,'minute rate');
 const incrementalCompensationSatang=ceilDistanceCost(rate,detourMeters)+pickup+drop+minute*addedMinutes;
 const existingOrders=existingIds.map(orderId=>({
   orderId,
   currentFeeSatang:integer(fin[orderId]?.currentFeeSatang,'current fee'),
   lockedRiderPayoutSatang:integer(fin[orderId]?.lockedRiderPayoutSatang,'locked payout')
 }));
 const candidate={orderId:candidateId,currentFeeSatang:integer(fin[candidateId]?.currentFeeSatang,'candidate current fee')};
 const allocation=simulateStackSavingsGroupWeightedV1({
   existingOrders,candidate,incrementalCompensationSatang,
   existingGroupSavingsPercent:rules.existingGroupSavingsPercent
 });
 return {...allocation,route:{...route,incrementalCompensationSatang}};
}
