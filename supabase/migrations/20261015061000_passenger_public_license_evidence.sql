-- Passenger Ride public-driving-licence evidence.
-- Optional Ride eligibility only; ordinary delivery Rider onboarding is unchanged.
create table if not exists qg_private.rider_public_license_submissions(
 rider_id uuid primary key references public.rider_profiles(id) on delete cascade,
 evidence_path text not null,
 status text not null check(status in ('pending','approved','rejected')),
 submitted_at timestamptz not null default now(),
 reviewed_at timestamptz,
 reviewed_by uuid references public.users(id) on delete set null,
 review_note text,
 updated_at timestamptz not null default now()
);
create index if not exists rider_public_license_submission_status_idx
 on qg_private.rider_public_license_submissions(status,submitted_at desc);
revoke all on table qg_private.rider_public_license_submissions from public,anon,authenticated;
grant all on table qg_private.rider_public_license_submissions to service_role;

create or replace function qg_private.rider_public_license_status()
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare
 v_rider uuid;
 v_submission qg_private.rider_public_license_submissions%rowtype;
 v_expiry date;
 v_valid boolean;
begin
 perform qg_private.assert_live_session();
 select r.id into v_rider
 from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' and r.status='active'
 order by r.updated_at desc nulls last, r.id limit 1;
 if v_rider is null then raise exception 'active rider required' using errcode='42501'; end if;

 select * into v_submission
 from qg_private.rider_public_license_submissions
 where rider_id=v_rider;

 select expires_on into v_expiry
 from qg_private.rider_document_expiries
 where rider_id=v_rider and document_key='public_driver_license' and vehicle_id is null;

 v_valid:=qg_private.ride_public_license_valid(v_rider,now());
 return jsonb_build_object(
  'valid',v_valid,
  'status',case when v_submission.rider_id is not null then v_submission.status when v_valid then 'approved' else 'missing' end,
  'expires_on',v_expiry,
  'submitted_at',v_submission.submitted_at,
  'reviewed_at',v_submission.reviewed_at,
  'review_note',v_submission.review_note,
  'has_evidence',v_submission.rider_id is not null and v_submission.evidence_path is not null
 );
end $$;
revoke all on function qg_private.rider_public_license_status() from public,anon,authenticated;
grant execute on function qg_private.rider_public_license_status() to authenticated;

create or replace function public.qg_rider_public_license_status()
returns jsonb language sql stable security invoker set search_path='' as
$$ select qg_private.rider_public_license_status(); $$;
revoke all on function public.qg_rider_public_license_status() from public,anon;
grant execute on function public.qg_rider_public_license_status() to authenticated,service_role;

create or replace function qg_private.rider_submit_public_license(p_evidence_path text)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_rider uuid;
begin
 perform qg_private.assert_live_session();
 select r.id into v_rider
 from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' and r.status='active'
 order by r.updated_at desc nulls last, r.id limit 1 for update of r;
 if v_rider is null then raise exception 'active rider required' using errcode='42501'; end if;

 if p_evidence_path is null
    or p_evidence_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
    or not exists(select 1 from storage.objects where bucket_id='qg-evidence' and name=p_evidence_path)
 then raise exception 'valid public driving license evidence required'; end if;

 insert into qg_private.rider_public_license_submissions(
  rider_id,evidence_path,status,submitted_at,reviewed_at,reviewed_by,review_note,updated_at
 ) values(v_rider,p_evidence_path,'pending',now(),null,null,null,now())
 on conflict(rider_id) do update set
  evidence_path=excluded.evidence_path,status='pending',submitted_at=now(),
  reviewed_at=null,reviewed_by=null,review_note=null,updated_at=now();

 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 select r.user_id,'rider_public_license_submit','rider',v_rider,'Public driving licence evidence submitted',
        jsonb_build_object('evidence_path',p_evidence_path)
 from public.rider_profiles r where r.id=v_rider;

 return qg_private.rider_public_license_status();
end $$;
revoke all on function qg_private.rider_submit_public_license(text) from public,anon,authenticated;
grant execute on function qg_private.rider_submit_public_license(text) to authenticated;

