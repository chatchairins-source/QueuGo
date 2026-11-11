#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from zipfile import ZIP_STORED, ZipFile

SCRIPT = Path("native-android/qa/rider_offline_asset_pack.py")
spec = importlib.util.spec_from_file_location("rider_offline_asset_pack", SCRIPT)
asset_pack = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(asset_pack)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


class RiderOfflineAssetPackTests(unittest.TestCase):
    source_sha = "a" * 40

    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.map_root = self.root / "map-source"
        self.routing_root = self.root / "routing-source"
        self.map_bytes = b"pmtiles-header-and-test-data"
        self.graph_bytes = b"verified-valhalla-test-graph"
        self.config_bytes = b'{"mjolnir":{"tile_dir":""}}\n'
        (self.map_root / "maps/thailand-shortbread.pmtiles").parent.mkdir(parents=True)
        (self.map_root / "maps/thailand-shortbread.pmtiles").write_bytes(self.map_bytes)
        write_json(self.map_root / "maps/thailand-shortbread.json", {
            "sha256": digest(self.map_bytes), "bytes": len(self.map_bytes),
            "license": "ODbL-1.0", "attribution": "© OpenStreetMap contributors",
        })
        write_json(self.map_root / "maps/thailand-shortbread-recovery.json", {"version": 1})
        (self.routing_root / "routing").mkdir(parents=True)
        (self.routing_root / "routing/tiles.tar").write_bytes(self.graph_bytes)
        (self.routing_root / "routing/valhalla.json").write_bytes(self.config_bytes)
        write_json(self.routing_root / "routing/manifest.json", {
            "format_version": 1, "engine": "valhalla", "profile": "motorcycle", "licence": "ODbL-1.0",
            "graph": {"bytes": len(self.graph_bytes), "sha256": digest(self.graph_bytes)},
            "config": {"bytes": len(self.config_bytes), "sha256": digest(self.config_bytes)},
        })
        self.map_output = self.root / "map-pack/src/main/assets"
        self.routing_output = self.root / "routing-pack/src/main/assets"

    def tearDown(self) -> None:
        self.temp.cleanup()

    def prepare_dirs(self) -> dict:
        return asset_pack.prepare(
            source_sha=self.source_sha,
            map_output=self.map_output,
            routing_output=self.routing_output,
            map_root=self.map_root,
            routing_root=self.routing_root,
        )

    def make_apk(self, path: Path) -> str:
        with ZipFile(path, "w", compression=ZIP_STORED) as archive:
            for root in (self.map_root, self.routing_root):
                for file in root.rglob("*"):
                    if file.is_file():
                        archive.write(file, "assets/" + file.relative_to(root).as_posix())
        return digest(path.read_bytes())

    def test_source_packages_are_bound_and_hash_verified(self) -> None:
        result = self.prepare_dirs()
        self.assertEqual(result["source_sha"], self.source_sha)
        self.assertEqual(set(result["packs"]), {"map", "routing"})
        self.assertEqual((self.map_output / "maps/thailand-shortbread.pmtiles").read_bytes(), self.map_bytes)
        self.assertEqual((self.routing_output / "routing/tiles.tar").read_bytes(), self.graph_bytes)
        self.assertEqual(json.loads((self.map_output / "queuego-offline-assets.json").read_text())["source_sha"], self.source_sha)

    def test_apk_source_commit_and_whole_file_digest_are_required(self) -> None:
        apk = self.root / "candidate.apk"
        candidate_sha = self.make_apk(apk)
        commit_file = self.root / "build-commit.txt"
        commit_file.write_text(self.source_sha + "\n")
        result = asset_pack.prepare(
            source_sha=self.source_sha, map_output=self.map_output, routing_output=self.routing_output,
            apk=apk, apk_sha256=candidate_sha, build_commit_file=commit_file,
        )
        self.assertEqual(result["source_sha"], self.source_sha)
        self.assertEqual((self.map_output / "maps/thailand-shortbread.pmtiles").read_bytes(), self.map_bytes)

        with self.assertRaisesRegex(ValueError, "APK SHA-256"):
            asset_pack.prepare(
                source_sha=self.source_sha, map_output=self.root / "bad-map", routing_output=self.root / "bad-route",
                apk=apk, apk_sha256="0" * 64, build_commit_file=commit_file,
            )
        commit_file.write_text("b" * 40 + "\n")
        with self.assertRaisesRegex(ValueError, "source commit"):
            asset_pack.prepare(
                source_sha=self.source_sha, map_output=self.root / "bad-map", routing_output=self.root / "bad-route",
                apk=apk, apk_sha256=candidate_sha, build_commit_file=commit_file,
            )

    def test_manifest_mismatch_and_missing_assets_fail_closed(self) -> None:
        manifest = self.map_root / "maps/thailand-shortbread.json"
        data = json.loads(manifest.read_text())
        data["sha256"] = "0" * 64
        write_json(manifest, data)
        with self.assertRaisesRegex(ValueError, "bytes or SHA-256 mismatch"):
            self.prepare_dirs()

        manifest.unlink()
        with self.assertRaisesRegex(ValueError, "missing required offline asset"):
            self.prepare_dirs()

    def test_aab_must_contain_both_exact_source_install_time_packs(self) -> None:
        self.prepare_dirs()
        aab = self.root / "rider.aab"
        with ZipFile(aab, "w", compression=ZIP_STORED) as archive:
            for kind, pack in asset_pack.PACKS.items():
                prefix = pack["name"] + "/"
                archive.writestr(prefix + "manifest/AndroidManifest.xml", b"binary-xml-fixture")
                root = self.map_output if kind == "map" else self.routing_output
                for file in root.rglob("*"):
                    if file.is_file():
                        archive.write(file, prefix + "assets/" + file.relative_to(root).as_posix())
        evidence = asset_pack.verify_aab(aab, self.source_sha)
        self.assertEqual(evidence["map"]["source_sha"], self.source_sha)
        with self.assertRaisesRegex(ValueError, "exact source"):
            asset_pack.verify_aab(aab, "b" * 40)

        bad_aab = self.root / "missing-pack.aab"
        with ZipFile(bad_aab, "w", compression=ZIP_STORED) as archive:
            prefix = asset_pack.PACKS["map"]["name"] + "/"
            for item in ZipFile(aab).infolist():
                if item.filename.startswith(prefix):
                    archive.writestr(item.filename, ZipFile(aab).read(item.filename))
        with self.assertRaisesRegex(ValueError, "rider_offline_routing_pack"):
            asset_pack.verify_aab(bad_aab, self.source_sha)


if __name__ == "__main__":
    unittest.main()
