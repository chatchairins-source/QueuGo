import assert from 'node:assert/strict';
import {
  simulateStackSavingsGroupWeightedV1 as sim,
  STACK_FINANCIAL_POLICY_VERSION as POLICY
} from '../supabase/functions/queuego-route-bundle-matrix/stack-financial-simulator.mjs';

let checks=0; const eq=(a,b)=>{assert.deepEqual(a,b);checks++;};

const pair=sim({
 existingOrders:[{orderId:'A',currentFeeSatang:5000,lockedRiderPayoutSatang:5000}],
 candidate:{orderId:'B',currentFeeSatang:4000},
 incrementalCompensationSatang:2000,
 existingGroupSavingsPercent:50
});
eq(pair.policyVersion,POLICY);
eq(pair.eligible,true);
eq(pair.allocations.map(x=>[x.orderId,x.customerSavingSatang,x.proposedFeeSatang,x.riderPayoutSatang]),
   [['A',1000,4000,5000],['B',1000,3000,2000]]);
eq(pair.proposedCollectionsSatang,7000);
eq(pair.riderPayoutTotalSatang,7000);
eq(pair.fundingGapSatang,0);

const group=sim({
 existingOrders:[
  {orderId:'A',currentFeeSatang:4000,lockedRiderPayoutSatang:4000},
  {orderId:'B',currentFeeSatang:3000,lockedRiderPayoutSatang:3000},
  {orderId:'C',currentFeeSatang:2000,lockedRiderPayoutSatang:2000},
 ],
 candidate:{orderId:'D',currentFeeSatang:4000},
 incrementalCompensationSatang:2000,
 existingGroupSavingsPercent:'50.00'
});
eq(group.eligible,true);
eq(group.allocations.map(x=>x.customerSavingSatang),[445,333,222,1000]);
eq(group.proposedCollectionsSatang,11000);
eq(group.riderPayoutTotalSatang,11000);
eq(group.totalCustomerSavingSatang,2000);

const gap=sim({
 existingOrders:[
  {orderId:'A',currentFeeSatang:4000,lockedRiderPayoutSatang:5000},
  {orderId:'B',currentFeeSatang:3000,lockedRiderPayoutSatang:4000},
  {orderId:'C',currentFeeSatang:2000,lockedRiderPayoutSatang:2000},
 ],
 candidate:{orderId:'D',currentFeeSatang:4000},
 incrementalCompensationSatang:2000,
 existingGroupSavingsPercent:50
});
eq(gap.eligible,false);
eq(gap.fundingGapSatang,2000);
eq(gap.reason,'preserved rider payouts are not fully funded');

const tie=sim({
 existingOrders:[
  {orderId:'B',currentFeeSatang:100,lockedRiderPayoutSatang:50},
  {orderId:'A',currentFeeSatang:100,lockedRiderPayoutSatang:50},
  {orderId:'C',currentFeeSatang:100,lockedRiderPayoutSatang:50},
 ],
 candidate:{orderId:'D',currentFeeSatang:103},
 incrementalCompensationSatang:1,
 existingGroupSavingsPercent:52
});
const tieSavings=Object.fromEntries(
  tie.allocations.filter(x=>x.role==='existing').map(x=>[x.orderId,x.customerSavingSatang])
);
eq(tieSavings,{A:18,B:18,C:17});

const noSaving=sim({
 existingOrders:[{orderId:'A',currentFeeSatang:1000,lockedRiderPayoutSatang:1000}],
 candidate:{orderId:'B',currentFeeSatang:500},
 incrementalCompensationSatang:500,
 existingGroupSavingsPercent:50
});
eq(noSaving.eligible,false);
eq(noSaving.reason,'candidate has no positive saving');

assert.throws(()=>sim({
 existingOrders:[{orderId:'A',currentFeeSatang:1.5,lockedRiderPayoutSatang:1}],
 candidate:{orderId:'B',currentFeeSatang:100},
 incrementalCompensationSatang:1,
 existingGroupSavingsPercent:50
}),/invalid current fee/);checks++;

assert.throws(()=>sim({
 existingOrders:[{orderId:'A',currentFeeSatang:100,lockedRiderPayoutSatang:100}],
 candidate:{orderId:'B',currentFeeSatang:100},
 incrementalCompensationSatang:1,
 existingGroupSavingsPercent:'50.001'
}),/invalid existing group savings percent/);checks++;

console.log(JSON.stringify({
 checks,failures:0,policy:POLICY,
 scope:'read-only deterministic satang allocation; preserves locked Rider payouts; no bonus; unfunded stacks rejected'
}));
