#!/usr/bin/env python3
"""Stage only SHA-verified, exact-source Rider map/routing assets for Play delivery.

Never uses debug signing for a release. It reads the APK already verified by
fetch-rider-offline-candidate.py and keeps the original source digest bound to
the exact main HEAD. Build with Play install-time asset packs only.
"""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
COUNTRY = ROOT / "native-android/rider_country_map/src/main/assets"
ROUTING = ROOT / "native-android/rider_country_routing/src/main/assets"
MAP_NAME = "rider_country_map"
ROUTE_NAME = "rider_country_routing"
MAX_PACK_BYTES = 1_500_000_000
FILES = {
    "assets/maps/thailand-shortbread.pmtiles": COUNTRY / "maps/thailand-shortbread.pmtiles",
    "assets/maps/thailand-shortbread.json": COUNTRY / "maps/thailand-shortbread.json",
    "assets/maps/thailand-shortbread-recovery.json": COUNTRY / "maps/thailand-shortbread-recovery.json",
    "assets/routing/manifest.json": ROUTING / "routing/manifest.json",
    "assets/routing/tiles.tar": ROUTING / "routing/tiles.tar",
    "assets/routing/valhalla.json": ROUTING / "routing/valhalla.json",
}

def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def check_source() -> None:
    candidate = ROOT / "artifacts/rider-authenticated-review/candidate.json"
    metadata = json.loads(candidate.read_text())
    source = os.getenv("GITHUB_SHA", "").strip()
    required = os.getenv("QG_REQUIRED_OFFLINE_RUN_ID", "").strip()
    if len(source) != 40 or metadata.get("source_head") != source:
        raise SystemExit("Exact source SHA does not match certified Rider offline evidence")
    if not required or metadata.get("offline_run_id") != int(required):
        raise SystemExit("Certified Rider routing run ID differs from actual verified candidate")
    if metadata.get("same_offline_tested_apk") is not True:
        raise SystemExit("Rider candidate did not pass exact-source offline provenance")

def check_file_metadata() -> dict:
    country = json.loads(FILES["assets/maps/thailand-shortbread.json"].read_text())
    pmtiles = FILES["assets/maps/thailand-shortbread.pmtiles"]
    with pmtiles.open("rb") as source:
        if source.read(8) != b"PMTiles\x03":
            raise ValueError("Not a nationwide PMTiles version-3 map")
    if country.get("bytes") != pmtiles.stat().st_size or country.get("sha256") != digest(pmtiles):
        raise ValueError("Nationwide PMTiles checksum does not match captured rendering metadata")
    graph = json.loads(FILES["assets/routing/manifest.json"].read_text())
    config = json.loads(FILES["assets/routing/valhalla.json"].read_text())
    if not isinstance(graph, dict) or not graph or not isinstance(config, dict) or not config:
        raise ValueError("Missing graph/Valhalla metadata")
    tile = FILES["assets/routing/tiles.tar"]
    with tile.open("rb") as data:
        if data.seek(0, 2) < 1024 or data.tell() % 512:
            raise ValueError("Invalid Valhalla routing TAR archive length")
        data.seek(257)
        if data.read(5) != b"ustar":
            raise ValueError("Invalid Valhalla routing TAR header")
    for directory in (COUNTRY, ROUTING):
        total = sum(f.stat().st_size for f in directory.rglob("*") if f.is_file())
        if total < 1024 or total > MAX_PACK_BYTES:
            raise ValueError(f"Play asset pack size out of bounds: {directory.name}: {total}")
    return {"map_sha256": country["sha256"], "routing_tar_sha256": digest(tile),
            "map_bytes": pmtiles.stat().st_size, "routing_bytes": tile.stat().st_size}

def stage(apk: Path) -> None:
    if not apk.is_file() or apk.stat().st_size < 400_000_000:
        raise SystemExit("Unverified or incomplete nationwide Rider test APK")
    check_source()
    candidate = json.loads((ROOT / "artifacts/rider-authenticated-review/candidate.json").read_text())
    if digest(apk) != candidate["apk_sha256"]:
        raise ValueError("Offline-tested Rider APK digest mismatch")
    with zipfile.ZipFile(apk) as container:
        if len(set(container.namelist())) != len(container.namelist()):
            raise ValueError("Duplicate Rider APK entries")
        for member, target in FILES.items():
            info = container.getinfo(member)
            if info.is_dir() or info.file_size <= 0 or info.file_size > MAX_PACK_BYTES:
                raise ValueError("Invalid country asset entry " + member)
            target.parent.mkdir(parents=True, exist_ok=True)
            with container.open(info) as source, target.open("wb") as dest:
                for chunk in iter(lambda: source.read(1024 * 1024), b""):
                    dest.write(chunk)
            if target.stat().st_size != info.file_size:
                raise ValueError("Incomplete extracted country asset " + member)
    checksums = check_file_metadata()
    print(json.dumps({"status": "VERIFIED_SOURCE_ONLY", "apk": str(apk),
                      "certified_offline_run": candidate["offline_run_id"], **checksums}, sort_keys=True))

def verify_bundle(aab: Path) -> None:
    if not aab.is_file():
        raise SystemExit("Signed Rider Play AAB not generated")
    check_source()
    expected = {
        MAP_NAME + "/assets/" + member.removeprefix("assets/") if member.startswith("assets/maps/")
        else ROUTE_NAME + "/assets/" + member.removeprefix("assets/"): target
        for member, target in FILES.items()
    }
    with zipfile.ZipFile(aab) as bundle:
        names = bundle.namelist()
        if len(names) != len(set(names)):
            raise ValueError("AAB has duplicate ZIP entries")
        if "base/assets/maps/thailand-shortbread.pmtiles" in names or "base/assets/routing/tiles.tar" in names:
            raise ValueError("Nationwide archives duplicated into Play base module")
        for member, target in expected.items():
            info = bundle.getinfo(member)
            if info.file_size != target.stat().st_size:
                raise ValueError("Play asset pack file length mismatch: " + member)
            if info.file_size <= 0:
                raise ValueError("Empty Play asset pack file: " + member)
            with bundle.open(info) as stream:
                h = hashlib.sha256()
                for block in iter(lambda: stream.read(1024 * 1024), b""):
                    h.update(block)
                if h.hexdigest() != digest(target):
                    raise ValueError("Pack payload diverges from SHA-pinned Rider test APK")
        for pack in (MAP_NAME, ROUTE_NAME):
            if pack + "/manifest/AndroidManifest.xml" not in names:
                raise ValueError("Missing Play install-time asset pack manifest for " + pack)
        base_bytes = sum(z.file_size for z in bundle.infolist() if z.filename.startswith("base/"))
        if base_bytes > 500_000_000:
            raise ValueError("Base module uncompressed budget excessive, require Play size review")
    print(json.dumps({"status": "SOURCE_AND_PACK_BYTES_VERIFIED",
                      "aab_sha256": digest(aab), "apk_offline_source_attested": True}, sort_keys=True))

if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--verify-bundle":
        verify_bundle(Path(sys.argv[2]))
    elif len(sys.argv) == 2:
        stage(Path(sys.argv[1]))
    else:
        raise SystemExit("usage: stage-play-rider-assets.py VERIFIED_RIDER.apk | --verify-bundle RIDER.aab")
