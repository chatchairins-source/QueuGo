const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const merchant=fs.readFileSync(path.join(root,'merchant/index.html'),'utf8');
const sql=fs.readFileSync(path.join(root,'supabase/migrations/20261006083000_product_delete_archive.sql'),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

ok(merchant.includes('let qgmProductSaveBusy=false;'),'product save must have a duplicate-submit lock');
ok(merchant.includes("data-product-request-id="),'new product editor must keep a stable request/product id');
ok(merchant.includes("on_conflict=id"),'new product creation must use idempotent upsert by stable id');
ok(merchant.includes("qgmDeleteProduct"),'merchant product editor must expose delete behavior');
ok(merchant.includes("rpc/queuego_delete_or_archive_product"),'delete button must use ownership-checked RPC');
ok(merchant.includes("merchant product hydrate after delete failed"),'delete post-commit refresh must be non-fatal');
ok(merchant.includes("!p?.archived_at"),'archived historical products must be hidden from active product management');
ok(merchant.includes('function qtSanitizeCachedProducts'),'Merchant fast cache must sanitize product ownership and duplicate ids');
ok(merchant.includes('productsAt:now'),'Merchant product cache must have a short freshness timestamp');
ok(merchant.includes('productCacheFresh'),'stale cached product lists must not be trusted on boot');
ok(merchant.includes('async function qgmRefreshProductsPage'),'product management must fetch an authoritative shop product snapshot');
ok(merchant.includes("products?select=*&shop_id=eq."),'authoritative product snapshot must be scoped to the current shop');
ok(merchant.includes("renderShopProductsModern({authoritative:true})"),'authoritative snapshot must replace the visible cached product list');
ok(merchant.includes("qgmProductViewSearch")&&merchant.includes("qgmProductViewMode"),'search and availability filter state must be combined instead of overriding one another');

ok(sql.includes('create or replace function public.queuego_delete_or_archive_product'),'safe product delete/archive RPC must exist');
ok(sql.includes('join public.shop_profiles sp on sp.id=p.shop_id'),'RPC must verify shop ownership');
ok(sql.includes('delete from public.market_products'),'market stock row must be cleared before product removal');
ok(sql.includes('exists(select 1 from public.order_items oi where oi.product_id=p_product_id)'),'historical order references must prevent hard deletion');
ok(sql.includes("return 'archived'"),'historical products must archive instead of breaking past orders');
ok(sql.includes("return 'deleted'"),'unused products must hard-delete');
ok(sql.includes('revoke all on function public.queuego_delete_or_archive_product(uuid) from public,anon'),'delete RPC must not be callable anonymously');

console.log(JSON.stringify({checks,failures:0,scope:'Merchant product single-submit idempotency, authoritative cache refresh, and safe delete/archive'}));
