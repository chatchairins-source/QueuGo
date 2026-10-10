"""Validate semantic completeness of operator-captured Native physical evidence.

This never manufactures PASS. It only rejects incomplete/inconsistent physical evidence
before the canonical release gate may accept operator certification.
"""
from __future__ import annotations

import hashlib
import json
import re
from datetime import datetime
from pathlib import Path
from typing import Any

SCHEMA_VERSION = 1
ROLES = ("customer", "merchant", "rider")
PHYSICAL_GATE_NAMES = (
    "physical_push_customer",
    "physical_push_merchant",
    "physical_push_rider",
    "physical_voice_two_devices_two_networks",
    "turn_relay",
    "voice_session_order_block_authorization",
    "rider_floating_q",
    "customer_blueprint",
    "merchant_blueprint",
    "rider_blueprint",
    "android_lifecycle_permissions_upload_location",
)
PHYSICAL_MANIFEST = "native-physical-evidence.json"
HEX64 = re.compile(r"^[0-9a-f]{64}$")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def safe_bundle_file(bundle_root: Path, rel: str, digest: str, label: str) -> Path:
    require(isinstance(rel, str) and rel, f"{label} evidence file missing")
    path_rel = Path(rel)
    require(not path_rel.is_absolute() and ".." not in path_rel.parts, f"{label} evidence path traversal")
    require(isinstance(digest, str) and bool(HEX64.fullmatch(digest.lower())), f"{label} evidence SHA-256 malformed")
    target = (bundle_root / path_rel).resolve()
    require(target == bundle_root or bundle_root in target.parents, f"{label} evidence escapes bundle")
    require(target.is_file(), f"{label} evidence file unavailable: {rel}")
    require(target.name not in {"native-release-evidence.json", PHYSICAL_MANIFEST}, f"{label} evidence must be a captured artifact")
    require(sha256_file(target) == digest.lower(), f"{label} evidence hash mismatch: {rel}")
    return target


def verify_evidence_list(bundle_root: Path, items: Any, label: str, minimum: int = 1) -> None:
    require(isinstance(items, list) and len(items) >= minimum, f"{label} requires at least {minimum} captured evidence file(s)")
    seen = set()
    for index, item in enumerate(items):
        require(isinstance(item, dict), f"{label} evidence item {index} must be an object")
        rel = item.get("file")
        digest = item.get("sha256")
        require(rel not in seen, f"{label} repeats evidence file: {rel}")
        seen.add(rel)
        safe_bundle_file(bundle_root, rel, digest, f"{label}[{index}]")


def require_true_map(value: Any, keys: tuple[str, ...], label: str) -> None:
    require(isinstance(value, dict), f"{label} must be an object")
    for key in keys:
        require(value.get(key) is True, f"{label}.{key} must be observed true")


def parse_captured_at(value: Any) -> None:
    require(isinstance(value, str) and value, "physical captured_at missing")
    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as exc:
        raise ValueError("physical captured_at must be ISO-8601") from exc
    require(parsed.tzinfo is not None, "physical captured_at must include timezone")


