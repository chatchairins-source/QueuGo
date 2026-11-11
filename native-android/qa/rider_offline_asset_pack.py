#!/usr/bin/env python3
"""Prepare and verify source-bound Rider Play Asset Delivery packages."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import sys
from typing import BinaryIO, Callable
from zipfile import ZipFile

MAX_PACK_BYTES = 1_500_000_000
MAX_MANIFEST_BYTES = 128 * 1024
PACKS = {
    "map": {
        "name": "rider_offline_map_pack",
        "asset_manifest": "queuego-offline-map-assets.json",
        "members": {
            "maps/thailand-shortbread.pmtiles": "rendering",
            "maps/thailand-shortbread.json": "rendering_manifest",
            "maps/thailand-shortbread-recovery.json": "recovery_manifest",
        },
    },
    "routing": {
        "name": "rider_offline_routing_pack",
        "asset_manifest": "queuego-offline-routing-assets.json",
        "members": {
            "routing/tiles.tar": "graph",
            "routing/valhalla.json": "config",
            "routing/manifest.json": "routing_manifest",
        },
    },
}


def sha256_stream(stream: BinaryIO) -> tuple[int, str]:
    digest = hashlib.sha256()
    count = 0
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        count += len(chunk)
        if count > MAX_PACK_BYTES:
            raise ValueError("offline asset exceeds per-pack size limit")
        digest.update(chunk)
    return count, digest.hexdigest()


def read_json(reader: Callable[[str], BinaryIO], member: str) -> dict:
    with reader(member) as stream:
        data = stream.read(MAX_MANIFEST_BYTES + 1)
    if len(data) > MAX_MANIFEST_BYTES:
        raise ValueError(f"offline manifest is too large: {member}")
    try:
        value = json.loads(data)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"offline manifest is invalid JSON: {member}") from error
    if not isinstance(value, dict):
        raise ValueError(f"offline manifest must be a JSON object: {member}")
    return value


def source_metadata(reader: Callable[[str], BinaryIO], member: str) -> tuple[int, str]:
    with reader(member) as stream:
        size, digest = sha256_stream(stream)
    return size, digest


def safe_zip_members(archive: ZipFile) -> dict[str, object]:
    result: dict[str, object] = {}
    for info in archive.infolist():
        name = info.filename
        path = PurePosixPath(name)
        if path.is_absolute() or ".." in path.parts or "\\" in name:
            raise ValueError("offline APK contains a non-portable or traversal path")
        if name in result:
            raise ValueError("offline APK contains duplicate paths")
        if stat.S_ISLNK(info.external_attr >> 16):
            raise ValueError("offline APK contains a symlink")
        result[name] = info
    return result


def manifest_expectations(kind: str, reader: Callable[[str], BinaryIO]) -> dict[str, tuple[int, str]]:
    if kind == "map":
        data = read_json(reader, "maps/thailand-shortbread.json")
        recovery = read_json(reader, "maps/thailand-shortbread-recovery.json")
        if data.get("license") != "ODbL-1.0" or data.get("attribution") != "© OpenStreetMap contributors":
            raise ValueError("nationwide map license or attribution is missing")
        if not isinstance(recovery, dict):
            raise ValueError("map recovery manifest is malformed")
        digest, size = data.get("sha256"), data.get("bytes")
        if not isinstance(digest, str) or not re.fullmatch(r"[a-f0-9]{64}", digest):
            raise ValueError("nationwide map manifest has an invalid SHA-256")
        if not isinstance(size, int) or isinstance(size, bool) or not 0 < size <= MAX_PACK_BYTES:
            raise ValueError("nationwide map manifest has an invalid byte count")
        return {
            "maps/thailand-shortbread.pmtiles": (size, digest),
            "maps/thailand-shortbread.json": source_metadata(reader, "maps/thailand-shortbread.json"),
            "maps/thailand-shortbread-recovery.json": source_metadata(reader, "maps/thailand-shortbread-recovery.json"),
        }

    data = read_json(reader, "routing/manifest.json")
    if data.get("format_version") != 1 or data.get("engine") != "valhalla" or \
       data.get("profile") != "motorcycle" or data.get("licence") != "ODbL-1.0":
        raise ValueError("routing manifest does not describe the supported ODbL motorcycle graph")
    graph = data.get("graph")
    config = data.get("config")
    if not isinstance(graph, dict) or not isinstance(config, dict):
        raise ValueError("routing manifest graph/config entries are missing")
    out: dict[str, tuple[int, str]] = {}
    for path, entry in (("routing/tiles.tar", graph), ("routing/valhalla.json", config)):
        size, digest = entry.get("bytes"), entry.get("sha256")
        if not isinstance(size, int) or isinstance(size, bool) or not 0 < size <= MAX_PACK_BYTES:
            raise ValueError(f"routing manifest has invalid byte count: {path}")
        if not isinstance(digest, str) or not re.fullmatch(r"[a-f0-9]{64}", digest):
            raise ValueError(f"routing manifest has invalid SHA-256: {path}")
        out[path] = (size, digest)
    out["routing/manifest.json"] = source_metadata(reader, "routing/manifest.json")
    return out


def prepare(
    *, source_sha: str, map_output: Path, routing_output: Path,
    map_root: Path | None = None, routing_root: Path | None = None,
    apk: Path | None = None, apk_sha256: str | None = None, build_commit_file: Path | None = None,
) -> dict:
    if not re.fullmatch(r"[0-9a-f]{40}", source_sha):
        raise ValueError("source SHA must be a full lowercase Git commit SHA")
    if map_root is not None and (map_root.resolve() == routing_root.resolve()):
        raise ValueError("map and routing asset roots must be distinct")
    if apk is not None:
        if map_root is not None or routing_root is not None or build_commit_file is None:
            raise ValueError("APK mode requires only an APK and its build-commit manifest")
        if not apk.is_file() or not build_commit_file.is_file():
            raise ValueError("source-bound field APK or build commit is unavailable")
        build_sha = build_commit_file.read_text(encoding="utf-8").strip()
        if build_sha != source_sha:
            raise ValueError("field APK source commit does not match release source SHA")
        if not apk_sha256 or not re.fullmatch(r"[a-f0-9]{64}", apk_sha256):
            raise ValueError("a complete expected field APK SHA-256 is required")
        with apk.open("rb") as stream:
            _, actual = sha256_stream(stream)
        if actual != apk_sha256:
            raise ValueError("field APK SHA-256 does not match the exact-source artifact manifest")
        archive = ZipFile(apk)
        members = safe_zip_members(archive)

        def reader(path: str) -> BinaryIO:
            name = "assets/" + path
            if name not in members:
                raise ValueError("verified field APK is missing required offline asset: " + path)
            return archive.open(name)
    else:
        if map_root is None or routing_root is None or build_commit_file is not None or apk_sha256:
            raise ValueError("asset-directory mode requires map and routing roots only")
        if not map_root.is_dir() or not routing_root.is_dir():
            raise ValueError("offline map or routing source directory is missing")

        def reader(path: str) -> BinaryIO:
            root = map_root if path.startswith("maps/") else routing_root
            local = root / path
            if not local.is_file():
                raise ValueError("missing required offline asset: " + path)
            if local.resolve().parent != (root / Path(path).parent).resolve():
                raise ValueError("offline asset source escapes its root: " + path)
            return local.open("rb")

        archive = None

    outputs = {"map": map_output, "routing": routing_output}
    expectations = {kind: manifest_expectations(kind, reader) for kind in PACKS}
    prepared: dict[str, dict] = {}
    try:
        for kind, pack in PACKS.items():
            root = outputs[kind]
            if root.exists() and any(root.iterdir()):
                raise ValueError(f"refusing to overwrite non-empty generated asset pack: {root}")
            root.mkdir(parents=True, exist_ok=True)
            files = []
            for relative, (expected_bytes, expected_hash) in expectations[kind].items():
                source = relative
                target = root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                digest = hashlib.sha256()
                total = 0
                with reader(source) as input_stream, target.open("xb") as output_stream:
                    for chunk in iter(lambda: input_stream.read(1024 * 1024), b""):
                        total += len(chunk)
                        if total > expected_bytes or total > MAX_PACK_BYTES:
                            raise ValueError(f"offline asset exceeds its manifest size: {relative}")
                        output_stream.write(chunk)
                        digest.update(chunk)
                if total != expected_bytes or digest.hexdigest() != expected_hash:
                    raise ValueError(f"offline asset bytes or SHA-256 mismatch: {relative}")
                files.append({"path": relative, "bytes": total, "sha256": expected_hash})
            pack_manifest = {
                "format_version": 1,
                "source_sha": source_sha,
                "delivery": "play_asset_delivery_install_time",
                "pack_name": pack["name"],
                "files": files,
            }
            (root / pack["asset_manifest"]).write_text(
                json.dumps(pack_manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
            )
            prepared[kind] = {"pack_name": pack["name"], "files": files}
    except Exception:
        for path in outputs.values():
            if path.exists():
                shutil.rmtree(path)
        raise
    finally:
        if archive is not None:
            archive.close()
    return {"source_sha": source_sha, "packs": prepared}


def verify_aab(bundle: Path, source_sha: str) -> dict:
    if not re.fullmatch(r"[0-9a-f]{40}", source_sha):
        raise ValueError("expected AAB source SHA must be a full lowercase Git commit SHA")
    if not bundle.is_file():
        raise ValueError("Rider AAB is missing")
    with ZipFile(bundle) as archive:
        members = safe_zip_members(archive)
        evidence = {}
        if any(name.startswith("base/assets/maps/") or name.startswith("base/assets/routing/") for name in members):
            raise ValueError("Rider AAB must deliver nationwide data from asset packs, not the 500 MB-limited base module")
        for kind, pack in PACKS.items():
            prefix = pack["name"] + "/assets/"
            manifest_path = prefix + pack["asset_manifest"]
            if pack["name"] + "/manifest/AndroidManifest.xml" not in members:
                raise ValueError("Rider AAB is missing install-time asset pack manifest: " + pack["name"])
            if manifest_path not in members:
                raise ValueError("Rider AAB is missing source-bound asset pack manifest: " + pack["name"])
            with archive.open(manifest_path) as stream:
                data = json.loads(stream.read(MAX_MANIFEST_BYTES + 1))
            if data.get("format_version") != 1 or data.get("source_sha") != source_sha or \
               data.get("delivery") != "play_asset_delivery_install_time" or data.get("pack_name") != pack["name"]:
                raise ValueError("Rider AAB asset pack manifest is not bound to the exact source and delivery mode")
            files = data.get("files")
            expected_paths = set(pack["members"])
            if not isinstance(files, list) or {item.get("path") for item in files if isinstance(item, dict)} != expected_paths:
                raise ValueError("Rider AAB asset pack file inventory is incomplete")
            total_bytes = 0
            for item in files:
                relative = item["path"]
                size, digest = item.get("bytes"), item.get("sha256")
                if not isinstance(size, int) or isinstance(size, bool) or not 0 < size <= MAX_PACK_BYTES:
                    raise ValueError("Rider AAB asset pack file size is invalid: " + relative)
                if not isinstance(digest, str) or not re.fullmatch(r"[a-f0-9]{64}", digest):
                    raise ValueError("Rider AAB asset pack file SHA-256 is invalid: " + relative)
                path = prefix + relative
                if path not in members:
                    raise ValueError("Rider AAB is missing offline asset: " + path)
                info = members[path]
                if info.file_size != size or size > MAX_PACK_BYTES:
                    raise ValueError("Rider AAB offline asset byte count differs from manifest: " + relative)
                with archive.open(path) as stream:
                    actual_size, actual_hash = sha256_stream(stream)
                if actual_size != size or actual_hash != digest:
                    raise ValueError("Rider AAB offline asset SHA-256 differs from manifest: " + relative)
                total_bytes += size
            if total_bytes > MAX_PACK_BYTES:
                raise ValueError("Rider AAB install-time asset pack exceeds the 1.5 GB pack limit")
            evidence[kind] = {"pack_name": pack["name"], "uncompressed_bytes": total_bytes, "source_sha": source_sha}
    return evidence


def verify_standalone_apk(apk: Path) -> dict:
    """Require direct-install Rider APKs to retain the complete offline package."""
    with ZipFile(apk) as archive:
        members = safe_zip_members(archive)

        def reader(path: str) -> BinaryIO:
            name = "assets/" + path
            if name not in members:
                raise ValueError("standalone Rider APK is missing offline asset: " + path)
            return archive.open(name)

        result = {}
        for kind in PACKS:
            expectations = manifest_expectations(kind, reader)
            total = 0
            for path, (size, digest) in expectations.items():
                with reader(path) as stream:
                    actual_size, actual_digest = sha256_stream(stream)
                if actual_size != size or actual_digest != digest:
                    raise ValueError("standalone Rider APK offline asset failed integrity check: " + path)
                total += size
            result[kind] = {"files": len(expectations), "uncompressed_bytes": total}
        return result


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    prepare_parser = sub.add_parser("prepare")
    prepare_parser.add_argument("--source-sha", required=True)
    prepare_parser.add_argument("--map-pack-output", type=Path, required=True)
    prepare_parser.add_argument("--routing-pack-output", type=Path, required=True)
    prepare_parser.add_argument("--map-assets", type=Path)
    prepare_parser.add_argument("--routing-assets", type=Path)
    prepare_parser.add_argument("--apk", type=Path)
    prepare_parser.add_argument("--apk-sha256")
    prepare_parser.add_argument("--build-commit-file", type=Path)
    verify_parser = sub.add_parser("verify-aab")
    verify_parser.add_argument("--bundle", type=Path, required=True)
    verify_parser.add_argument("--source-sha", required=True)
    apk_parser = sub.add_parser("verify-apk")
    apk_parser.add_argument("--apk", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "verify-aab":
        result = verify_aab(args.bundle, args.source_sha)
    elif args.command == "verify-apk":
        result = verify_standalone_apk(args.apk)
    else:
        if args.apk:
            result = prepare(
                source_sha=args.source_sha, map_output=args.map_pack_output, routing_output=args.routing_pack_output,
                apk=args.apk, apk_sha256=args.apk_sha256, build_commit_file=args.build_commit_file,
            )
        else:
            result = prepare(
                source_sha=args.source_sha, map_output=args.map_pack_output, routing_output=args.routing_pack_output,
                map_root=args.map_assets, routing_root=args.routing_assets,
            )
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
        print(f"Rider offline asset package BLOCKED: {error}", file=sys.stderr)
        raise SystemExit(1)
