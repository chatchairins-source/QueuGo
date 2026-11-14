-- Restore complete foreign-key index coverage after Merchant item substitution.
-- Additive only: no table/column/policy/function changes.

CREATE INDEX IF NOT EXISTS qg_order_item_adjustments_shop_fk_idx
  ON public.qg_order_item_adjustments(shop_id);

CREATE INDEX IF NOT EXISTS qg_order_item_adjustments_actor_user_fk_idx
  ON public.qg_order_item_adjustments(actor_user_id);
