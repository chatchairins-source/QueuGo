const assert=require('assert');
const fs=require('fs'),path=require('path');
const {
  SOURCE_KEY,SOURCE_LICENSE,parseOperationalSnapshot,renderOperationalPlacesSql,shopCategory
}=require('../scripts/import-queuego-verified-places.cjs');

let checks=0;
const ok=(value,message)=>{assert.ok(value,message);checks++};
const eq=(actual,expected,message)=>{assert.deepStrictEqual(actual,expected,message);checks++};
const uuid=n=>`00000000-0000-4000-8000-${String(n).padStart(12,'0')}`;

const input={
  snapshot_complete:true,
  generated_at:'2026-10-24T03:40:00Z',
  shops:[
    {id:uuid(1),shop_name:'ร้านข้าวบุรีรัมย์',public_category:'food',status:'active',onboarding_status:'approved',archived_at:null,address:'1 ถนนทดสอบ',subdistrict:'ในเมือง',district:'เมืองบุรีรัมย์',province:'บุรีรัมย์',postcode:'31000',latitude:14.99,longitude:103.10,updated_at:'2026-10-24T03:30:00Z'},
    {id:uuid(2),shop_name:'คาเฟ่คิวโก',public_category:'cafe',status:'active',onboarding_status:'approved',archived_at:null,address:'2 ถนนทดสอบ',province:'เชียงใหม่',latitude:18.79,longitude:98.98},
    {id:uuid(3),shop_name:'ร้านของชำไม่มีพิกัด',public_category:'grocery',status:'active',onboarding_status:'approved',archived_at:null,province:'อุบลราชธานี'},
    {id:uuid(4),shop_name:'ร้านรออนุมัติ',public_category:'food',status:'pending',onboarding_status:'pending',archived_at:null,latitude:14.9,longitude:103.1},
    {id:uuid(5),shop_name:'ร้านตลาดยังไม่ยืนยัน',public_category:'market',status:'active',onboarding_status:'approved',market_membership_status:'pending',archived_at:null,latitude:14.9,longitude:103.1}
  ],
  markets:[
    {id:uuid(11),name:'ตลาดสดทดสอบ',active:true,verified:true,address:'ถนนตลาด',subdistrict:'ในเมือง',district:'เมือง',province:'ขอนแก่น',latitude:16.43,longitude:102.83,updated_at:'2026-10-24T03:20:00Z'},
    {id:uuid(12),name:'ตลาดยังไม่ตรวจ',active:true,verified:false,latitude:16.4,longitude:102.8}
  ]
};

const snapshot=parseOperationalSnapshot(JSON.stringify(input));
eq(snapshot.rows.length,4,'three verified shops plus one verified market must import');
eq(snapshot.summary.inactive_or_unapproved_shops,2,'pending and unverified-market-member shops must be excluded');
eq(snapshot.summary.inactive_or_unverified_markets,1,'unverified market must be excluded');
eq(snapshot.summary.without_coordinates,1,'approved shop without coordinates must remain searchable and be reported');
ok(/^[a-f0-9]{64}$/.test(snapshot.snapshotHash),'complete public snapshot must have a deterministic SHA-256 fingerprint');
eq(snapshot.rows.map(x=>x.source_record_id),[
  'market:'+uuid(11),'shop:'+uuid(1),'shop:'+uuid(2),'shop:'+uuid(3)
],'records must sort deterministically by stable source identity');

const food=snapshot.rows.find(x=>x.source_record_id==='shop:'+uuid(1));
const cafe=snapshot.rows.find(x=>x.source_record_id==='shop:'+uuid(2));
const grocery=snapshot.rows.find(x=>x.source_record_id==='shop:'+uuid(3));
const market=snapshot.rows.find(x=>x.source_record_id==='market:'+uuid(11));
eq([food.category,food.subcategory],['food','restaurant'],'food shops must map to food/restaurant');
eq([cafe.category,cafe.subcategory],['food','cafe'],'cafes must share food nearby category while retaining cafe subtype');
eq([grocery.category,grocery.subcategory],['grocery','grocery'],'grocery category must be preserved');
eq([market.category,market.subcategory],['market','fresh_market'],'verified markets must use market/fresh_market');
eq(grocery.latitude,null,'coordinate-less verified places must retain null coordinates');
ok(/^[a-f0-9]{64}$/.test(food.source_payload_hash),'each normalized public record must have a deterministic payload hash');
eq(shopCategory('laundry'),{category:'laundry',subcategory:'laundry'},'Laundry category mapping must remain explicit');

