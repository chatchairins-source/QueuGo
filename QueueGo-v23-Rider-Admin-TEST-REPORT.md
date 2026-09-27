# QueueGo v23 — Rider / Admin verification

27 September 2026 (Asia/Bangkok)

## Changes

- Admin: fixed the UI mapping (`users.status=active` becomes `approved` in the existing UI model), corrected seconds/milliseconds handling for session expiry, added a visible switch-to-Admin-login action for another role's session, and made the login page fit mobile width.
- Rider: if Leaflet fails to load, the rest of the app remains usable and shows a clear map fallback; online refresh no longer relies on `window.S` for a lexical `const S`; database errors appear to the user; missing GPS is explained; dates sent to the ledger RPC are YYYY-MM-DD even on Safari; job card route metrics attach to the actual DOM markup.
- Existing Customer root `index.html` was preserved. Database RLS, constraints, and finance RPCs were not relaxed.

## Checked

- `node --check` on every inline script: Admin 19, Rider 5, all passed; no duplicate static DOM IDs.
- Isolated Admin route test with a mocked Auth response: other-role session -> switch button -> login form -> active Admin route passed. No password was used.
- Isolated Rider fallback test without Leaflet: app map fallback renders without throwing.
- Supabase authenticated-role SQL check: active Admin's own row is visible; active Rider ledger and period summary RPCs return valid data. Transactions rolled back.

## Live browser checks

- To be recorded after GitHub Pages deployment. Actual Admin password login and active Rider session cannot be exercised without the user's credentials. No claim of real-user login verification is made.
