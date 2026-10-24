-- Register the pinned, reusable Thailand administrative hierarchy source.
-- The importer records this source and its snapshot fingerprint per imported row.
insert into public.queuego_place_sources(
 source_key,source_name,license_id,license_url,attribution_requirement,update_policy,
 allowed_storage,allowed_redistribution,metadata
) values (
 'th_admin_open_data','Open Admin Data Thailand Administrative Divisions','CC-BY-4.0',
 'https://creativecommons.org/licenses/by/4.0/',
 'Attribute Open Admin Data and link CC BY 4.0; identify modifications. Preserve upstream citation and source record IDs.',
 'Import only a reviewed release snapshot; verify upstream release and all-flat.ndjson Git blob SHA before each update.',
 true,true,
 '{"repository":"https://github.com/open-admin-data/thailand-administrative-divisions","data_file":"data/all-flat.ndjson","release":"2026.06","file_blob_sha":"baa3eb18fb79412e85dcef08059d16f8cf47b317","expected_counts":{"province":77,"district":928,"subdistrict":7364},"villages_included":false,"attribution":"Open Admin Data, Thailand Administrative Divisions (2026.06), CC BY 4.0; modified for QueueGo Places."}'::jsonb
)
on conflict(source_key) do update set source_name=excluded.source_name,license_id=excluded.license_id,
 license_url=excluded.license_url,attribution_requirement=excluded.attribution_requirement,
 update_policy=excluded.update_policy,allowed_storage=excluded.allowed_storage,
 allowed_redistribution=excluded.allowed_redistribution,metadata=excluded.metadata,updated_at=now();

-- DOPA publishes village-name/location resources for 76 provinces under Open Data Common.
-- Keep the raw resource provenance and never import its unrelated population attributes.
insert into public.queuego_place_sources(
 source_key,source_name,license_id,license_url,attribution_requirement,update_policy,
 allowed_storage,allowed_redistribution,metadata
) values (
 'th_dopa_village_gis','Department of Provincial Administration Village GIS','Open Data Common',
 'https://gdhelppage.gdcatalog.go.th/p00_03_new7.html',
 'Credit the Department of Provincial Administration and link its GIS-01 village dataset; honor any rights-holder attribution request.',
 'Refresh from the reviewed DOPA GIS-01 JSON resources for 76 provinces; record each resource URL, retrieval time, checksum, and source record ID. Import village name, hierarchy, and coordinates only; do not ingest population attributes.',
 true,true,
 '{"catalog_url":"https://catalog.dopa.go.th/dataset/gis-01","dataset_id":"gis-01","published_province_count":76,"formats":["JSON"],"fields":["GIS","LAT","LONG","village","subdistrict","district","province"],"excludes_bangkok":true,"attribution":"Department of Provincial Administration (DOPA), Village Location and General Information GIS-01; Open Data Common"}'::jsonb
)
on conflict(source_key) do update set source_name=excluded.source_name,license_id=excluded.license_id,
 license_url=excluded.license_url,attribution_requirement=excluded.attribution_requirement,
 update_policy=excluded.update_policy,allowed_storage=excluded.allowed_storage,
 allowed_redistribution=excluded.allowed_redistribution,metadata=excluded.metadata,updated_at=now();

-- Keep one current provenance record per imported source field and record.
create unique index if not exists queuego_place_field_provenance_source_field_uidx
 on public.queuego_place_field_provenance(place_id,field_name,source,source_record_id)
 where source_record_id is not null;
