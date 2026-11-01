"""Build a truthful local gallery of captured native Android PNG evidence.

Never manufacture screenshots, invent captured routes or claim missing roles passed.
"""
from pathlib import Path
import hashlib
import html
import json
import re
import struct
import sys
import tempfile
import zlib

ROLES = ("customer", "merchant", "rider")


def validate_manifest(data):
    """Never certify an empty, duplicate, or contradictory screenshot inventory."""
    expected = data.get("expected_screens")
    screens = data.get("screens", [])
    declared_missing = data.get("missing_screens", [])
    def names_valid(values):
        return isinstance(values, list) and all(
            isinstance(value, str) and bool(value.strip()) for value in values
        ) and len(values) == len(set(values))
    if not names_valid(expected) or not expected:
        raise AssertionError("Missing, duplicate or invalid expected screen inventory")
    if not isinstance(screens, list) or any(not isinstance(item, dict) for item in screens):
        raise AssertionError("Invalid screenshot manifest entries")
    names = [item.get("screen") for item in screens]
    if not names_valid(names):
        raise AssertionError("Missing or duplicate captured screen identities")
    if not names_valid(declared_missing) or not set(declared_missing).issubset(expected):
        raise AssertionError("Invalid declared missing screen inventory")
    if set(names) & set(declared_missing):
        raise AssertionError("Captured screen is also declared missing")
    # A manifest must not alias two screen identities to the same PNG file.
    files = [item.get("file") for item in screens]
    if not names_valid(files):
        raise AssertionError("Missing or duplicate screenshot filenames")
    if data.get("review_complete") is True:
        if set(expected) - set(names) or declared_missing:
            raise AssertionError("Manifest claims complete with missing screens")
        if not re.fullmatch(r"[0-9a-f]{40}", str(data.get("source_head", ""))):
            raise AssertionError("Complete screenshot evidence lacks exact source SHA")
        if not re.fullmatch(r"[0-9a-f]{64}", str(data.get("apk_sha256", ""))):
            raise AssertionError("Complete screenshot evidence lacks APK SHA256")
    return expected, declared_missing


def png_dimensions(content):
    """Validate PNG chunk framing and CRCs, not just the eight-byte signature.

    A truncated screenshot with a newly computed SHA256 is still invalid
    evidence. Android screencap PNGs have IHDR, IDAT and terminal IEND chunks.
    """
    if not content.startswith(b"\x89PNG\r\n\x1a\n"):
        raise AssertionError("Screenshot is not a PNG")
    pos, width, height, idat_seen = 8, None, None, False
    while pos + 12 <= len(content):
        length = struct.unpack(">I", content[pos:pos + 4])[0]
        end = pos + 12 + length
        if end > len(content):
            raise AssertionError("Truncated PNG chunk")
        kind = content[pos + 4:pos + 8]
        payload = content[pos + 8:pos + 8 + length]
        checksum = struct.unpack(">I", content[pos + 8 + length:end])[0]
        if zlib.crc32(kind + payload) & 0xffffffff != checksum:
            raise AssertionError("PNG chunk checksum mismatch")
        if pos == 8:
            if kind != b"IHDR" or length != 13:
                raise AssertionError("Screenshot lacks valid PNG IHDR")
            width, height = struct.unpack(">II", payload[:8])
        if kind == b"IDAT":
            idat_seen = True
        if kind == b"IEND":
            if length or not idat_seen or end != len(content):
                raise AssertionError("Invalid terminal PNG IEND")
            if width < 100 or height < 100:
                raise AssertionError("Invalid Android screenshot dimensions")
            return width, height
        pos = end
    raise AssertionError("Screenshot PNG has no terminal IEND")


