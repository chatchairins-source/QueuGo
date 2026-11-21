// The restore drill must never write to a remote Supabase project.
export function localRestoreStorageTarget(raw) {
  let url;
  try { url = new URL(raw); }
  catch { throw new Error('Storage restore requires an explicit local loopback target'); }
  if (!['http:', 'https:'].includes(url.protocol) ||
      !['127.0.0.1', '[::1]', 'localhost'].includes(url.hostname) ||
      url.username || url.password || url.pathname !== '/' || url.search || url.hash) {
    throw new Error('Storage restore requires a local loopback target without credentials, path, query or fragment');
  }
  return url.origin;
}
