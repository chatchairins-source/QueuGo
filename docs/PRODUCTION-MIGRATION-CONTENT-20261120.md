# Production migration content receipt — 2026-11-20

An independent SQL-body digest export is now available. Supabase Production
`schema_migrations.statements` contains exactly **one stored SQL body for each
of 392 versions**, totalling 2,500,594 UTF-8 bytes. The read-only export hashes
those stored bytes on the server; it does not return SQL bodies or business rows.

- Query: `ops/p0/export-migration-sql-digests-readonly.sql`.
- Manifest: `docs/qa/production-migration-sql-digests-20261120.json`.
- Comparison: `docs/qa/production-migration-content-comparison-20261120.json`.
- Source tree compared: Integration #1072 at `7e7761649654eb4c8cf7f4a4d67e0fb3f2cac5e4`.
- Verifier: unchanged `scripts/verify_migration_sql_digests.py` from PR #1241,
  branch `audit/backend-digest-symlink-guard-20261118` (not a new duplicate gate).

| Byte-exact check | Result |
|---|---:|
| Production versions | 392 |
| Current source versions | 258 |
| Same-version, same-byte SHA-256 | 59 |
| Same-version, differing-byte SHA-256 | 92 |
| Production-only versions | 241 |
| Source-only versions | 107 |
| Invalid source filenames | 0 |
| Full byte parity | FAIL |

The prior assumption that the MCP can only provide names/versions is incorrect:
read-only SQL can hash the stored SQL body. This removes the missing-independent-
digest input blocker for the existing backend reconciliation workstream.

These hashes prove stored migration-history **content**, not current schema
equivalence, successful restoration or absence of later manual DDL. Differing
bytes may be whitespace/comments or executable changes; investigate rather
than calling all 92 mismatches semantic defects. Never replace source SQL,
repair history or apply the 107 source-only files in bulk to make this table green.

Backend follow-up: review missing versions and same-version mismatches against
the actual catalog, retain pending source work explicitly, reconcile each
accepted migration without losing provenance, then prove restore and staged
compatibility. This export is **not** a database/Storage backup or deployment
authorization. No Production DDL or data write occurred.
