"""Verify the actual signed Native Android release artifacts after packaging.

This verifier does not replace the pre-build physical/Play release gate. It runs
after release APK/AAB creation and proves that the files we intend to retain
match the certified package/version metadata, are non-debuggable, are signed,
and have stable hashes. It never reads or prints signing passwords.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / "native-android"
ROLES = {
    "customer": "com.queuego.customer",
    "merchant": "com.queuego.merchant",
    "rider": "com.queuego.rider",
}
PACKAGE_RE = re.compile(
    r"package: name='(?P<package>[^']+)' versionCode='(?P<code>[^']+)' versionName='(?P<name>[^']+)'"
)
CERT_RE = re.compile(r"^Signer #1 certificate SHA-256 digest:\s*(?P<digest>[0-9a-fA-F:]+)\s*$", re.MULTILINE)
KEYTOOL_SHA256_RE = re.compile(r"SHA256:\s*(?P<digest>[0-9a-fA-F:]+)")


def run(args: list[str]) -> str:
    completed = subprocess.run(
        args,
        cwd=ROOT,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    return completed.stdout


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def version_key(path: Path) -> tuple[int, ...]:
    parts = []
    for token in re.split(r"[^0-9]+", path.name):
        if token:
            parts.append(int(token))
    return tuple(parts)


def android_build_tool(name: str) -> str:
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        raise ValueError("ANDROID_HOME or ANDROID_SDK_ROOT is required")
    build_tools = Path(sdk) / "build-tools"
    candidates = sorted(
        (directory for directory in build_tools.iterdir() if directory.is_dir()),
        key=version_key,
        reverse=True,
    )
    for directory in candidates:
        executable = directory / name
        if executable.is_file() and os.access(executable, os.X_OK):
            return str(executable)
    raise ValueError(f"Android build tool unavailable: {name}")


def verify() -> dict:
    version_name = os.environ.get("QG_NATIVE_VERSION_NAME", "")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version_name):
        raise ValueError("QG_NATIVE_VERSION_NAME must be the certified x.y.z release value")
    expected_cert = re.sub(r"[^0-9a-fA-F]", "", os.environ.get("QG_ANDROID_EXPECTED_CERT_SHA256", "")).lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected_cert):
        raise ValueError("QG_ANDROID_EXPECTED_CERT_SHA256 must be the certified 64-hex SHA-256 fingerprint")

    expected_codes: dict[str, int] = {}
    observed_play_max: dict[str, int] = {}
    for role in ROLES:
        raw = os.environ.get(f"QG_{role.upper()}_VERSION_CODE", "")
        play_raw = os.environ.get(f"QG_{role.upper()}_PLAY_MAX_VERSION_CODE", "")
        if not re.fullmatch(r"\d+", raw):
            raise ValueError(f"QG_{role.upper()}_VERSION_CODE must be an explicit positive integer")
        if not re.fullmatch(r"\d+", play_raw):
            raise ValueError(f"QG_{role.upper()}_PLAY_MAX_VERSION_CODE must be the observed highest Play versionCode")
        code = int(raw)
        play_max = int(play_raw)
        if code <= 0 or code > 2100000000:
            raise ValueError(f"release versionCode is out of range for {role}")
        if code <= play_max:
            raise ValueError(f"release artifact versionCode must exceed observed Play history for {role}")
        expected_codes[role] = code
        observed_play_max[role] = play_max

    aapt = android_build_tool("aapt")
    apksigner = android_build_tool("apksigner")
    jarsigner = shutil.which("jarsigner")
    keytool = shutil.which("keytool")
    if not jarsigner or not keytool:
        raise ValueError("jarsigner and keytool are required from a JDK")

    source_sha = run(["git", "rev-parse", "HEAD"]).strip()
    if run(["git", "status", "--porcelain", "--untracked-files=normal"]).strip():
        raise ValueError("release artifact verification requires a clean source checkout")
    artifacts: dict[str, dict] = {}
    signer_digests: set[str] = set()

    for role, package in ROLES.items():
        apk = NATIVE / role / "build" / "outputs" / "apk" / "release" / f"{role}-release.apk"
        aab = NATIVE / role / "build" / "outputs" / "bundle" / "release" / f"{role}-release.aab"
        for path in (apk, aab):
            if not path.is_file() or path.stat().st_size <= 0:
                raise ValueError(f"release artifact missing or empty: {path.relative_to(ROOT)}")

        badging = run([aapt, "dump", "badging", str(apk)])
        match = PACKAGE_RE.search(badging)
        if not match:
            raise ValueError(f"unable to read release package metadata for {role}")
        if match.group("package") != package:
            raise ValueError(f"release package mismatch for {role}")
        if match.group("code") != str(expected_codes[role]):
            raise ValueError(f"release versionCode mismatch for {role}")
        if match.group("name") != version_name:
            raise ValueError(f"release versionName mismatch for {role}")
        if "application-debuggable" in badging:
            raise ValueError(f"release APK must not be debuggable: {role}")

        certificate = run([apksigner, "verify", "--verbose", "--print-certs", str(apk)])
        cert_match = CERT_RE.search(certificate)
        if not cert_match:
            raise ValueError(f"APK signer certificate digest missing for {role}")
        cert_sha = cert_match.group("digest").replace(":", "").lower()
        if cert_sha != expected_cert:
            raise ValueError(f"APK signer does not match certified release certificate for {role}")
        signer_digests.add(cert_sha)

        run([jarsigner, "-verify", str(aab)])
        aab_certificate = run([keytool, "-printcert", "-jarfile", str(aab)])
        aab_cert_match = KEYTOOL_SHA256_RE.search(aab_certificate)
        if not aab_cert_match:
            raise ValueError(f"AAB signer certificate digest missing for {role}")
        aab_cert_sha = aab_cert_match.group("digest").replace(":", "").lower()
        if aab_cert_sha != cert_sha:
            raise ValueError(f"APK/AAB signer mismatch for {role}")
        if aab_cert_sha != expected_cert:
            raise ValueError(f"AAB signer does not match certified release certificate for {role}")

        artifacts[role] = {
            "application_id": package,
            "version_code": expected_codes[role],
            "observed_play_max_version_code": observed_play_max[role],
            "version_name": version_name,
            "apk": str(apk.relative_to(ROOT)),
            "apk_sha256": sha256(apk),
            "aab": str(aab.relative_to(ROOT)),
            "aab_sha256": sha256(aab),
            "signer_certificate_sha256": cert_sha,
            "aab_signer_certificate_sha256": aab_cert_sha,
            "apk_non_debuggable": True,
            "apk_signature_verified": True,
            "aab_signature_verified": True,
            "apk_aab_signer_match": True,
        }

    if len(signer_digests) != 1:
        raise ValueError("all three QueueGo Native release apps must use the certified signing identity")

    result = {
        "source_sha": source_sha,
        "version_name": version_name,
        "signer_certificate_sha256": next(iter(signer_digests)),
        "roles": artifacts,
        "result": "PASS",
    }
    output = os.environ.get("QG_NATIVE_RELEASE_ARTIFACT_EVIDENCE")
    if output:
        target = Path(output)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(result, indent=2) + "\n")
    return result


if __name__ == "__main__":
    try:
        print(json.dumps(verify(), indent=2))
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f"Native release artifact verification BLOCKED: {error}", file=sys.stderr)
        sys.exit(1)
