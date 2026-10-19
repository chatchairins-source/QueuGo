-- Generic raw whole-duck commodity only; no branded or size-specific approvals.
update public.market_master_products
set image='https://upload.wikimedia.org/wikipedia/commons/6/62/Duck%2C_Duck%2C_Duck_%282863443552%29.jpg',
    image_status='approved',
    image_source='https://commons.wikimedia.org/wiki/File:Duck,_Duck,_Duck_(2863443552).jpg',
    image_rights_evidence='cogdogblog (Alan Levine) · CC0 1.0 · Wikimedia Commons',
    updated_at=now()
where slug='whole-duck';