const exportSql=fs.readFileSync(path.join(__dirname,'../scripts/export-queuego-verified-places.sql'),'utf8');
ok(exportSql.includes("'snapshot_complete', true"),'read-only exporter must mark the snapshot complete explicitly');
for(const publicField of ['shop_name','public_category','status','onboarding_status','archived_at','market_membership_status','address','latitude','longitude','verified','province'])
  ok(exportSql.includes("'"+publicField+"'"),'read-only exporter must include public field '+publicField);
ok(!/\b(phone|email|user_id|auth_user_id|owner_name|contact_name|national_id)\b/i.test(exportSql),'read-only exporter must not select direct account/contact PII');
ok(!/\b(update|insert|delete|truncate|alter|drop)\b\s+(public\.|into|from|table)/i.test(exportSql),'operational snapshot exporter must remain read-only');

const sql=renderOperationalPlacesSql(snapshot);
ok(sql.includes("'"+SOURCE_KEY+"'"),'SQL must use QueueGo verified source authority');
ok(sql.includes("'"+SOURCE_LICENSE+"'"),'SQL must record QueueGo internal source license');
ok(sql.includes("ON CONFLICT(dedupe_key) WHERE dedupe_key IS NOT NULL"),'operational records must upsert by stable QueueGo dedupe identity');
ok(sql.includes("queuego_place_source_records"),'source-record provenance must be recorded');
ok(sql.includes("queuego_place_field_provenance"),'field-level provenance must be recorded');
ok(sql.includes("queuego_operational_snapshot_absent"),'complete snapshots must retire stale operational places');
ok(sql.includes("p.source_id LIKE 'shop:%' OR p.source_id LIKE 'market:%'"),'stale reconciliation must be scoped only to operational shop/market records');
ok(!/phone|email|user_id|auth_user_id|owner_name|contact_name/i.test(sql),'generated search SQL must not contain direct contact/identity PII fields');
ok(sql.includes("last_operational_snapshot_at"),'source metadata must record the operational snapshot timestamp');
ok(sql.includes("last_operational_snapshot_count"),'source metadata must record the imported row count');
ok(sql.includes("last_operational_snapshot_sha256")&&sql.includes(snapshot.snapshotHash),'source metadata must retain the exact public snapshot fingerprint');

assert.throws(()=>parseOperationalSnapshot(JSON.stringify({...input,snapshot_complete:false})),/snapshot_complete=true/);checks++;
assert.throws(()=>parseOperationalSnapshot(JSON.stringify({...input,shops:[{...input.shops[0],phone:'0812345678'}]})),/prohibited sensitive field phone/);checks++;
assert.throws(()=>parseOperationalSnapshot(JSON.stringify({...input,shops:[{...input.shops[0],latitude:99,longitude:103}]})),/outside Thailand bounds/);checks++;
assert.throws(()=>parseOperationalSnapshot(JSON.stringify({...input,shops:[{...input.shops[0],longitude:null}]})),/incomplete coordinate pair/);checks++;
assert.throws(()=>parseOperationalSnapshot(JSON.stringify({...input,shops:[{...input.shops[0],id:'not-a-uuid'}]})),/shop id must be a UUID/);checks++;
assert.throws(()=>parseOperationalSnapshot(JSON.stringify({...input,markets:[{...input.markets[0],email:'market@example.com'}]})),/prohibited sensitive field email/);checks++;

console.log(JSON.stringify({checks,failures:0,scope:'QueueGo-owned verified shop/market Places ingestion, complete-snapshot stale reconciliation, category mapping, provenance and PII exclusion'}));