def inspect_role(folder: Path, role: str):
    manifest = folder / "metadata.json"
    if not manifest.is_file():
        return {"role": role, "status": "BLOCKED", "reason": "No authenticated metadata or PNG proof",
                "screens": [], "missing": ["authenticated screenshot evidence"]}
    data = json.loads(manifest.read_text(encoding="utf-8"))
    if data.get("package") != "com.queuego." + role:
        raise AssertionError("Incorrect APK package in " + str(manifest))
    expected, declared_missing = validate_manifest(data)
    captured = []
    for item in data.get("screens", []):
        filename = item.get("file", "")
        if not filename or Path(filename).name != filename or not filename.endswith(".png"):
            raise AssertionError("Unsafe or invalid screenshot filename")
        path = folder / filename
        if not path.is_file():
            raise AssertionError("Manifest lists missing PNG: " + filename)
        content = path.read_bytes()
        width, height = png_dimensions(content)
        if hashlib.sha256(content).hexdigest() != item.get("sha256"):
            raise AssertionError("Screenshot hash mismatch: " + filename)
        captured.append({"screen": str(item.get("screen", "")), "file": filename,
                         "width": width, "height": height})
    # Distinct screen names with identical pixels are not independent visual
    # evidence; catch stale/unchanged navigation captures before owner review.
    hashes = [item["sha256"] for item in data.get("screens", [])]
    if len(hashes) != len(set(hashes)):
        raise AssertionError("Identical screenshot reused for different screens")
    seen = {item["screen"] for item in captured}
    missing = sorted((set(expected) - seen) | set(declared_missing))
    complete = bool(captured) and not missing and data.get("review_complete") is True
    return {"role": role, "status": "CAPTURED_SCOPE" if complete else "INCOMPLETE",
            "screens": captured, "missing": missing,
            "source_head": data.get("source_head"), "apk_sha256": data.get("apk_sha256"),
            "scope": data.get("scope", ""), "visual_parity_certified": False,
            "physical_device": False}


def render(result):
    escape = lambda x: html.escape(str(x), quote=True)
    cards = "".join(
        '<figure><a href="' + escape(item["file"]) + '"><img loading="lazy" src="' +
        escape(item["file"]) + '" alt="' + escape(item["screen"]) +
        '"></a><figcaption>' + escape(item["screen"]) + ' — ' +
        str(item["width"]) + "×" + str(item["height"]) + "</figcaption></figure>"
        for item in result["screens"]
    )
    missing = ", ".join(result["missing"]) if result["missing"] else "none in declared capture scope"
    return ('<!doctype html><html lang="th"><meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width,initial-scale=1">'
            '<title>QueueGo actual Android screenshot evidence</title>'
            '<style>body{font:15px system-ui,sans-serif;margin:2rem;max-width:1200px}'
            'section{display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:16px}'
            'figure{margin:0}img{width:100%;border:1px solid #aaa;border-radius:8px}'
            'figcaption{padding:8px 0}small{color:#555}</style>'
            '<h1>QueueGo ' + escape(result["role"]) + ' — actual Android APK screenshots</h1>'
            '<p>Status: <strong>' + escape(result["status"]) + '</strong></p>'
            '<p>Missing: ' + escape(missing) + '</p>'
            '<p>Source: <code>' + escape(result.get("source_head", "unknown")) + '</code></p>'
            '<p>APK SHA256: <code>' + escape(result.get("apk_sha256", "unknown")) + '</code></p>'
            '<p><small>Not a complete UI inventory, visual parity certification, GPS road test or release approval.</small></p>'
            '<section>' + cards + '</section></html>\n')


def main():
    root = Path("artifacts")
    for role in ROLES:
        folder = root / (role + "-authenticated-review")
        folder.mkdir(parents=True, exist_ok=True)
        result = inspect_role(folder, role)
        (folder / "index.html").write_text(render(result), encoding="utf-8")
        (folder / "index.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("QUEUEGO_VISUAL_INDEX " + role + " " + result["status"] +
              " screenshots=" + str(len(result["screens"])))


