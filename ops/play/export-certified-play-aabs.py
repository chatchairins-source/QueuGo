#!/usr/bin/env python3
"""Export only independently verified QueueGo release AABs for manual Play upload.

Input is the retained artifact of the *certified* Native release workflow, never
an APK or a directly built debug bundle. This exporter does not certify a build.
"""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROLES = {
    "customer": "com.queuego.customer",
    "merchant": "com.queuego.merchant",
    "rider": "com.queuego.rider",
}
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
SEMVER = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+\Z")
CERT_PATTERN = re.compile(r"SHA256:\s*([0-9a-fA-F:]{64,95})")


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as source:
        for data in iter(lambda: source.read(1024 * 1024), b""):
            h.update(data)
    return h.hexdigest()


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def signer_of(path):
    """Read verified JAR signature and its signing certificate from the AAB."""
    result_verify = subprocess.run(["jarsigner", "-verify", str(path)], check=True,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    require("jar verified" in result_verify.stdout.lower(), "AAB JAR signature is missing")
    result = subprocess.run(["keytool", "-J-Duser.language=en", "-J-Duser.country=US",
                             "-printcert", "-jarfile", str(path)], check=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    match = CERT_PATTERN.search(result.stdout)
    require(match is not None, f"Unable to identify verified AAB signer: {path.name}")
    return match.group(1).replace(":", "").lower()


def validated_entries(artifact_dir):
    evidence = artifact_dir / "native-release-artifacts.json"
    require(evidence.is_file() and not evidence.is_symlink(), "Trusted Native release artifact report is missing")
    report = json.loads(evidence.read_text(encoding="utf-8"))
    require(report.get("result") == "PASS", "Native release artifact report is not PASS")
    source_sha = report.get("source_sha", "")
    version = report.get("version_name", "")
    cert = str(report.get("signer_certificate_sha256", "")).lower()
    require(re.fullmatch(r"[0-9a-fA-F]{40}", source_sha) is not None, "Exact source SHA is missing")
    require(SEMVER.fullmatch(version) is not None, "A certified numeric versionName is required")
    require(HEX64.fullmatch(cert) is not None, "Valid signing certificate fingerprint is missing")
    roles = report.get("roles")
    require(isinstance(roles, dict) and set(roles) == set(ROLES), "Expected exactly Customer, Merchant, Rider")
    validated = []
    for role, package in ROLES.items():
        item = roles[role]
        require(isinstance(item, dict), f"Malformed artifact evidence for {role}")
        require(item.get("application_id") == package, f"Package mismatch: {role}")
        require(item.get("version_name") == version, f"VersionName mismatch: {role}")
        code, maximum = item.get("version_code"), item.get("observed_play_max_version_code")
        require(type(code) is int and 0 < code <= 2100000000, f"Invalid versionCode: {role}")
        require(type(maximum) is int and 0 <= maximum < code, f"Play versionCode not incremented: {role}")
        require(item.get("aab") == f"native-android/{role}/build/outputs/bundle/release/{role}-release.aab",
                f"Unexpected bundle path in release evidence: {role}")
        require(all(item.get(key) is True for key in (
            "aab_manifest_metadata_verified", "aab_page_alignment_16kb", "aab_signature_verified",
            "apk_non_debuggable", "apk_signature_verified", "apk_aab_signer_match")),
            f"Missing certified package checks: {role}")
        require(item.get("signer_certificate_sha256") == cert and
                item.get("aab_signer_certificate_sha256") == cert, f"Certified signer mismatch: {role}")
        path = artifact_dir / f"{role}-release.aab"
        require(path.is_file() and not path.is_symlink() and path.stat().st_size > 0,
                f"AAB missing/empty/symlink: {role}")
        checksum = item.get("aab_sha256", "")
        require(isinstance(checksum, str) and HEX64.fullmatch(checksum) is not None,
                f"Invalid AAB SHA-256 in release evidence: {role}")
        require(digest(path) == checksum, f"AAB SHA-256 mismatch: {role}")
        with zipfile.ZipFile(path) as archive:
            require(archive.testzip() is None, f"Corrupted bundle ZIP: {role}")
            contents = set(archive.namelist())
            require("base/manifest/AndroidManifest.xml" in contents and "BundleConfig.pb" in contents,
                    f"Not an Android App Bundle: {role}")
        require(signer_of(path) == cert, f"Actual AAB signer fingerprint mismatch: {role}")
        validated.append((role, package, code, path, checksum))
    return source_sha, version, cert, validated


def export(artifact_dir, output_dir):
    artifact_dir = artifact_dir.resolve(strict=True)
    source_sha, version, cert, entries = validated_entries(artifact_dir)
    require(not output_dir.is_symlink(), "Output must not be a symlink")
    output_dir = output_dir.resolve()
    require(output_dir != artifact_dir and artifact_dir not in output_dir.parents,
            "Output directory must be separate from the certified artifact input")
    require(not output_dir.exists() or not any(output_dir.iterdir()), "Output directory must be empty")
    output_dir.mkdir(parents=True, exist_ok=True)
    files = []
    for role, package, code, source, checksum in entries:
        target = output_dir / f"QueueGo-{role}-v{version}-{code}.aab"
        shutil.copyfile(source, target)
        require(digest(target) == checksum, f"Export checksum mismatch: {role}")
        files.append({"role": role, "package": package, "version_code": code,
                      "filename": target.name, "sha256": checksum})
    manifest = {"source_sha": source_sha, "version_name": version,
                "signing_certificate_sha256": cert, "source": "certified-native-release-artifact",
                "purpose": "manual_google_play_upload", "files": files}
    (output_dir / "PLAY-UPLOAD-MANIFEST.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (output_dir / "SHA256SUMS.txt").write_text(
        "".join(f"{entry['sha256']}  {entry['filename']}\n" for entry in files), encoding="utf-8")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact-dir", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()
    try:
        report = export(args.artifact_dir, args.output_dir)
    except (OSError, ValueError, KeyError, json.JSONDecodeError, zipfile.BadZipFile,
            subprocess.CalledProcessError, FileNotFoundError) as exc:
        print(f"PLAY AAB EXPORT BLOCKED: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
