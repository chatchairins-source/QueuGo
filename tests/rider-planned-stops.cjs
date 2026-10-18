const assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const source=fs.readFileSync('rider/index.html','utf8');
const start=source.indexOf('function qgBundleNextOrder('),end=source.indexOf('async function qgLoadLaundryRiderState(',start);
assert(start>=0&&end>start,'current Rider bundle-next selector must be locatable');
const context=vm.createContext({S:{bundleOrders:[]},statusFromRow:o=>o.status});
vm.runInContext(source.slice(start,end),context);
const order=(id,status,sequence,dropRank)=>({id,status,route_bundle_sequence:sequence,_bundleDropRank:dropRank});
let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};

context.S.bundleOrders=[
  order('A','picked_up',1,3),
  order('B','ready',2,5),
  order('C','ready',4,6)
];
eq(context.qgBundleNextOrder().id,'B');

context.S.bundleOrders[1].status='picked_up';
eq(context.qgBundleNextOrder().id,'C');

context.S.bundleOrders[2].status='picked_up';
eq(context.qgBundleNextOrder().id,'A');

context.S.bundleOrders=[
  order('A','picked_up',1,5),
  order('B','picked_up',2,3),
  order('C','picked_up',3,4)
];
eq(context.qgBundleNextOrder().id,'B');

context.S.bundleOrders=[
  order('A','ready',3,8),
  order('B','in_progress',9,9),
  order('C','ready',1,7)
];
eq(context.qgBundleNextOrder().id,'B');

context.S.bundleOrders=[
  order('A','cancelled',1,1),
  order('B','completed',2,2),
  order('C','ready',3,3)
];
eq(context.qgBundleNextOrder().id,'C');

context.S.bundleOrders=[
  order('legacy-a','ready',1,null),
  order('legacy-b','ready',2,null)
];
eq(context.qgBundleNextOrder().id,'legacy-a');

context.S.bundleOrders=[];
eq(context.qgBundleNextOrder(),null);

console.log(JSON.stringify({checks,failures:0,scope:'Web current route-bundle next-order selection, cancellation/completion filtering, pickup sequence and drop-rank ordering'}));
