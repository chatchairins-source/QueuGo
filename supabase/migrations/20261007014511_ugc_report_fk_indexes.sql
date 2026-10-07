create index if not exists qg_ugc_reports_reporter_fk_idx on public.qg_ugc_reports(reporter_user_id);
create index if not exists qg_ugc_reports_order_fk_idx on public.qg_ugc_reports(order_id);
create index if not exists qg_ugc_reports_message_fk_idx on public.qg_ugc_reports(message_id);
create index if not exists qg_ugc_reports_admin_fk_idx on public.qg_ugc_reports(admin_id);
