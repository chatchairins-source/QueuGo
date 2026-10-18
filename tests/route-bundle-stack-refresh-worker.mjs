import assert from 'node:assert/strict';
import {refreshRouteMatrices} from '../supabase/functions/queuego-route-bundle-matrix/refresh.mjs';
let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
const calls=[],produced=[];
const fetcher=async(url,options)=>{
 const name=new URL(url).pathname.split('/').pop();calls.push(name);
 if(name==='queuego_route_matrix_refresh_claim')return {ok:true,json:async()=>({})};
 if(name==='queuego_route_bundle_stack_refresh_claim')return {ok:true,json:async()=>({leaseId:'00000000-0000-4000-8000-000000000090',pairs:[{action:'stack',quoteAfterCache:true,primaryOrderId:'00000000-0000-4000-8000-000000000070',candidateOrderId:'00000000-0000-4000-8000-000000000072'}]})};
 if(name==='queuego_route_matrix_refresh_release')return {ok:true,json:async()=>true};
 throw Error('unexpected RPC '+name);
};
const result=await refreshRouteMatrices('https://backend.invalid',{},fetcher,async pair=>{produced.push(pair);return new Response('{}',{status:200});},{includeStack:true});
eq(result,{refreshed:1,failed:0,busyOrDisabled:false});
eq(produced,[{action:'stack',quoteAfterCache:true,primaryOrderId:'00000000-0000-4000-8000-000000000070',candidateOrderId:'00000000-0000-4000-8000-000000000072'}]);
eq(calls,['queuego_route_matrix_refresh_claim','queuego_route_bundle_stack_refresh_claim','queuego_route_matrix_refresh_release']);
console.log(JSON.stringify({checks,failures:0,scope:'existing bounded refresh worker processes generalized stack discovery through the same lease/release authority'}));