create or replace function public.qg_rider_submit_public_license(p_evidence_path text)
returns jsonb language sql security invoker set search_path='' as
$$ select qg_private.rider_submit_public_license(p_evidence_path); $$;
revoke all on function public.qg_rider_submit_public_license(text) from public,anon;
grant execute on function public.qg_rider_submit_public_license(text) to authenticated,service_role;

create or replace function public.queuego_admin_review_public_license(p_rider_id uuid,p_expires_on date,p_note text)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare
 v_admin uuid;
 v_submission qg_private.rider_public_license_submissions%rowtype;
begin
 perform qg_private.assert_live_session();
 select id into v_admin from public.users
 where auth_user_id=auth.uid() and role='admin' and status='active';
 if v_admin is null then raise exception 'active admin required' using errcode='42501'; end if;
 if length(trim(coalesce(p_note,''))) not between 3 and 500 then
  raise exception 'public license review note required';
 end if;

 perform 1 from public.rider_profiles r join public.users u on u.id=r.user_id
 where r.id=p_rider_id and u.role='rider' and u.status<>'deleted'
 for update of r;
 if not found then raise exception 'rider unavailable'; end if;

 if p_expires_on is not null and (p_expires_on<current_date or p_expires_on>current_date+interval '20 years')
 then raise exception 'valid public driving license expiry required'; end if;

 select * into v_submission
 from qg_private.rider_public_license_submissions
 where rider_id=p_rider_id
 for update;

 if p_expires_on is not null then
  if v_submission.rider_id is null or v_submission.status<>'pending' or v_submission.evidence_path is null
     or not exists(select 1 from storage.objects where bucket_id='qg-evidence' and name=v_submission.evidence_path)
  then raise exception 'pending public driving license evidence required'; end if;

  delete from qg_private.rider_document_expiries
  where rider_id=p_rider_id and document_key='public_driver_license';

  insert into qg_private.rider_document_expiries(rider_id,document_key,expires_on,recorded_by)
  values(p_rider_id,'public_driver_license',p_expires_on,v_admin);

  update qg_private.rider_public_license_submissions
  set status='approved',reviewed_at=now(),reviewed_by=v_admin,review_note=trim(p_note),updated_at=now()
  where rider_id=p_rider_id;
 else
  delete from qg_private.rider_document_expiries
  where rider_id=p_rider_id and document_key='public_driver_license';

  update qg_private.rider_public_license_submissions
  set status='rejected',reviewed_at=now(),reviewed_by=v_admin,review_note=trim(p_note),updated_at=now()
  where rider_id=p_rider_id;
 end if;

 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(v_admin,'rider_public_license_review','rider',p_rider_id,trim(p_note),
  jsonb_build_object('expires_on',p_expires_on,'approved',p_expires_on is not null,
   'evidence_path',case when v_submission.rider_id is not null then v_submission.evidence_path else null end));

 return jsonb_build_object(
  'rider_id',p_rider_id,
  'public_license_expires_on',p_expires_on,
  'approved',p_expires_on is not null,
  'evidence_path',case when v_submission.rider_id is not null then v_submission.evidence_path else null end
 );
end $$;
revoke all on function public.queuego_admin_review_public_license(uuid,date,text) from public,anon;
grant execute on function public.queuego_admin_review_public_license(uuid,date,text) to authenticated;

create or replace function public.queuego_admin_public_licenses()
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
begin
 perform qg_private.assert_live_session();
 if not public.is_active_admin() then raise exception 'active admin required' using errcode='42501'; end if;
 return coalesce((
  select jsonb_agg(jsonb_build_object(
   'rider_id',r.id,
   'name',u.name,
   'expires_on',d.expires_on,
   'approved',qg_private.ride_public_license_valid(r.id,now()),
   'submission_status',s.status,
   'evidence_path',s.evidence_path,
   'submitted_at',s.submitted_at,
   'reviewed_at',s.reviewed_at,
   'review_note',s.review_note
  ) order by u.name)
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  left join qg_private.rider_document_expiries d
   on d.rider_id=r.id and d.document_key='public_driver_license'
  left join qg_private.rider_public_license_submissions s
   on s.rider_id=r.id
  where u.role='rider' and u.status='active'
 ),'[]'::jsonb);
end $$;
revoke all on function public.queuego_admin_public_licenses() from public,anon;
grant execute on function public.queuego_admin_public_licenses() to authenticated;
