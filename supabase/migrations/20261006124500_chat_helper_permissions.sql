-- Tighten direct RPC exposure for chat policy helper functions.
BEGIN;

REVOKE ALL ON FUNCTION public.qg_chat_closed_at(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.qg_chat_window_open(uuid) FROM PUBLIC, anon, authenticated;

REVOKE ALL ON FUNCTION public.qg_chat_postjob_allowed(uuid,uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.qg_chat_postjob_allowed(uuid,uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.qg_chat_read_allowed(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.qg_chat_read_allowed(uuid) TO authenticated;

COMMIT;
