# QueueMarket POS — tax backend and real merchant credit repair (17 Nov 2026)

Scope: QueueMarket **Native Android** POS (Kotlin/Jetpack Compose), PR #1207 integration successor. Engineering work is isolated in `fix/native-pos-credit-tax-service-20261117`; do not merge or apply Production database DDL without verified integration and safe roll-forward/rollback evidence.

## Confirmed read-only Production facts

Inspected Supabase project `pkypiqhlrmzocysgeqew` in read-only mode on 2026-11-17:

- Project healthy; `supabase_migrations.schema_migrations` contains **392** rows, latest `20261111111354`.
- `public.pos_tax_settings` is **absent**.
- `public.pos_save_tax_settings(boolean,numeric,boolean,text,text,text)` is **absent**.
- `public.pos_apply_tax_snapshot()` is **absent**, and the `orders` POS tax-snapshot columns are **absent**.
- The existing `public.orders` POS integrity constraint assumes `total_amount = subtotal - discount_amount`; enabling tax-exclusive checkout without updating this invariant would make writes fail.
- Actual QueueGo merchant credit infrastructure **does exist**: `qg_private.credit_accounts`, `credit_entries`, `credit_holds`, `credit_topups`, and the authenticated `public.queuego_credit_summary(p_kind text,p_topup uuid)` RPC. This is **QueueGo platform credit for fees/GP**, *not input VAT tax credit*, a customer store-credit debt, or POS cash.

Therefore **the Android tax settings screen cannot save successfully on current Production**. A client-only redesign does not install tax calculation or invoice issuance.

## Safe Android fixes in this branch

1. Preserve the owner-only **เครดิตร้านค้า** POS tab and its existing `PosMerchantCreditView`, reading `MerchantPosApi.merchantCreditSummary(auth)` from the real authenticated `queuego_credit_summary` RPC. No additional wallet or parallel credit ledger is introduced. The standalone Merchant Revenue screen continues to use its existing shared credit component.
2. Display specific tax backend-load failure (rather than infinite disabled form) and provide a retry that remains clickable while settings are unavailable. Disable tax edits and saves until server settings are loaded successfully.
3. Show base amount and tax amount in the catalog cart and cashier only when the bill's immutable server tax snapshots are available. Do not calculate tax client-side for payment or invent VAT for legacy bills.
4. Preserve cash-shift, payment reconciliation, refunds, receipts and POS permissions. This PR keeps the parent PR #1207 fixes to Kotlin checkout layout and server-confirmed cash/receipt handling; it only adds fail-closed VAT guards and accurate backend service errors.
5. Add `tests/native-pos-credit-tax-service.cjs` plus `MerchantPosTaxServiceTest.kt` to Merchant source and JUnit tests.

## Backend tax delivery sequence — still required

2026-11-22 isolated acceptance: `tests/pos-tax-backend-isolated.cjs` now runs the captured Production owner/shop helper bodies with actual PostgreSQL roles and RLS in PGlite. All 56 checks pass locally, including two-shop visibility, staff/unrelated/missing-identity RPC denial, anonymous denial, direct-write ACLs and rollback-only temporary-grant RLS defense. Authentication identities and records are synthetic; this is not live JWT, full checkout/refund/trigger composition or Production acceptance. Root SQL and Production remain unchanged. Exact-head hosted CI is still required.

- Review existing source files `QueueGo-POS-Tax-Foundation-Migration.sql` and `QueueGo-POS-Tax-Checkout-Integration-Migration.sql`. Both are at repo root, **not in the timestamped migrations currently deployed to Production**. Do not simply paste them into the live SQL Editor.
- Reconcile the entire Production migration history and actual definitions of `public.orders`, `pos_guard_write`, related triggers, `pos_channel_integrity_check`, permission checks, tax and payment RPCs against current source. Run exact migration upgrade on disposable staging with representative legacy and new bills.
- Verify `pos_my_shop` / `pos_is_owner`, owner-only settings write, `SELECT` RLS for the shop, authenticated RPC `EXECUTE`, and denial of anon/cross-shop users. Review any `SECURITY DEFINER` `search_path`, grants and all billing-affecting triggers. Do **not** relax security to make the button work.
- Test inclusive and exclusive tax modes, discount/price edits, multiple cart lines, refund/void, receipt snapshot permanence, report consistency, no double collection and old pre-migration bills. Ensure the current POS-channel integrity constraint remains valid and non-POS orders are unaffected.
- Only after staging tests and an approved rollout/backup/restore plan: create a properly versioned migration in the normal repository ledger, apply to Production under controlled release, verify the schema and RPC, smoke-test authenticated settings and new bills, and enable UI.
- Credit balance must not be mistaken for a VAT input-tax credit. If the owner meant *เครดิตภาษีซื้อ* rather than *เครดิตค่าบริการ QueueGo*, that requires a separate bookkeeping/tax-invoice contract and must not be auto-derived from the credit wallet.

## Read-only Production preflight

```sql
select
  to_regclass('public.pos_tax_settings') is not null as settings_table_ready,
  to_regprocedure('public.pos_save_tax_settings(boolean,numeric,boolean,text,text,text)') is not null as settings_rpc_ready,
  to_regprocedure('public.pos_apply_tax_snapshot()') is not null as snapshot_trigger_function_ready,
  exists (
    select 1 from information_schema.columns
    where table_schema='public' and table_name='orders'
      and column_name='pos_tax_amount'
  ) as order_tax_snapshot_ready,
  to_regprocedure('public.queuego_credit_summary(text,uuid)') is not null as merchant_credit_rpc_ready;
```

This preflight is a metadata query only. It does not imply a tax certificate can be generated, nor that a payment transaction was tested.
