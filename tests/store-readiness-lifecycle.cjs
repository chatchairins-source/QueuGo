const fs=require('fs'),path=require('path'),assert=require('assert');
const read=p=>fs.readFileSync(path.resolve(__dirname,'..',p),'utf8');
const merchant=read('merchant/index.html'),admin=read('admin/index.html'),adminLocations=read('admin/locations.js'),adminMarket=read('admin/market.js');
const v1=read('supabase/migrations/20261008030941_store_readiness_safe_archive_v1.sql');
const reapply=read('supabase/migrations/20261008031227_store_reapplication_after_archive_v1.sql');
const backup=read('supabase/migrations/20261008031652_store_lifecycle_backup_20261008.sql');
const v2=read('supabase/migrations/20261008032050_store_readiness_review_hardening_v2.sql');
const v3=read('supabase/migrations/20261008032631_store_readiness_rls_hardening_v3.sql');
const overview=read('supabase/migrations/20261008033600_store_admin_readiness_overview_v4.sql');
const reapplyV5=read('supabase/migrations/20261008035002_store_reapplication_generated_columns_fix_v5.sql');
const reapplyV6=read('supabase/migrations/20261008035409_store_reapplication_role_guard_v6.sql');
const reconcile=read('supabase/migrations/20261008034157_store_readiness_legacy_reconcile_v4.sql');
const statusBackup=read('supabase/migrations/20261008040547_store_status_cascade_backup_20261008.sql');
const v7=read('supabase/migrations/20261008040628_store_lifecycle_status_cascade_hardening_v7.sql');
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
ok(!merchant.includes('public_category:next.category')&&!merchant.includes('public_logo:next.logo||null')&&!merchant.includes('public_cover:next.cover||null')&&merchant.includes('metadata:qtUserMetadata(next)'),'Merchant setup must persist generated public fields through metadata only');

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
ok(overview.includes('queuego_admin_shop_overview')&&overview.includes('queuego_shop_readiness(r.id)'),'Admin overview must classify stores from server readiness');
ok(admin.includes('shopReadiness:som.get(String(x.id))?.readiness||null'),'Admin list must consume server readiness');
ok(admin.includes("u.shopReadiness?.complete===true"),'Ready bucket must require complete readiness');

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
ok(reapplyV5.includes('insert into public.shop_profiles(user_id,shop_name,status,onboarding_status,metadata)'),'Reapplication must write only base shop fields');
ok(!reapplyV5.includes('insert into public.shop_profiles(user_id,shop_name,public_category'),'Reapplication must not write generated public_category directly');
ok(reapplyV6.includes("public.get_my_role() is distinct from 'shop'")&&reapplyV6.includes("SHOP_LOGIN_REQUIRED"),'Only merchant-role users may create a fresh shop application');
ok(merchant.includes('qgmStartNewStoreApplication'),'Merchant UI must expose fresh application after archive');
ok(admin.includes("if(u.type==='shop')return qgAdminArchiveShop(id)"),'Legacy Admin delete entry must route shops to safe archive');

// Current-profile resolution after archive/reapplication.
ok(!admin.includes('shop_profiles?on_conflict=user_id'),'Admin cache must never recreate a shop through the retired user_id upsert');
ok(admin.includes("shop_profiles?select=id&user_id=eq.'+encodeURIComponent(u.id)+'&archived_at=is.null&order=created_at.desc&limit=1"),'Admin shop persistence must target the current non-archived profile');
ok(admin.includes("shop_profiles?select=id,user_id&archived_at=is.null"),'Admin open-state refresh must ignore archived profiles');
ok(admin.includes("shop_profiles?select=id&user_id=eq.'+encodeURIComponent(id)+'&archived_at=is.null&order=created_at.desc&limit=1"),'Admin open/close action must resolve the current profile');
ok(adminLocations.includes("kind==='shop_user'?'&archived_at=is.null&order=created_at.desc':''"),'Admin location editor must resolve the current profile for a merchant user');
ok(adminMarket.includes('market_membership_status=eq.pending&archived_at=is.null'),'Market review must ignore archived merchant profiles');
ok(merchant.includes("shop_profiles?select=user_id,shop_name&user_id=in.('+ids.map(encodeURIComponent).join(',')+')&archived_at=is.null"),'Merchant support lookup must use the current store name');

// Suspension/restore must respect lifecycle instead of bypassing approval or mutating archived history.
ok(statusBackup.includes('store_status_cascade_backup_20261008'),'Status cascade definitions must be backed up before replacement');
ok(v7.includes("where user_id=new.id and archived_at is null"),'User status cascade must never mutate archived shop history');
ok(v7.includes("when onboarding_status='approved' then 'active'")&&v7.includes("else 'pending'"),'Account restore must not activate an unapproved shop');
ok(v7.includes("old.onboarding_status='approved'")&&v7.includes("old.status is distinct from 'suspended'")&&v7.includes('SHOP_RESTORE_REQUIRES_APPROVED_SUSPENSION'),'Only a suspended approved store may use the restore path');
ok(v7.includes('SHOP_NOT_READY'),'Restored approved stores must still pass readiness');

// Case G: public/customer visibility only active + approved + not archived.
ok(v3.includes("sp.status='active'")&&v3.includes("sp.archived_at is null")&&v3.includes("sp.onboarding_status='approved'"),'Authenticated product visibility must require active approved store');
ok(v3.includes('users_guest_active_shops')&&v3.includes('users_customer_select_shops'),'Public/user shop visibility must be hardened');

// Legacy reconciliation: an old active shop that is not actually ready must go back to needs_changes,
// but only when it has no live order. No order history is removed.
ok(reconcile.includes('RECONCILE_BLOCKED_ACTIVE_ORDERS'),'Legacy reconciliation must abort if an affected shop has a live order');
ok(reconcile.includes("status='pending'")&&reconcile.includes("onboarding_status='needs_changes'"),'Incomplete legacy stores must return to pending readiness');
ok(reconcile.includes('delivery_enabled=false'),'Incomplete legacy stores must be removed from delivery');
ok(reconcile.includes("set status='pending'")&&reconcile.includes("where u.role='shop'"),'Incomplete legacy merchant user must return to pending');
ok(!reconcile.includes('delete from public.orders'),'Legacy reconciliation must preserve order history');

// Backup/source-of-truth requirements.
ok(backup.includes('queuego_private.store_lifecycle_backup_20261008'),'Lifecycle backup migration must be mirrored to GitHub');
ok(backup.includes("'shop_profiles'")&&backup.includes("'functions'")&&backup.includes("'policies'"),'Lifecycle backup must cover data and security definitions');
ok(reconcile.includes('queuego_private.store_lifecycle_pre_reconcile_20261008_1034'),'Pre-reconcile shop snapshot must be tracked');

// Admin cleanup filters required by operations.
for(const token of ["['ready','พร้อมใช้งาน']","['pending_review','รออนุมัติ']","['incomplete','ข้อมูลไม่ครบ']","['never_opened','ไม่เคยเปิดใช้งาน']","['archived','ถูกลบ / Archived']"])
  ok(admin.includes(token),'Admin cleanup filter missing '+token);

// Schema/source-of-truth must contain initial lifecycle migration as well as hardening.
ok(v1.includes('onboarding_status')&&v1.includes('submitted_for_review_at')&&v1.includes('archived_at'),'Lifecycle columns must be tracked in GitHub migrations');

console.log(JSON.stringify({checks,failures:0,scope:'Store readiness, approval, archive, reapplication, current-profile resolution, lifecycle restore and public visibility A-G'}));
