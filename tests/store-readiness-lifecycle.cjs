const fs=require('fs'),path=require('path'),assert=require('assert');
const read=p=>fs.readFileSync(path.resolve(__dirname,'..',p),'utf8');
const merchant=read('merchant/index.html'),admin=read('admin/index.html');
const v1=read('supabase/migrations/20261008030941_store_readiness_safe_archive_v1.sql');
const reapply=read('supabase/migrations/20261008031227_store_reapplication_after_archive_v1.sql');
const v2=read('supabase/migrations/20261008032050_store_readiness_review_hardening_v2.sql');
const v3=read('supabase/migrations/20261008032631_store_readiness_rls_hardening_v3.sql');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

// Case A/B: readiness is based on real store data, media, location and sellable catalog — never a fake/test order.
for(const token of ["'shop_info'","'storefront_image'","'cover_image'","'location'","'catalog'"])
  ok(v2.includes(token),'Readiness must contain '+token);
const rs=v2.indexOf('create or replace function public.queuego_shop_readiness');
const re=v2.indexOf('create or replace function public.queuego_submit_shop_for_review',rs);
const readiness=v2.slice(rs,re);
ok(rs>=0&&re>rs,'Readiness function must be locatable');
ok(!readiness.includes('from public.orders'),'Readiness must not require a fake/test order');
ok(readiness.includes('public.laundry_services'),'Laundry readiness must use real sellable services');
ok(merchant.includes("page:isLaundry?'shop-laundry':'shop-products'"),'Laundry applicant must reach service setup before approval');
ok(merchant.includes('public_category:next.category')&&merchant.includes('public_logo:next.logo||null')&&merchant.includes('public_cover:next.cover||null'),'Merchant setup must persist canonical public store fields');

// Case C: one submit, repeat safe.
ok(v2.includes("v_status in ('pending_approval','approved')"),'Duplicate submit must replay safely');
ok(v2.includes("'duplicate',true"),'Duplicate submit result must be explicit');
ok(merchant.includes('qgmReviewSubmitBusy'),'Client must block duplicate review taps');

// Case D: Admin sees and reviews a submitted, ready store through server RPCs.
ok(v2.includes('queuego_admin_shop_application'),'Admin store detail RPC must exist');
ok(v2.includes("onboarding_status='pending_approval'")&&v2.includes('submitted_for_review_at is not null'),'Approval must require explicit submission');
ok(v2.includes('SHOP_NOT_READY'),'Approval must fail when readiness is incomplete');
ok(admin.includes("rpc/queuego_admin_shop_application"),'Admin detail must load authoritative store data');
ok(admin.includes('qg-review-map')&&admin.includes('new longdo.Map'),'Admin detail must show the store on Longdo');
ok(admin.includes('รูปหน้าร้าน')&&admin.includes('รูปหน้าปก')&&admin.includes('ตัวอย่างรายการพร้อมขาย'),'Admin detail must expose media and catalog sample');
ok(admin.includes("adminRequestShopChanges")&&admin.includes("rpc/queuego_admin_reject_shop"),'Admin must have separate request-changes and reject actions');

// Security: Merchant cannot self-approve or create an already-active application.
ok(v2.includes('SHOP_REVIEW_FIELDS_SERVER_ONLY'),'Review fields must be server controlled');
ok(v2.includes('SHOP_APPROVAL_REQUIRES_ADMIN'),'Activation must require Admin');
ok(v3.includes('SHOP_APPLICATION_MUST_START_DRAFT'),'New application insert must start as draft');

// Case E/F: archive instead of hard delete; preserve order history and permit reapplication.
ok(v2.includes("if v_role='shop' then raise exception 'SHOP_ARCHIVE_REQUIRED'"),'Generic hard delete must reject shop accounts');
ok(v3.includes('drop policy if exists shop_profiles_shop_delete'),'Merchant direct profile delete must be removed');
ok(v3.includes('SHOP_HAS_ACTIVE_ORDERS'),'Archive must refuse a shop with live orders');
ok(v3.includes("'history_preserved',true")&&v3.includes("'can_register_again',true"),'Archive result must state history preservation/reapply');
ok(!v3.includes('delete from public.orders'),'Archive must never delete order history');
ok(reapply.includes('shop_profiles_one_current_per_owner')&&reapply.includes('queuego_start_new_shop_application'),'Archived owner must be able to start one fresh application');
ok(merchant.includes('qgmStartNewStoreApplication'),'Merchant UI must expose fresh application after archive');
ok(admin.includes("if(u.type==='shop')return qgAdminArchiveShop(id)"),'Legacy Admin delete entry must route shops to safe archive');

// Case G: public/customer visibility only active + approved + not archived.
ok(v3.includes("sp.status='active'")&&v3.includes("sp.archived_at is null")&&v3.includes("sp.onboarding_status='approved'"),'Authenticated product visibility must require active approved store');
ok(v3.includes('users_guest_active_shops')&&v3.includes('users_customer_select_shops'),'Public/user shop visibility must be hardened');

// Admin cleanup filters required by operations.
for(const token of ["['ready','พร้อมใช้งาน']","['pending_review','รออนุมัติ']","['incomplete','ข้อมูลไม่ครบ']","['never_opened','ไม่เคยเปิดใช้งาน']","['archived','ถูกลบ / Archived']"])
  ok(admin.includes(token),'Admin cleanup filter missing '+token);

// Schema/source-of-truth must contain initial lifecycle migration as well as hardening.
ok(v1.includes('onboarding_status')&&v1.includes('submitted_for_review_at')&&v1.includes('archived_at'),'Lifecycle columns must be tracked in GitHub migrations');

console.log(JSON.stringify({checks,failures:0,scope:'Store readiness, approval, archive, reapplication and public visibility A-G'}));