def verify_physical_evidence(report: dict[str, Any], report_file: Path, source_sha: str) -> None:
    bundle_root = report_file.parent.resolve()
    physical_path = (bundle_root / PHYSICAL_MANIFEST).resolve()
    require(physical_path.is_file(), f"{PHYSICAL_MANIFEST} missing from certification bundle")
    physical = json.loads(physical_path.read_text())

    require(physical.get("schema_version") == SCHEMA_VERSION, "physical evidence schema_version unsupported")
    require(physical.get("source_sha") == source_sha, "physical evidence source_sha must match exact release HEAD")
    parse_captured_at(physical.get("captured_at"))
    require(physical.get("operator_certified") is True, "physical evidence must be explicitly operator-certified")
    require(physical.get("p0") == 0 and physical.get("p1") == 0, "physical evidence P0/P1 must both equal zero")

    # Every physical gate must be backed by this same semantic manifest.
    manifest_sha = sha256_file(physical_path)
    gates = report.get("gates", {})
    for gate in PHYSICAL_GATE_NAMES:
        item = gates.get(gate, {})
        require(item.get("evidence_file") == PHYSICAL_MANIFEST, f"{gate} must reference {PHYSICAL_MANIFEST}")
        require(str(item.get("sha256", "")).lower() == manifest_sha, f"{gate} physical manifest hash mismatch")

    devices = physical.get("devices")
    require(isinstance(devices, list) and len(devices) >= 2, "physical evidence requires at least two distinct Android devices")
    device_ids = set()
    role_coverage = set()
    for index, device in enumerate(devices):
        require(isinstance(device, dict), f"devices[{index}] must be an object")
        device_id = str(device.get("device_id_sha256", "")).lower()
        require(bool(HEX64.fullmatch(device_id)), f"devices[{index}].device_id_sha256 malformed")
        require(device_id not in device_ids, "physical device identity repeated")
        device_ids.add(device_id)
        require(device.get("physical") is True and device.get("emulator") is False, f"devices[{index}] must be a real non-emulator device")
        require(isinstance(device.get("android_api"), int) and device["android_api"] > 0, f"devices[{index}].android_api invalid")
        require(isinstance(device.get("model"), str) and device["model"].strip(), f"devices[{index}].model missing")
        roles = device.get("roles")
        require(isinstance(roles, list) and roles, f"devices[{index}].roles missing")
        for role in roles:
            require(role in ROLES, f"devices[{index}] has unknown role {role}")
            role_coverage.add(role)
        verify_evidence_list(bundle_root, device.get("evidence"), f"devices[{index}]", 1)
    require(role_coverage == set(ROLES), "physical device evidence must cover Customer, Merchant and Rider")

    checks = physical.get("checks")
    require(isinstance(checks, dict), "physical checks missing")

    push = checks.get("push")
    require(isinstance(push, dict), "physical push checks missing")
    push_keys = (
        "foreground",
        "background",
        "killed",
        "refresh_login",
        "logout_revocation",
        "stale_token_exclusion",
        "order_notification",
        "call_notification",
    )
    for role in ROLES:
        row = push.get(role)
        require_true_map(row, push_keys, f"checks.push.{role}")
        verify_evidence_list(bundle_root, row.get("evidence"), f"checks.push.{role}", 2)

    voice = checks.get("voice")
    require_true_map(
        voice,
        (
            "two_devices",
            "two_networks",
            "bidirectional_audio",
            "forced_turn_relay",
            "foreground_background",
            "controls_complete",
            "hangup_cleanup",
            "session_order_block_authorization",
        ),
        "checks.voice",
    )
    voice_devices = voice.get("device_ids_sha256")
    require(isinstance(voice_devices, list) and len(set(voice_devices)) >= 2, "voice evidence must identify two distinct devices")
    require(set(voice_devices).issubset(device_ids), "voice device identity is not present in devices[]")
    verify_evidence_list(bundle_root, voice.get("evidence"), "checks.voice", 3)

    floating = checks.get("rider_floating_q")
    require_true_map(
        floating,
        (
            "overlay_granted_path",
            "overlay_denied_path",
            "tap_returns_to_active_job",
            "persistent_notification_return",
            "stops_outside_active_work",
        ),
        "checks.rider_floating_q",
    )
    verify_evidence_list(bundle_root, floating.get("evidence"), "checks.rider_floating_q", 2)

    blueprint = checks.get("blueprint")
    require(isinstance(blueprint, dict), "physical blueprint checks missing")
    for role in ROLES:
        row = blueprint.get(role)
        require_true_map(
            row,
            ("all_required_screens_observed", "all_required_states_observed", "pixel_diff_reviewed"),
            f"checks.blueprint.{role}",
        )
        require(type(row.get("blocking_differences")) is int and row["blocking_differences"] == 0, f"checks.blueprint.{role}.blocking_differences must equal zero")
        verify_evidence_list(bundle_root, row.get("evidence"), f"checks.blueprint.{role}", 2)

    lifecycle = checks.get("lifecycle")
    require(isinstance(lifecycle, dict), "physical lifecycle checks missing")
    lifecycle_keys = (
        "permissions",
        "single_back_to_home",
        "upload",
        "location",
        "session_persistence",
        "offline_timeout",
        "reconnect",
        "background_foreground",
    )
    for role in ROLES:
        row = lifecycle.get(role)
        require_true_map(row, lifecycle_keys, f"checks.lifecycle.{role}")
        verify_evidence_list(bundle_root, row.get("evidence"), f"checks.lifecycle.{role}", 2)

    revocation = checks.get("order_session_block_private_topic_revocation")
    require_true_map(
        revocation,
        ("real_order", "session_revocation", "block_revocation", "private_topic_revocation"),
        "checks.order_session_block_private_topic_revocation",
    )
    verify_evidence_list(bundle_root, revocation.get("evidence"), "checks.order_session_block_private_topic_revocation", 2)
