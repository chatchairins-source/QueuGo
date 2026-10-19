-- Raw pork tenderloin / generic lean pork commodity image, not a retailer pack.
update public.market_master_products
set image='https://upload.wikimedia.org/wikipedia/commons/0/05/Poledwiczka_wieprzowa.JPG',
    image_status='approved',
    image_source='https://commons.wikimedia.org/wiki/File:Poledwiczka_wieprzowa.JPG',
    image_rights_evidence='Julo · Public domain · Wikimedia Commons',
    updated_at=now()
where slug in ('pork-tenderloin-dressed','pork-lean');
