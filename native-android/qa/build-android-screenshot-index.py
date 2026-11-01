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
    if data.get("review_complete") is True:
        if set(expected) - set(names) or declared_missing:
            raise AssertionError("Manifest claims complete with missing screens")
        if not re.fullmatch(r"[0-9a-f]{40}", str(data.get("source_head", ""))):
            raise AssertionError("Complete screenshot evidence lacks exact source SHA")
        if not re.fullmatch(r"[0-9a-f]{64}", str(data.get("apk_sha256", ""))):
            raise AssertionError("Complete screenshot evidence lacks APK SHA256")
    return expected, declared_missing


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
        if len(content) < 100 or not content.startswith(bytes.fromhex("89504e470d0a1a0a")):
            raise AssertionError("Screenshot is not a real PNG: " + filename)
        if content[12:16] != b"IHDR" or len(content) < 24:
            raise AssertionError("Screenshot lacks PNG IHDR: " + filename)
        width, height = struct.unpack(">II", content[16:24])
        if width < 100 or height < 100:
            raise AssertionError("Invalid Android screenshot dimensions")
        if hashlib.sha256(content).hexdigest() != item.get("sha256"):
            raise AssertionError("Screenshot hash mismatch: " + filename)
        captured.append({"screen": str(item.get("screen", "")), "file": filename,
                         "width": width, "height": height})
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
    ]
    for item in invalid:
        try:
            validate_manifest(item)
        except AssertionError:
            continue
        raise AssertionError("Invalid visual evidence manifest was accepted")
    print("QUEUEGO_VISUAL_INDEX_SELFTEST_PASS 13")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    elif len(sys.argv) == 1:
        main()
    else:
        raise SystemExit("Usage: build-android-screenshot-index.py [--self-test]")
