-- Read-only consistent application snapshot. Not an auth/Storage object backup.
-- Keep the resulting private JSON outside git. Do not execute restore on Production.
with tables as (
 select c.oid,n.nspname,c.relname,c.relrowsecurity,c.relforcerowsecurity,c.relacl,
 (select jsonb_agg(jsonb_build_object('name',a.attname,'type',format_type(a.atttypid,a.atttypmod),'not_null',a.attnotnull,'generated',a.attgenerated,'identity',a.attidentity,'default',pg_get_expr(d.adbin,d.adrelid)) order by a.attnum)
 from pg_attribute a left join pg_attrdef d on d.adrelid=a.attrelid and d.adnum=a.attnum where a.attrelid=c.oid and a.attnum>0 and not a.attisdropped) as columns,
 (xpath('/table/row/data/text()',query_to_xml(format('select encode(convert_to(coalesce(jsonb_agg(to_jsonb(t) order by to_jsonb(t)::text),''[]''::jsonb)::text,''UTF8''),''base64'') data from ONLY %I.%I t',n.nspname,c.relname),true,false,'')))[1]::text as data_b64
 from pg_class c join pg_namespace n on n.oid=c.relnamespace
 where c.relkind='r' and (n.nspname='public' or n.nspname='storage' and c.relname in ('buckets','objects'))
)
select jsonb_build_object(
 'format','queuego-application-snapshot-v1','captured_at',now(),'project_ref','pkypiqhlrmzocysgeqew','server_version',current_setting('server_version'),
 'scope','public application tables and schema; storage.buckets/objects metadata; excludes auth credentials, storage object bytes and platform-managed internals',
 'tables',(select jsonb_agg(to_jsonb(t)-'oid' order by nspname,relname) from tables t),
 'constraints',(select coalesce(jsonb_agg(jsonb_build_object('table',c.conrelid::regclass::text,'name',c.conname,'type',c.contype,'definition',pg_get_constraintdef(c.oid,true))), '[]') from pg_constraint c join pg_class t on t.oid=c.conrelid join pg_namespace n on n.oid=t.relnamespace where n.nspname='public'),
 'indexes',(select coalesce(jsonb_agg(jsonb_build_object('table',i.indrelid::regclass::text,'definition',pg_get_indexdef(i.indexrelid))), '[]') from pg_index i join pg_class c on c.oid=i.indrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='public'),
 'triggers',(select coalesce(jsonb_agg(jsonb_build_object('table',t.tgrelid::regclass::text,'name',t.tgname,'enabled',t.tgenabled,'definition',pg_get_triggerdef(t.oid,true))), '[]') from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname in ('public','storage') and not t.tgisinternal),
 'functions',(select coalesce(jsonb_agg(jsonb_build_object('name',p.oid::regprocedure::text,'definition',pg_get_functiondef(p.oid),'owner',pg_get_userbyid(p.proowner),'acl',p.proacl)), '[]') from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.prokind='f' and not exists(select 1 from pg_depend d where d.classid='pg_proc'::regclass and d.objid=p.oid and d.deptype='e')),
 'policies',(select coalesce(jsonb_agg(to_jsonb(p)), '[]') from pg_policies p where schemaname in ('public','storage')),
 'views',(select coalesce(jsonb_agg(jsonb_build_object('name',c.oid::regclass::text,'kind',c.relkind,'definition',pg_get_viewdef(c.oid,true),'options',c.reloptions,'acl',c.relacl)), '[]') from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind in ('v','m')),
 'enums',(select coalesce(jsonb_agg(jsonb_build_object('schema',x.nspname,'name',x.typname,'labels',x.labels)), '[]') from (select n.nspname,t.typname,jsonb_agg(e.enumlabel order by e.enumsortorder) labels from pg_type t join pg_namespace n on n.oid=t.typnamespace join pg_enum e on e.enumtypid=t.oid where n.nspname in ('public','storage') group by n.nspname,t.typname) x),
 'sequences',(select coalesce(jsonb_agg(to_jsonb(s)), '[]') from pg_sequences s where schemaname='public'),
 'grants',(select coalesce(jsonb_agg(to_jsonb(g)), '[]') from information_schema.role_table_grants g where table_schema in ('public','storage')),
 'extensions',(select coalesce(jsonb_agg(jsonb_build_object('name',e.extname,'version',e.extversion,'schema',n.nspname)), '[]') from pg_extension e join pg_namespace n on n.oid=e.extnamespace)
) as backup;
