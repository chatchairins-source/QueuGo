"""Fail closed before Native release packaging; never manufacture certification."""
import hashlib
import json
import os
import re
import secrets
from pathlib import Path
import subprocess
import sys
import tempfile
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


GATES = (
    "full_native_ci", "production_backend", "backup_restore", "security_regression",
    "service_area", "ugc_chat_safety", "security_platform_auth",
    "firebase_three_packages", "physical_push_customer", "physical_push_merchant",
    "physical_push_rider", "physical_voice_two_devices_two_networks", "turn_relay",
    "voice_session_order_block_authorization", "rider_floating_q",
    "customer_blueprint", "merchant_blueprint", "rider_blueprint",
    "android_lifecycle_permissions_upload_location", "privacy_data_safety_account_deletion",
    "play_store_preflight", "release_signing",
)

PHYSICAL_GATES = {
    "physical_push_customer", "physical_push_merchant", "physical_push_rider",
    "physical_voice_two_devices_two_networks", "rider_floating_q",
    "customer_blueprint", "merchant_blueprint", "rider_blueprint",
    "android_lifecycle_permissions_upload_location",
}

PHYSICAL_GATE_REQUIRED_ROLES = {
    "physical_push_customer": {"customer"},
    "physical_push_merchant": {"merchant"},
    "physical_push_rider": {"rider"},
    "rider_floating_q": {"rider"},
    "customer_blueprint": {"customer"},
    "merchant_blueprint": {"merchant"},
    "rider_blueprint": {"rider"},
    "android_lifecycle_permissions_upload_location": {"customer", "merchant", "rider"},
}


def bundle_file(bundle_root: Path, relative: str, label: str) -> Path:
    rel = Path(str(relative))
    if rel.is_absolute() or ".." in rel.parts:
        raise ValueError(f"{label} must stay inside the certification bundle")
    resolved = (bundle_root / rel).resolve()
    if resolved.parent != bundle_root and bundle_root not in resolved.parents:
        raise ValueError(f"{label} escapes the certification bundle")
    if not resolved.is_file():
        raise ValueError(f"{label} file is unavailable")
    return resolved


