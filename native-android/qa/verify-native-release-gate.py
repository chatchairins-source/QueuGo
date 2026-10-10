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
        evidence_rel = Path(str(evidence))
        if evidence_rel.is_absolute() or ".." in evidence_rel.parts:
            raise ValueError(f"gate evidence must stay inside the certification bundle: {name}")
        if not re.fullmatch(r"[0-9a-fA-F]{64}", str(digest)):
            raise ValueError(f"gate evidence SHA-256 is malformed: {name}")
        bundle_root = report_file.parent.resolve()
        path = (bundle_root / evidence_rel).resolve()
        if path.parent != bundle_root and bundle_root not in path.parents:
            raise ValueError(f"gate evidence escapes the certification bundle: {name}")
        if not path.is_file():
            raise ValueError(f"gate evidence file is unavailable: {name}")
        if sha256_file(path) != str(digest).lower():
            raise ValueError(f"evidence hash mismatch: {name}")
    if not re.fullmatch(r"\d+\.\d+\.\d+", os.environ.get("QG_NATIVE_VERSION_NAME", "")):
        raise ValueError("release versionName must be explicitly set to x.y.z")
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
