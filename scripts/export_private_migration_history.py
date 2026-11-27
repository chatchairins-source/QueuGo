#!/usr/bin/env python3
"""Export historical Production migration SQL to a PRIVATE local file for review.

No migrations run, no Production writes, no SQL, credentials or file path in logs.
An operator must set QG_SUPABASE_DB_URL or standard libpq PG* environment
variables (ideally from a secure secret store, never a shell command argument).
Output parent MUST be an existing chmod-700 directory OUTSIDE this repository.
Usage:
  python3 scripts/export_private_migration_history.py \
    --output /path/to/chmod700-private-folder/history.json --expected-count 392
  python3 scripts/stage_missing_production_migrations.py \
    --history /path/to/chmod700-private-folder/history.json \
    --output /another/private-review-folder --expected-count 392
Never commit, upload, print, or blindly apply the exported SQL.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile

from stage_missing_production_migrations import check_history, load_private_history

# Exactly one SELECT result. psql's -X/-A/-t/-q yields only its JSON value.
# No source SQL, connection URL or credentials are passed on the command line.
HISTORY_SQL = """
SELECT json_build_object('migrations',
  COALESCE(json_agg(json_build_object(
    'version', version::text, 'name', name::text, 'statements', statements
  ) ORDER BY version), '[]'::json))
FROM supabase_migrations.schema_migrations;
"""


def export_history(output, expected_count, repo_root, psql="psql", environ=None):
    if type(expected_count) is not int or expected_count <= 0:
        raise ValueError("an independently checked positive migration count is required")
    repo = Path(repo_root).resolve()
    dest = Path(output).absolute()
    if dest.exists() or dest.is_symlink():
        raise ValueError("refusing to overwrite an existing export")
    if dest.parent.is_symlink() or not dest.parent.is_dir():
        raise ValueError("a real pre-existing private output directory is required")
    parent = dest.parent.resolve(strict=True)
    if parent.is_relative_to(repo):
        raise ValueError("Production migration SQL export may not be written into Git")
    if os.name == "posix" and parent.stat().st_mode & 0o077:
        raise ValueError("private export directory must be chmod 700")
    env = dict(os.environ if environ is None else environ)
    if env.get("QG_SUPABASE_DB_URL"):
        env["PGDATABASE"] = env["QG_SUPABASE_DB_URL"]
    if not env.get("PGDATABASE"):
        raise ValueError("set QG_SUPABASE_DB_URL or libpq PGDATABASE securely")
    # Server enforces READ ONLY. Never preserve caller PGOPTIONS that might
    # override safe settings. No ~/.psqlrc and no credential prompting.
    env["PGOPTIONS"] = "-c default_transaction_read_only=on -c statement_timeout=60000"
    env["PGCLIENTENCODING"] = "UTF8"
    env["PAGER"] = "cat"
    env["PSQL_PAGER"] = "cat"
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
    fd = os.open(dest, flags, 0o600)
    try:
        with os.fdopen(fd, "wb") as private_output:
            try:
                result = subprocess.run(
                    [psql, "-X", "-A", "-t", "-q", "-w", "-v", "ON_ERROR_STOP=1", "-c", HISTORY_SQL],
                    env=env, stdout=private_output, stderr=subprocess.PIPE, check=False, timeout=90,
                )
            except (subprocess.TimeoutExpired, OSError) as exc:
                raise RuntimeError("read-only SQL history export failed; inspect local connection settings") from None
        if result.returncode != 0:
            # stderr may include Production connection details: never print it.
            raise RuntimeError("read-only SQL history export failed; private stderr suppressed")
        history = load_private_history(dest, repo)
        rows = check_history(history)
        if len(rows) != expected_count:
            raise ValueError("private SQL history count differs from independently checked Production ledger")
        return len(rows)
    except Exception:
        dest.unlink(missing_ok=True)
        raise


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        base = Path(tmp)
        repo = base / "source"
        repo.mkdir()
        directory = base / "private"
        directory.mkdir(mode=0o700)
        fake = base / "fake-psql"
        fake.write_text(
            "#!/usr/bin/env python3\n"
            "import os,sys,json\n"
            "assert os.environ['PGOPTIONS'] == '-c default_transaction_read_only=on -c statement_timeout=60000'\n"
            "assert os.environ['PGCLIENTENCODING'] == 'UTF8'\n"
            "assert '-X' in sys.argv and '-w' in sys.argv and '-c' in sys.argv\n"
            "assert os.environ.get('PGDATABASE') == 'postgresql://fixture-only'\n"
            "if os.environ.get('FAKE_ERROR'):\n"
            "  print('SENSITIVE SHOULD NEVER REACH LOG',file=sys.stderr); sys.exit(2)\n"
            "print(json.dumps({'migrations':["
            "{'version':'20260101000000','name':'alpha','statements':['select 1;']},"
            "{'version':'20260101000001','name':'beta','statements':['select 2;']}"
            "]}))\n",
            encoding="utf-8",
        )
        fake.chmod(0o700)
        env = {"QG_SUPABASE_DB_URL": "postgresql://fixture-only", "FAKE_ERROR": ""}
        private_file = directory / "sql-history.json"
        assert export_history(private_file, 2, repo, str(fake), env) == 2
        assert private_file.is_file()
        if os.name == "posix":
            assert private_file.stat().st_mode & 0o077 == 0
        data = json.loads(private_file.read_text())
        assert len(data["migrations"]) == 2
        try:
            export_history(private_file, 2, repo, str(fake), env)
        except ValueError:
            pass
        else:
            raise AssertionError("existing export overwritten")
        for dest, reason in [
            (repo / "in-repo.json", "in-repo path"),
            (base / "missing-parent" / "nope.json", "missing parent"),
        ]:
            try:
                export_history(dest, 2, repo, str(fake), env)
            except ValueError:
                pass
            else:
                raise AssertionError(reason + " accepted")
        try:
            export_history(directory / "partial.json", 3, repo, str(fake), env)
        except ValueError:
            pass
        else:
            raise AssertionError("partial SQL history accepted")
        assert not (directory / "partial.json").exists()
        try:
            export_history(directory / "failed.json", 2, repo, str(fake), {**env, "FAKE_ERROR": "1"})
        except RuntimeError as error:
            assert "SENSITIVE" not in str(error)
        else:
            raise AssertionError("psql failure accepted")
        assert not (directory / "failed.json").exists()
        if os.name == "posix":
            public = base / "world-readable-dir"
            public.mkdir(mode=0o755)
            try:
                export_history(public / "leak.json", 2, repo, str(fake), env)
            except ValueError:
                pass
            else:
                raise AssertionError("unsafe directory accepted")
        try:
            export_history(directory / "missing-credentials.json", 2, repo, str(fake), {})
        except ValueError:
            pass
        else:
            raise AssertionError("missing connection refused incorrectly")
    print("PASS: read-only private history export; secure file mode, fail-closed count and no raw SQL logs")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--expected-count", type=int)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if args.output is None or args.expected_count is None:
        parser.error("--output and --expected-count are required")
    try:
        total = export_history(args.output, args.expected_count, Path(__file__).resolve().parents[1])
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError):
        raise SystemExit("Export NOT completed; no Production SQL or connection data logged")
    print("PASS: " + str(total) + " migration SQL records privately exported for review ONLY; no migration executed")


if __name__ == "__main__":
    main()
