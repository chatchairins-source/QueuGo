"""Fail closed when the checked-in Production security review is stale or partial."""
from __future__ import annotations

import argparse
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EXPECTED_PROJECT = "pkypiqhlrmzocysgeqew"
MAX_AGE = timedelta(hours=24)


def parse_time(value: object, label: str) -> datetime:
    if not isinstance(value, str) or not value:
        raise ValueError(f"{label} is missing")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ValueError(f"{label} must be ISO-8601") from error
    if parsed.tzinfo is None:
        raise ValueError(f"{label} must include a timezone")
    return parsed.astimezone(timezone.utc)


def _nonnegative_count(value: object, label: str) -> int:
    if type(value) is not int or value < 0:
        raise ValueError(f"{label} must be a non-negative integer")
    return value


def verify_production_security_review(
    review_path: Path,
    allowlist_path: Path,
    now: datetime | None = None,
    expected_project: str = EXPECTED_PROJECT,
) -> dict:
    """Require a fresh complete review whose counts match the live Advisor snapshot."""
    try:
        review = json.loads(review_path.read_text(encoding="utf-8"))
        allowlist = json.loads(allowlist_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError("Production security review or anonymous allowlist is unavailable/invalid") from error
    if not isinstance(review, dict) or not isinstance(allowlist, dict):
        raise ValueError("Production security review and anonymous allowlist must be JSON objects")
    if review.get("production_project") != expected_project:
        raise ValueError("Production security review project mismatch")

    snapshot = review.get("current_live_advisor_snapshot")
    if not isinstance(snapshot, dict):
        raise ValueError("current Production security Advisor snapshot is missing")
    observed = parse_time(snapshot.get("observed_at"), "live Advisor observed_at")
    current = now or datetime.now(timezone.utc)
    if current.tzinfo is None:
        raise ValueError("security review validation time must include a timezone")
    current = current.astimezone(timezone.utc)
    if observed > current + timedelta(minutes=5):
        raise ValueError("live Advisor snapshot is from the future")
    if current - observed > MAX_AGE:
        raise ValueError("live Advisor snapshot is stale; refresh and review Production before release")

    counts = snapshot.get("counts")
    reviewed = review.get("reviewed_surface")
    if not isinstance(counts, dict) or not isinstance(reviewed, dict):
        raise ValueError("current Advisor counts or reviewed function surface is missing")
    live_auth = _nonnegative_count(
        counts.get("authenticated_security_definer_function_executable"),
        "live authenticated SECURITY DEFINER Advisor count",
    )
    live_anon = _nonnegative_count(
        counts.get("anon_security_definer_function_executable"),
        "live anon SECURITY DEFINER Advisor count",
    )
    _nonnegative_count(counts.get("auth_leaked_password_protection"), "live leaked-password protection Advisor count")
    reviewed_auth = _nonnegative_count(
        reviewed.get("authenticated_security_definer_total"),
        "reviewed authenticated SECURITY DEFINER count",
    )
    reviewed_anon = _nonnegative_count(
        reviewed.get("anonymous_security_definer_total"),
        "reviewed anonymous SECURITY DEFINER count",
    )
    if reviewed_auth != live_auth:
        raise ValueError(
            f"reviewed authenticated SECURITY DEFINER count {reviewed_auth} does not match current live Advisor count {live_auth}"
        )
    if reviewed_anon != live_anon:
        raise ValueError(
            f"reviewed anonymous SECURITY DEFINER count {reviewed_anon} does not match current live Advisor count {live_anon}"
        )

    reviewed_at = parse_time(review.get("reviewed_at"), "security review reviewed_at")
    if reviewed_at > current + timedelta(minutes=5):
        raise ValueError("security review reviewed_at is from the future")
    if reviewed_at < observed:
        raise ValueError("security review predates the current live Advisor snapshot")

    classification = review.get("classification")
    if not isinstance(classification, dict):
        raise ValueError("Production SECURITY DEFINER classification is missing")
    direct = _nonnegative_count(
        review.get("direct_identity_or_role_guard_count"), "direct/helper-guarded function count"
    )
    direct_auth_uid = _nonnegative_count(review.get("direct_auth_uid_count"), "direct auth.uid function count")
    helper_guarded = _nonnegative_count(
        review.get("helper_guarded_without_direct_auth_uid_count"), "helper-guarded function count"
    )
    unguarded = _nonnegative_count(
        review.get("no_direct_identity_guard_count"), "downstream/public-guarded function count"
    )
    if direct_auth_uid + helper_guarded != direct:
        raise ValueError("direct and helper-guarded function counts do not reconcile")
    if direct + unguarded != live_auth:
        raise ValueError("reviewed authenticated function classifications do not cover the live Advisor count")

    public_review = classification.get("intentional_public_or_guest")
    allow_rules = allowlist.get("rules")
    if not isinstance(public_review, list) or not isinstance(allow_rules, list):
        raise ValueError("anonymous SECURITY DEFINER review/allowlist is missing")
    reviewed_public = [item.get("signature") for item in public_review if isinstance(item, dict)]
    allowed_public = [item.get("signature") for item in allow_rules if isinstance(item, dict)]
    if len(reviewed_public) != len(public_review) or len(allowed_public) != len(allow_rules):
        raise ValueError("anonymous SECURITY DEFINER entries must have exact signatures")
    if len(set(reviewed_public)) != len(reviewed_public) or len(set(allowed_public)) != len(allowed_public):
        raise ValueError("anonymous SECURITY DEFINER review contains duplicate signatures")
    if sorted(reviewed_public) != sorted(allowed_public) or len(allowed_public) != live_anon:
        raise ValueError("anonymous SECURITY DEFINER review does not exactly match the current live Advisor surface")

    downstream = classification.get("downstream_guarded_authenticated_wrapper")
    pos_wrappers = classification.get("downstream_guarded_pos_or_table_wrappers")
    if not isinstance(downstream, list) or not isinstance(pos_wrappers, list):
        raise ValueError("downstream authenticated SECURITY DEFINER classifications are missing")
    if len(reviewed_public) + len(downstream) + len(pos_wrappers) != unguarded:
        raise ValueError("public, POS/table and downstream wrapper counts do not cover the reviewed unguarded surface")

    live_review = review.get("live_review")
    if not isinstance(live_review, dict):
        raise ValueError("live Production security review details are missing")
    if live_review.get("anonymous_allowlist_exact_match") is not True:
        raise ValueError("live anonymous SECURITY DEFINER set is not exactly reviewed")
    if live_review.get("database_changes_required") is not False:
        raise ValueError("live SECURITY DEFINER review has unresolved database changes")
    if live_review.get("unexpected_anonymous_security_definers") != []:
        raise ValueError("live anonymous SECURITY DEFINER surface contains unreviewed functions")
    if review.get("review_status") != "PASS":
        raise ValueError(f"Production security review is blocked: {review.get('review_status', 'status missing')}")
    if review.get("conclusion") != "PASS_CODE_DATABASE_REVIEW_CURRENT_SURFACE":
        raise ValueError("Production security review conclusion is not a current-surface PASS")
    return review


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--review", type=Path, default=ROOT / "docs/security-auth-definer-review.json")
    parser.add_argument("--allowlist", type=Path, default=ROOT / "docs/security-anon-definer-allowlist.json")
    parser.add_argument("--now", help="validation time for deterministic regression tests")
    args = parser.parse_args()
    try:
        now = parse_time(args.now, "validation time") if args.now else None
        verify_production_security_review(args.review, args.allowlist, now=now)
    except (ValueError, OSError) as error:
        print(f"::error::Production security review failed closed: {error}")
        return 1
    print("PASS current Production SECURITY DEFINER surface and anonymous allowlist reviewed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
