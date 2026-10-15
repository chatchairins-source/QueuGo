const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const merchant=fs.readFileSync(path.join(root,'merchant/index.html'),'utf8');
const sql=fs.readFileSync(path.join(root,'supabase/migrations/20261006083000_product_delete_archive.sql'),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

ok(merchant.includes('let qgmProductSaveBusy=false;'),'product save must have a duplicate-submit lock');
ok(merchant.includes("data-product-request-id="),'new product editor must keep a stable request/product id');
ok(merchant.includes("window.qgmNewProductRequestId||(window.qgmNewProductRequestId=uid())"),'new product request id must survive editor rerenders');
ok(merchant.includes("if(!id&&String(window.qgmNewProductRequestId||'')===String(stableId))window.qgmNewProductRequestId=null;"),'stable new-product request id must clear only after confirmed save');
ok(merchant.includes("on_conflict=id"),'new product creation must use idempotent upsert by stable id');
ok(merchant.includes("qgmDeleteProduct"),'merchant product editor must expose delete behavior');
ok(merchant.includes("rpc/queuego_delete_or_archive_product"),'delete button must use ownership-checked RPC');
ok(merchant.includes("merchant product hydrate after delete failed"),'delete post-commit refresh must be non-fatal');
ok(merchant.includes("Promise.resolve().then(()=>qtBackgroundHydrate({light:false})).catch(refreshErr=>console.warn('merchant product hydrate after save failed'"),'product save must not wait for background hydrate after server commit');
ok(merchant.includes("Promise.resolve().then(()=>qtBackgroundHydrate({light:false})).catch(refreshErr=>console.warn('merchant product hydrate after delete failed'"),'product delete must not wait for background hydrate after server commit');
ok(merchant.includes("QT_DB_CACHE.qt_products=qtSanitizeCachedProducts(dbGet('qt_products',[]),u).filter(p=>String(p.id)!==String(row.id)).concat(mapped)"),'confirmed saved product must replace its cached row immediately');
ok(merchant.includes("p?.archived_at)continue"),'archived historical products must be skipped from active product management');
ok(merchant.includes('function qtSanitizeCachedProducts'),'Merchant fast cache must sanitize product ownership and duplicate ids');
ok(merchant.includes('productsAt:now'),'Merchant product cache must have a short freshness timestamp');
ok(merchant.includes('productCacheFresh'),'stale cached product lists must not be trusted on boot');
ok(merchant.includes("cached.user.shopProfileId&&cached.user.category&&Date.now()-cached.at<7*86400000"),'returning Merchant cache must not be trusted without resolved shop profile and category');
ok(merchant.includes("qtLoadOwnMerchantIdentity(token,s.authUserId)"),'returning Merchant session must restore users and shop profile together');
const merchantRouterSource=merchant.slice(merchant.indexOf('function qgmRenderBootFailure'),merchant.indexOf('/* ---------- Layout ---------- */'));
ok(merchantRouterSource.includes("try{ const rendered=window.qtShopRoute();"),'Merchant router must attempt first paint inside its protected render block');
ok(merchantRouterSource.includes("setTimeout(()=>{try{Promise.resolve(qgmActivateVoice()).catch("),'Merchant Voice startup must be deferred and non-fatal after first paint');
ok(merchantRouterSource.indexOf("try{ const rendered=window.qtShopRoute();")<merchantRouterSource.indexOf("qgmActivateVoice()"),'Merchant first paint must happen before Voice startup');
ok(merchantRouterSource.includes("เปิดหน้าร้านค้าไม่สำเร็จ"),'Merchant must render a visible recovery screen instead of leaving a blank page when route rendering fails');
ok(merchant.includes("if(page!=='login'&&!u&&qgmHasRestorableShopSession())"),'valid Merchant session must restore identity before showing login');
ok(merchant.includes("กำลังตรวจสอบบัญชีร้าน"),'Merchant route must expose session-restore state instead of silently showing login');

ok(merchant.includes('const QGM_ROUTE_RESTORE_TIMEOUT_MS=10000;'),'Merchant route restore must have a hard upper bound');
ok(merchant.includes('qgmBoundSessionRestore(qgmRequireShopUser())'),'Merchant route must bound session restore instead of waiting forever');
ok(merchant.includes('await qtRestoreOwnUser();u=shopUser();return u||null'),'Merchant protected actions must prefer the lightweight own-identity restore and safe cache path');
ok(!merchant.includes("await qtHydrateDatabase({light:false});return shopUser()"),'Merchant protected-route identity recovery must not fall through to an unbounded full database hydrate');
ok(merchant.includes('await qgmBoundSessionRestore(qtRestoreOwnUser({refresh:true}))'),'Grocery category recovery must refresh only the Merchant identity under the same timeout');

ok(merchant.includes('const QT_AUTH_REFRESH_TIMEOUT_MS=8000;'),'Merchant token refresh must have an explicit network timeout below the route restore bound');
ok(merchant.includes('const timeout=qtRequestTimeout(QT_AUTH_REFRESH_TIMEOUT_MS);'),'Merchant refresh-token fetch must be abortable instead of hanging indefinitely');

ok(merchant.includes("function qtApplyShopProfileIdentity"),'Merchant identity merge must be shared across login/restore/hydrate');

ok(merchant.includes("const posControl=$('#qgm-pos-available'),deliveryControl=$('#qgm-delivery-available');"),'product save must tolerate missing POS/Delivery controls from stale DOM');
ok(merchant.includes("const posAvailable=posControl?posControl.checked:(existingProduct?existingProduct.posAvailable!==false:true);"),'missing POS control must preserve saved value or safe default');
ok(merchant.includes("const deliveryRequested=deliveryControl?deliveryControl.checked:(existingProduct?existingProduct.deliveryAvailable!==false:true);"),'missing Delivery control must preserve saved value or safe default');
ok(!merchant.includes("pos_available:$('#qgm-pos-available').checked"),'product save must never dereference a missing POS checkbox directly');
ok(merchant.includes("if(!nameControl||!categoryControl||!priceControl||!stockControl||!availableControl)throw new Error('หน้าฟอร์มสินค้าไม่ครบ"),'product save must reject an incomplete/stale editor DOM cleanly');
ok(!merchant.includes("available=$('#qgm-prod-available').checked"),'product save must not dereference required controls before validation');
ok(merchant.includes("const qgmProductToggleInFlight=new Set();"),'product availability toggle must ignore rapid duplicate taps');
ok(merchant.includes("control.disabled=true"),'product toggle control must lock while save is in flight');
ok(merchant.includes("&select=id,available"),'product toggle PATCH must request authoritative saved state');
ok(merchant.includes("ฐานข้อมูลไม่ยืนยันสถานะสินค้า"),'product toggle must reject unconfirmed writes');
ok(merchant.includes("qgmToggleProduct('${p.id}',this.checked,this)"),'product list toggle must pass its control for lock and rollback');
ok(merchant.includes("const belongs=pid"),'cached product ownership must prefer the authoritative shop profile id');
ok(merchant.includes(":String(p?.shopId||'')===uid"),'legacy user-id ownership fallback must only apply before shop profile resolution');
ok(merchant.includes('async function qgmRefreshProductsPage'),'product management must fetch an authoritative shop product snapshot');
ok(merchant.includes("products?select=*&shop_id=eq."),'authoritative product snapshot must be scoped to the current shop');
ok(merchant.includes("const ownPid=String(ownShop?.shopProfileId||profileMaps.shopByUserId.get(me?.userId)||'');"),'background Merchant hydrate must resolve one authoritative shop profile');
ok(!merchant.includes("qtSupabaseTable('products?select=*',{accessToken:token})"),'Merchant background hydrate must never request every product row');
ok(merchant.includes("renderShopProductsModern({authoritative:true})"),'authoritative snapshot must replace the visible cached product list');
ok(merchant.includes("qgmProductViewSearch")&&merchant.includes("qgmProductViewMode"),'search and availability filter state must be combined instead of overriding one another');
ok(merchant.includes("onclick=\"navigate('shop-product/new')\""),'Grocery primary add action must open the manual product editor');
ok(merchant.includes("onclick=\"qgmOpenGroceryCatalog()\""),'Grocery QueueGo catalog must open through the direct catalog action');
ok(merchant.includes("qgmOpenGroceryCatalogFromSession"),'Grocery catalog must support a valid Merchant session without waiting for hydrated profile state');
ok(merchant.includes("page==='shop-grocery-catalog'&&!u&&qgmHasRestorableShopSession()"),'cold Grocery catalog route must bypass the full-screen identity restore gate');
const grocery=fs.readFileSync(path.join(root,'merchant/grocery-catalog.js'),'utf8');
ok(grocery.includes("const sessionShopOwner=()=>"),'Grocery catalog renderer must accept the signed-in Merchant session as temporary owner identity');
ok(grocery.includes("shop_profiles?select=id,public_category&user_id=eq."),'session fallback must resolve the current shop directly and verify its public category');
ok(merchant.includes('grocery-catalog.js?v=20261015-makro-full1'),'Merchant HTML must cache-bust the current Grocery catalog JavaScript');
ok(merchant.includes('grocery.css?v=20261015-makro-full1'),'Merchant HTML must cache-bust the current Grocery sales-channel layout CSS');
ok(merchant.includes("case'shop-grocery-catalog':return qgRenderGroceryCatalog({addMode:true,pageTitle:'เพิ่มสินค้าจากแคตตาล็อก'})"),'dedicated Grocery catalog fallback route must keep the catalog page name');
ok(merchant.includes("if(u?.category==='grocery'&&p?.grocery_master_id)return qgRenderGroceryCatalog"),'only existing master-linked Grocery rows may redirect to the master catalog');
ok(!merchant.includes("if(u?.category==='grocery'&&(id==='new'||p?.grocery_master_id))"),'new Grocery products must never be hijacked by master catalog');
ok(merchant.includes("const pagedGrocery=ownShop?.category==='grocery'&&['shop-grocery-catalog'].includes"),'normal Grocery product/manual-editor pages must load the shop product snapshot');


ok(sql.includes('create or replace function public.queuego_delete_or_archive_product'),'safe product delete/archive RPC must exist');
ok(sql.includes('join public.shop_profiles sp on sp.id=p.shop_id'),'RPC must verify shop ownership');
ok(sql.includes('delete from public.market_products'),'market stock row must be cleared before product removal');
ok(sql.includes('exists(select 1 from public.order_items oi where oi.product_id=p_product_id)'),'historical order references must prevent hard deletion');
ok(sql.includes("return 'archived'"),'historical products must archive instead of breaking past orders');
ok(sql.includes("return 'deleted'"),'unused products must hard-delete');
ok(sql.includes('revoke all on function public.queuego_delete_or_archive_product(uuid) from public,anon'),'delete RPC must not be callable anonymously');

console.log(JSON.stringify({checks,failures:0,scope:'Merchant product single-submit idempotency, authoritative cache refresh, and safe delete/archive'}));

const pos=fs.readFileSync(path.join(root,'merchant/pos-table-v2.js'),'utf8');
ok(pos.includes("qtLoadOwnMerchantIdentity(auth.access_token,auth.user.id)"),'fresh Merchant login must use the same full identity loader');
ok(pos.includes("if(!u.shopProfileId)throw Error('ไม่พบข้อมูลร้านค้า กรุณาติดต่อแอดมิน')"),'Merchant login must not complete with a half-hydrated shop identity');
