-- READ ONLY metadata observed 2026-11-21, applied ONLY in disposable tests.
-- Both live tables: RLS=true, FORCE=false; ACL postgres/service_role only.
-- Both policies: qg_server_only_no_client_access, ALL, anon/authenticated,
-- USING false / WITH CHECK false. Neither client role has public CREATE.
ALTER TABLE public.qg_table_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.qg_table_requests ENABLE ROW LEVEL SECURITY;
CREATE POLICY qg_server_only_no_client_access ON public.qg_table_sessions
 FOR ALL TO anon,authenticated USING(false) WITH CHECK(false);
CREATE POLICY qg_server_only_no_client_access ON public.qg_table_requests
 FOR ALL TO anon,authenticated USING(false) WITH CHECK(false);
REVOKE ALL ON public.qg_table_sessions,public.qg_table_requests FROM PUBLIC,anon,authenticated;
GRANT ALL ON public.qg_table_sessions,public.qg_table_requests TO service_role;
REVOKE CREATE ON SCHEMA public FROM PUBLIC,anon,authenticated;
