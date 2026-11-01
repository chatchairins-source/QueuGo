"""Build a truthful local gallery of captured native Android PNG evidence.

Never manufacture screenshots, invent captured routes or claim missing roles passed.
"""
from pathlib import Path
import hashlib
import html
import json
import struct
import sys

ROLES = ("customer", "merchant", "rider")


def inspect_role(folder: Path, role: str):
    manifest = folder / "metadata.json"
    if not manifest.is_file():
        return {"role": role, "status": "BLOCKED", "reason": "No authenticated metadata or PNG proof",
                "screens": [], "missing": ["authenticated screenshot evidence"]}
    data = json.loads(manifest.read_text(encoding="utf-8"))
    if data.get("package") != "com.queuego." + role:
        raise AssertionError("Incorrect APK package in " + str(manifest))
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
    expected = data.get("expected_screens") or []
    seen = {item["screen"] for item in captured}
    missing = sorted(set(expected) - seen)
    if data.get("missing_screens"):
        missing = sorted(set(missing) | set(data["missing_screens"]))
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
    print("QUEUEGO_VISUAL_INDEX_SELFTEST_PASS 4")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    elif len(sys.argv) == 1:
        main()
    else:
        raise SystemExit("Usage: build-android-screenshot-index.py [--self-test]")
