-- Snapshot product imagery onto order items for stable Rider verification.
BEGIN;

ALTER TABLE public.order_items
  ADD COLUMN IF NOT EXISTS item_image text;

UPDATE public.order_items oi
SET item_image=p.image
FROM public.products p
WHERE oi.product_id=p.id
  AND oi.item_image IS NULL
  AND nullif(trim(coalesce(p.image,'')),'') IS NOT NULL;

CREATE OR REPLACE FUNCTION public.qg_snapshot_order_item_image()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
BEGIN
  IF NEW.item_image IS NULL AND NEW.product_id IS NOT NULL THEN
    SELECT p.image INTO NEW.item_image
    FROM public.products p
    WHERE p.id=NEW.product_id;
  END IF;
  RETURN NEW;
END
$function$;

DROP TRIGGER IF EXISTS qg_snapshot_order_item_image_trg ON public.order_items;
CREATE TRIGGER qg_snapshot_order_item_image_trg
BEFORE INSERT ON public.order_items
FOR EACH ROW
EXECUTE FUNCTION public.qg_snapshot_order_item_image();

COMMIT;
