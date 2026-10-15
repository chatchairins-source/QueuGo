const assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const source=fs.readFileSync('rider/index.html','utf8');
const start=source.indexOf('function qgBundlePlannedStops('),end=source.indexOf('async function qgLoadLaundryRiderState(',start);
const context=vm.createContext({S:{bundleOrders:[]},statusFromRow:o=>o.status});vm.runInContext(source.slice(start,end),context);
const order=(id,status,pickup,drop)=>({id,status,_bundlePlanVersion:1,_bundlePickupStopRank:pickup,_bundleDropoffStopRank:drop});
let checks=0;const eq=(a,b)=>{assert.deepEqual(a,b);checks++};
context.S.bundleOrders=[order('A','picked_up',1,3),order('B','ready',2,5),order('C','ready',4,6)];
eq(context.qgBundleNextOrder().id,'B');context.S.bundleOrders[1].status='picked_up';eq(context.qgBundleNextOrder().id,'A');
eq(Array.from(context.qgBundlePlannedStops(),s=>s.rank),[3,4,5,6]);
context.S.bundleOrders[0].status='completed';eq(context.qgBundleNextOrder().id,'C');
context.S.bundleOrders[1].status='cancelled';eq(Array.from(context.qgBundlePlannedStops(),s=>s.order.id),['C','C']);
context.S.bundleOrders=[order('A','ready',2,1)];eq(context.qgBundleNextOrder(),null); // malformed plan cannot pick an arbitrary order
context.S.bundleOrders=[order('A','ready',1,3),order('B','ready',1,4)];eq(context.qgBundleNextOrder(),null);
context.S.bundleOrders=Array.from({length:5},(_,i)=>order('QT-'+i,'ready',i+1,i+6));eq(context.qgBundlePlannedStops().length,10);
eq(new Set(context.qgBundlePlannedStops().map(s=>s.order.id)).size,5);
context.S.bundleOrders=[{id:'legacy',status:'ready',route_bundle_sequence:1}];eq(context.qgBundlePlannedStops(),null);eq(context.qgBundleNextOrder().id,'legacy');
console.log(JSON.stringify({checks,failures:0,scope:'Web authoritative next-stop selection, interleaving/cancellation, invalid plans and legacy compatibility'}));