def verify_gate_envelope(path: Path, bundle_root: Path, gate: str, head: str, expected_status: str):
    if path.suffix.lower() != ".json":
        raise ValueError(f"gate evidence must be a JSON envelope: {gate}")
    try:
        envelope = json.loads(path.read_text())
    except (json.JSONDecodeError, UnicodeDecodeError) as error:
        raise ValueError(f"gate evidence JSON is invalid: {gate}") from error
    if not isinstance(envelope, dict):
        raise ValueError(f"gate evidence envelope must be an object: {gate}")
    if envelope.get("gate") != gate:
        raise ValueError(f"gate evidence envelope mismatch: {gate}")
    if envelope.get("source_sha") != head:
        raise ValueError(f"gate evidence source_sha mismatch: {gate}")
    if envelope.get("status") != expected_status:
        raise ValueError(f"gate evidence status mismatch: {gate}")

    observed_at = envelope.get("observed_at")
    if not isinstance(observed_at, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z", observed_at):
        raise ValueError(f"gate evidence observed_at must be UTC ISO-8601 seconds: {gate}")
    try:
        observed = datetime.strptime(observed_at, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    except ValueError as error:
        raise ValueError(f"gate evidence observed_at is invalid: {gate}") from error
    if observed > datetime.now(timezone.utc):
        raise ValueError(f"gate evidence observed_at cannot be in the future: {gate}")

    checks = envelope.get("checks")
    if not isinstance(checks, dict) or not checks or any(value is not True for value in checks.values()):
        raise ValueError(f"gate evidence checks must be non-empty and all true: {gate}")

    artifacts = envelope.get("artifacts")
    if not isinstance(artifacts, list) or not artifacts:
        raise ValueError(f"gate evidence must reference at least one artifact: {gate}")
    seen_artifacts = set()
    for index, artifact in enumerate(artifacts):
        if not isinstance(artifact, dict):
            raise ValueError(f"gate artifact entry must be an object: {gate}")
        artifact_rel = artifact.get("file")
        artifact_digest = str(artifact.get("sha256", "")).lower()
        artifact_kind = artifact.get("kind")
        if not isinstance(artifact_rel, str) or not artifact_rel:
            raise ValueError(f"gate artifact file is missing: {gate}")
        if artifact_rel in seen_artifacts:
            raise ValueError(f"gate evidence contains duplicate artifact paths: {gate}")
        seen_artifacts.add(artifact_rel)
        if not isinstance(artifact_kind, str) or not re.fullmatch(r"[a-z0-9_-]{2,40}", artifact_kind):
            raise ValueError(f"gate artifact kind is invalid: {gate}")
        if not re.fullmatch(r"[0-9a-f]{64}", artifact_digest):
            raise ValueError(f"gate artifact SHA-256 is malformed: {gate}")
        artifact_path = bundle_file(bundle_root, artifact_rel, f"gate artifact {gate}[{index}]")
        if sha256_file(artifact_path) != artifact_digest:
            raise ValueError(f"gate artifact hash mismatch: {gate}")

    if gate in PHYSICAL_GATES:
        devices = envelope.get("devices")
        if not isinstance(devices, list) or not devices:
            raise ValueError(f"physical gate evidence must identify at least one device: {gate}")
        device_ids = set()
        observed_roles = set()
        for device in devices:
            if not isinstance(device, dict):
                raise ValueError(f"physical gate device entry must be an object: {gate}")
            device_hash = str(device.get("device_id_hash", "")).lower()
            api = device.get("android_api")
            model = device.get("model")
            role = device.get("role")
            if not re.fullmatch(r"[0-9a-f]{64}", device_hash):
                raise ValueError(f"physical gate device_id_hash is invalid: {gate}")
            if type(api) is not int or api < 23 or api > 100:
                raise ValueError(f"physical gate android_api is invalid: {gate}")
            if not isinstance(model, str) or not model.strip():
                raise ValueError(f"physical gate device model is missing: {gate}")
            if role not in {"customer", "merchant", "rider"}:
                raise ValueError(f"physical gate device role is invalid: {gate}")
            device_ids.add(device_hash)
            observed_roles.add(role)
        required_roles = PHYSICAL_GATE_REQUIRED_ROLES.get(gate, set())
        if not required_roles.issubset(observed_roles):
            missing_roles = ", ".join(sorted(required_roles - observed_roles))
            raise ValueError(f"physical gate evidence is missing required roles ({missing_roles}): {gate}")
        if gate == "physical_voice_two_devices_two_networks":
            if len(device_ids) < 2:
                raise ValueError("physical voice gate requires two distinct devices")
            networks = envelope.get("networks")
            if not isinstance(networks, list) or len(networks) < 2:
                raise ValueError("physical voice gate requires two network observations")
            network_ids = set()
            for network in networks:
                if not isinstance(network, dict):
                    raise ValueError("physical voice network entry must be an object")
                network_hash = str(network.get("network_id_hash", "")).lower()
                network_type = network.get("type")
                if not re.fullmatch(r"[0-9a-f]{64}", network_hash):
                    raise ValueError("physical voice network_id_hash is invalid")
                if network_type not in {"wifi", "mobile", "ethernet", "other"}:
                    raise ValueError("physical voice network type is invalid")
                network_ids.add(network_hash)
            if len(network_ids) < 2:
                raise ValueError("physical voice gate requires two distinct networks")

    return envelope



def verify():
    report_path = os.environ.get("QG_NATIVE_RELEASE_EVIDENCE")
    if not report_path:
        raise ValueError("QG_NATIVE_RELEASE_EVIDENCE absent; physical and Play gates remain OPEN")
    report_file = Path(report_path).resolve()
    report = json.loads(report_file.read_text())
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    if report.get("source_sha") != head:
        raise ValueError("certification source_sha must match the exact release HEAD")
    if subprocess.check_output(["git", "status", "--porcelain", "--untracked-files=normal"], cwd=ROOT, text=True).strip():
        raise ValueError("release checkout must be clean; store evidence/credentials outside git")
    if any(type(report.get(key)) is not int or report[key] != 0 for key in ("p0", "p1")):
        raise ValueError("P0/P1 must both equal zero")
    envelopes = {}
    for name in GATES:
        item = report.get("gates", {}).get(name, {})
        status = item.get("status")
        allowed_statuses = {"PASS", "PASS_FREE_PLAN_CONTROLS"} if name == "security_platform_auth" else {"PASS"}
        if status not in allowed_statuses:
            raise ValueError(f"uncertified gate: {name}")
        evidence = item.get("evidence_file")
        digest = item.get("sha256")
        if not evidence or not digest:
            raise ValueError(f"gate has no verifiable evidence: {name}")
        if not re.fullmatch(r"[0-9a-fA-F]{64}", str(digest)):
            raise ValueError(f"gate evidence SHA-256 is malformed: {name}")
        bundle_root = report_file.parent.resolve()
        path = bundle_file(bundle_root, str(evidence), f"gate evidence {name}")
        if sha256_file(path) != str(digest).lower():
            raise ValueError(f"evidence hash mismatch: {name}")
        envelopes[name] = verify_gate_envelope(path, bundle_root, name, head, status)

    pilot_run_raw = os.environ.get("QG_CERTIFIED_NATIVE_PILOT_RUN_ID", "")
    if not re.fullmatch(r"\d+", pilot_run_raw):
        raise ValueError("QG_CERTIFIED_NATIVE_PILOT_RUN_ID must come from exact-HEAD GitHub attestation")
    if envelopes["full_native_ci"].get("github_run_id") != int(pilot_run_raw):
        raise ValueError("full_native_ci evidence does not match the attested Native Pilot run")
    if envelopes["production_backend"].get("supabase_project_ref") != "pkypiqhlrmzocysgeqew":
        raise ValueError("production_backend evidence must identify QueueGo Production Supabase")

    version_name = os.environ.get("QG_NATIVE_VERSION_NAME", "")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version_name):
        raise ValueError("release versionName must be explicitly set to x.y.z")
    release_codes = {}
    play_max_codes = {}
    projects = set()
    for role in ("customer", "merchant", "rider"):
        release_name = f"QG_{role.upper()}_VERSION_CODE"
        play_max_name = f"QG_{role.upper()}_PLAY_MAX_VERSION_CODE"
        release_raw = os.environ.get(release_name, "")
        play_max_raw = os.environ.get(play_max_name, "")
        if not re.fullmatch(r"\d+", release_raw):
            raise ValueError(f"{release_name} must be an explicit positive integer")
        if not re.fullmatch(r"\d+", play_max_raw):
            raise ValueError(f"{play_max_name} must be the observed highest Play versionCode")
        version_code = int(release_raw)
        play_max = int(play_max_raw)
        if version_code <= 0 or version_code > 2100000000:
            raise ValueError(f"release versionCode is out of range for {role}")
        if version_code <= play_max:
            raise ValueError(f"release versionCode must exceed observed Play history for {role}")
        release_codes[role] = version_code
        play_max_codes[role] = play_max
        path = ROOT / "native-android" / role / "google-services.json"
        config = json.loads(path.read_text())
        project_id = config.get("project_info", {}).get("project_id")
        if not project_id:
            raise ValueError(f"Firebase project identity is missing for {role}")
        projects.add(project_id)
        clients = config.get("client", [])
        package = f"com.queuego.{role}"
        matches = [c for c in clients if c.get("client_info", {}).get("android_client_info", {}).get("package_name") == package]
        if len(matches) != 1 or not matches[0].get("client_info", {}).get("mobilesdk_app_id"):
            raise ValueError(f"Firebase config requires one complete client: {package}")
    if len(projects) != 1:
        raise ValueError("all Native Firebase configs must use the certified Production project")
    firebase_project_id = next(iter(projects))
    if envelopes["firebase_three_packages"].get("firebase_project_id") != firebase_project_id:
        raise ValueError("firebase_three_packages evidence does not match the loaded Firebase project")

    play_envelope = envelopes["play_store_preflight"]
    if play_envelope.get("version_name") != version_name:
        raise ValueError("play_store_preflight evidence versionName mismatch")
    if play_envelope.get("release_version_codes") != release_codes:
        raise ValueError("play_store_preflight evidence release versionCodes mismatch")
    if play_envelope.get("observed_play_max_version_codes") != play_max_codes:
        raise ValueError("play_store_preflight evidence Play history mismatch")

    required = (
        "QG_ANDROID_KEYSTORE_PATH", "QG_ANDROID_STORE_PASSWORD",
        "QG_ANDROID_KEY_ALIAS", "QG_ANDROID_KEY_PASSWORD",
        "QG_ANDROID_SIGNING_CERT_SHA256",
    )
    if any(not os.environ.get(key) for key in required):
        raise ValueError("Native release signing environment is incomplete")
    if not Path(os.environ["QG_ANDROID_KEYSTORE_PATH"]).is_file():
        raise ValueError("Native release keystore is unavailable")
    # keytool reads the password from environment, never argv or logs.
    entry = subprocess.run([
                    "keytool", "-J-Duser.language=en", "-J-Duser.country=US",
                    "-list", "-v", "-keystore", os.environ["QG_ANDROID_KEYSTORE_PATH"],
                    "-storepass:env", "QG_ANDROID_STORE_PASSWORD",
                    "-alias", os.environ["QG_ANDROID_KEY_ALIAS"]],
                   check=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    if "PrivateKeyEntry" not in entry.stdout:
        raise ValueError("release alias is not a signing private-key entry")
    expected_cert = re.sub(r"[^0-9a-fA-F]", "", os.environ["QG_ANDROID_SIGNING_CERT_SHA256"]).lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected_cert):
        raise ValueError("QG_ANDROID_SIGNING_CERT_SHA256 must be a 32-byte SHA-256 fingerprint")
    signing_evidence_cert = re.sub(r"[^0-9a-fA-F]", "", str(envelopes["release_signing"].get("signing_certificate_sha256", ""))).lower()
    if signing_evidence_cert != expected_cert:
        raise ValueError("release_signing evidence does not match the certified signing identity")
    fingerprint = re.search(r"SHA256:\s*([0-9A-Fa-f:]+)", entry.stdout)
    if not fingerprint:
        raise ValueError("release signing certificate SHA-256 is unavailable")
    actual_cert = fingerprint.group(1).replace(":", "").lower()
    if actual_cert != expected_cert:
        raise ValueError("release signing certificate does not match the certified identity")
    with tempfile.TemporaryDirectory(prefix="queuego-signing-check-") as directory:
        validation_env = dict(os.environ, QG_VALIDATION_PASSWORD=secrets.token_urlsafe(32))
        subprocess.run(["keytool", "-importkeystore", "-srckeystore", os.environ["QG_ANDROID_KEYSTORE_PATH"],
                        "-srcstorepass:env", "QG_ANDROID_STORE_PASSWORD", "-srcalias", os.environ["QG_ANDROID_KEY_ALIAS"],
                        "-srckeypass:env", "QG_ANDROID_KEY_PASSWORD", "-destkeystore", str(Path(directory)/"check.p12"),
                        "-deststoretype", "PKCS12", "-deststorepass:env", "QG_VALIDATION_PASSWORD",
                        "-destkeypass:env", "QG_VALIDATION_PASSWORD", "-noprompt"],
                       env=validation_env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print("Native release evidence and prerequisites verified; physical evidence is operator-certified, not inferred from CI")


if __name__ == "__main__":
    try:
        verify()
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as error:
        print(f"Native release BLOCKED: {error}", file=sys.stderr)
        sys.exit(1)