def self_test():
    blocked = inspect_role(Path("/path-that-does-not-exist/queuego"), "customer")
    assert blocked["status"] == "BLOCKED" and not blocked["screens"]
    page = render({"role": "customer", "status": "INCOMPLETE", "screens": [],
                   "missing": ["<unsafe>"]})
    assert "&lt;unsafe&gt;" in page and "<unsafe>" not in page
    assert "Not a complete UI inventory" in page
    assert all(role in ROLES for role in ("customer", "merchant", "rider"))
    valid = {"screens": [{"screen": "home", "file": "home.png"}],
             "expected_screens": ["home"], "missing_screens": [],
             "review_complete": True, "source_head": "a" * 40,
             "apk_sha256": "b" * 64}
    assert validate_manifest(valid) == (["home"], [])
    invalid = [
        {**valid, "expected_screens": []},
        {**valid, "expected_screens": ["home", "home"]},
        {**valid, "screens": [{**valid["screens"][0]}, {**valid["screens"][0]}]},
        {**valid, "expected_screens": ["home", "ride"]},
        {**valid, "missing_screens": ["ride"]},
        {**valid, "source_head": ""},
        {**valid, "apk_sha256": "invalid"},
        {**valid, "screens": [{"file": "home.png"}]},
        {**valid, "missing_screens": ["home"], "review_complete": False},
        {**valid, "screens": [{"screen": "home", "file": "home.png"},
                              {"screen": "profile", "file": "home.png"}],
         "expected_screens": ["home", "profile"]},
    ]
    for item in invalid:
        try:
            validate_manifest(item)
        except AssertionError:
            continue
        raise AssertionError("Invalid visual evidence manifest was accepted")
    # Construct a minimal valid 100x100 PNG for parser-only negative tests.
    def chunk(kind, payload):
        return (struct.pack(">I", len(payload)) + kind + payload +
                struct.pack(">I", zlib.crc32(kind + payload) & 0xffffffff))
    png = (b"\x89PNG\r\n\x1a\n" +
           chunk(b"IHDR", struct.pack(">IIBBBBB", 100, 100, 8, 2, 0, 0, 0)) +
           chunk(b"IDAT", zlib.compress((b"\x00" + b"\x00" * 300) * 100)) +
           chunk(b"IEND", b""))
    # This fixture must use the real eight PNG signature bytes, never a textual
    # escaped sequence (which previously made the chunk parser start at offset 8).
    assert png[:8] == bytes.fromhex("89504e470d0a1a0a")
    assert png_dimensions(png) == (100, 100)
    # Exercise inspect_role end-to-end with two distinct files containing
    # identical pixels: this must not count as two different Android screens.
    with tempfile.TemporaryDirectory() as directory:
        folder = Path(directory)
        (folder / "home.png").write_bytes(png)
        (folder / "profile.png").write_bytes(png)
        data = {"package": "com.queuego.rider",
                "expected_screens": ["home", "profile"], "missing_screens": [],
                "review_complete": True, "source_head": "a" * 40,
                "apk_sha256": "b" * 64,
                "screens": [
                    {"screen": name, "file": name + ".png",
                     "sha256": hashlib.sha256(png).hexdigest()}
                    for name in ("home", "profile")]}
        (folder / "metadata.json").write_text(json.dumps(data), encoding="utf-8")
        try:
            inspect_role(folder, "rider")
        except AssertionError as error:
            assert "Identical screenshot" in str(error)
        else:
            raise AssertionError("Reused pixels accepted as independent screens")
        # A changed, valid second image must pass with its own hash.
        different = (b"\x89PNG\r\n\x1a\n" +
                     chunk(b"IHDR", struct.pack(">IIBBBBB", 100, 100, 8, 2, 0, 0, 0)) +
                     chunk(b"IDAT", zlib.compress((b"\x00" + b"\xff" * 300) * 100)) +
                     chunk(b"IEND", b""))
        (folder / "profile.png").write_bytes(different)
        data["screens"][1]["sha256"] = hashlib.sha256(different).hexdigest()
        (folder / "metadata.json").write_text(json.dumps(data), encoding="utf-8")
        result = inspect_role(folder, "rider")
        assert result["status"] == "CAPTURED_SCOPE" and len(result["screens"]) == 2
    for bad in (png[:-3], png[:-12], png[:29] + bytes([png[29] ^ 1]) + png[30:],
                png + b"trailing"):
        try:
            png_dimensions(bad)
        except AssertionError:
            continue
        raise AssertionError("Corrupt/truncated PNG was accepted")
    print("QUEUEGO_VISUAL_INDEX_SELFTEST_PASS 21")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    elif len(sys.argv) == 1:
        main()
    else:
        raise SystemExit("Usage: build-android-screenshot-index.py [--self-test]")
