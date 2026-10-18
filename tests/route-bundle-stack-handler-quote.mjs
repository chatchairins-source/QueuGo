import assert from 'node:assert/strict';
import {createRouteBundleMatrixHandler} from '../supabase/functions/queuego-route-bundle-matrix/handler.mjs';

let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
const A='00000000-0000-4000-8000-000000000070';
const C='00000000-0000-4000-8000-000000000071';
const D='00000000-0000-4000-8000-000000000072';
const quoteId='00000000-0000-4000-8000-000000000999';
const pointMap={R:{kind:'rider'},O0P:{orderId:A,kind:'pickup'},O0D:{orderId:A,kind:'dropoff'},O1P:{orderId:C,kind:'pickup'},O1D:{orderId:C,kind:'dropoff'},O2P:{orderId:D,kind:'pickup'},O2D:{orderId:D,kind:'dropoff'}};
const keys=Object.keys(pointMap),edges={};for(const x of keys)for(const y of keys)if(x!==y)edges[x+'|'+y]={km:.5,minutes:1};
const evidence={
 bundleId:'00000000-0000-4000-8000-000000000080',routeRevision:7,committedPrefix:[],lockedNext:null,lockedNextStop:null,
 primaryOrderId:A,candidateOrderId:D,riderId:'00000000-0000-4000-8000-000000000011',
 orderRevisions:{[A]:'2026-10-18T00:00:00Z',[C]:'2026-10-18T00:00:00Z',[D]:'2026-10-18T00:00:00Z'},
 orderIds:[A,C,D],pointMap,edges,orders:{[A]:{status:'ready'},[C]:{status:'ready'},[D]:{status:'searching_rider'}},
 financialPolicyVersion:'stack-savings-group-weighted-v1',financialEvaluatedAt:'2026-10-18T00:00:00Z',
 financialOrders:{
  [A]:{currentFeeSatang:5000,lockedRiderPayoutSatang:5000,merchantReadyEstimateAt:'2026-10-18T00:00:00Z',shopCategory:'grocery'},
  [C]:{currentFeeSatang:3000,lockedRiderPayoutSatang:3000,merchantReadyEstimateAt:'2026-10-18T00:00:00Z',shopCategory:'grocery'},
  [D]:{currentFeeSatang:4000,lockedRiderPayoutSatang:null,merchantReadyEstimateAt:'2026-10-18T00:00:00Z',shopCategory:'grocery'}
 },
 financialRules:{existingGroupSavingsPercent:50,incrementalKmRateSatangPerKm:500,pickupCostSatang:200,dropoffCostSatang:200,minuteRateSatangPerMinute:100,maxDetourMeters:1500,maxDelaySeconds:600,maxPickupWaitSeconds:300},
 expiresAt:'2026-10-18T00:01:00Z'
};
let cacheCalls=0,inputCalls=0;
const fetcher=async(url,options)=>{
 const u=new URL(url);
 if(u.pathname==='/rest/v1/rpc/queuego_route_bundle_stack_financial_input'){
  inputCalls++;return {ok:true,json:async()=>structuredClone(evidence)};
 }
 if(u.pathname==='/rest/v1/rpc/queuego_cache_route_bundle_stack_quote'){
  cacheCalls++;const body=JSON.parse(options.body);
  eq(body.p_primary_order_id,A);eq(body.p_candidate_order_id,D);
  eq(body.p_stop_plan.map(x=>[x.order_id,x.kind]),[
   [A,'pickup'],[C,'pickup'],[D,'pickup'],[A,'dropoff'],[C,'dropoff'],[D,'dropoff']
  ]);
  eq(body.p_financial.eligible,true);
  return {ok:true,json:async()=>quoteId};
 }
 throw Error('unexpected '+u.pathname);
};
const env={SUPABASE_SERVICE_ROLE_KEY:'svc',SUPABASE_URL:'https://backend.invalid',QUEUEGO_LONGDO_KEY:''};
const handler=createRouteBundleMatrixHandler({get:k=>env[k]},fetcher);
const req=action=>new Request('https://edge.invalid',{method:'POST',headers:{authorization:'Bearer svc','content-type':'application/json'},body:JSON.stringify({action,primaryOrderId:A,candidateOrderId:D})});

const planned=await handler(req('plan'));eq(planned.status,200);
const plannedBody=await planned.json();eq(plannedBody.readOnly,true);eq(plannedBody.financial.eligible,true);eq(cacheCalls,0);

const quoted=await handler(req('quote'));eq(quoted.status,200);
const quoteBody=await quoted.json();eq(quoteBody.quoteCached,true);eq(quoteBody.readOnlyBusinessState,true);eq(quoteBody.quoteId,quoteId);eq(cacheCalls,1);eq(inputCalls,2);

const late=structuredClone(evidence);late.financialOrders[D].merchantReadyEstimateAt='2026-10-18T00:20:00Z';
const lateFetcher=async(url,options)=>{
 const u=new URL(url);
 if(u.pathname.endsWith('queuego_route_bundle_stack_financial_input'))return {ok:true,json:async()=>late};
 if(u.pathname.endsWith('queuego_cache_route_bundle_stack_quote'))throw Error('ineligible quote must not be cached');
 throw Error('unexpected');
};
const lateHandler=createRouteBundleMatrixHandler({get:k=>env[k]},lateFetcher);
const rejected=await lateHandler(req('quote'));eq(rejected.status,409);
const rejectedBody=await rejected.json();eq(rejectedBody.quoteCached,false);eq(rejectedBody.financial.reason,'merchant readiness wait exceeds policy');

console.log(JSON.stringify({checks,failures:0,scope:'service quote action caches only eligible 3-5 plan; plan stays read-only; two-order authority unchanged'}));
