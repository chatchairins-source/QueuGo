"""Fetch only the successful exact-source offline candidate for authenticated QA."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import zipfile

def gh(path):
    return subprocess.check_output(["gh", "api", path], timeout=120)

def sha(path):
    with path.open("rb") as f:
        return hashlib.file_digest(f, "sha256").hexdigest()

head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
repo = os.environ["GITHUB_REPOSITORY"]
root = Path(os.environ["RUNNER_TEMP"]) / "exact-rider-candidate"
root.mkdir(exist_ok=True)
deadline = time.monotonic() + 1800
while True:
    runs = json.loads(gh(f"repos/{repo}/actions/runs?head_sha={head}&per_page=100"))["workflow_runs"]
    matching = [r for r in runs if r["name"] == "Rider in-process Thailand routing" and r["head_sha"] == head]
    successful = [r for r in matching if r["conclusion"] == "success"]
    if successful:
        run = max(successful, key=lambda r: r["id"])
        break
    assert not any(r["conclusion"] in ("failure", "cancelled", "timed_out") for r in matching), "Exact offline gate failed"
    assert time.monotonic() < deadline, "No successful exact-source offline candidate"
    time.sleep(15)
items = json.loads(gh(f"repos/{repo}/actions/runs/{run['id']}/artifacts?per_page=100"))["artifacts"]
for i in range(3):
    name = f"rider-unreleased-country-part0{i}-{head}"
    choices = [a for a in items if a["name"] == name and not a["expired"]]
    assert len(choices) == 1, "Missing unique candidate part: " + name
    a = choices[0]
    archive = root / f"part{i}.zip"
    with archive.open("wb") as f:
        subprocess.run(["gh", "api", f"repos/{repo}/actions/artifacts/{a['id']}/zip"],
                       stdout=f, check=True, timeout=300)
    assert sha(archive) == a["digest"].removeprefix("sha256:"), "Artifact digest mismatch"
    with zipfile.ZipFile(archive) as z:
        for n in z.namelist():
            assert "/" not in n and "\\" not in n and n not in (".", ".."), "Unsafe artifact path"
        z.extractall(root)
assert (root / "build-commit.txt").read_text().strip() == head
for line in (root / "parts.sha256").read_text().splitlines():
    digest, name = line.split()
    assert re.fullmatch(r"rider-debug\.apk\.part0[012]", name)
    assert sha(root / name) == digest, "Part digest mismatch"
target = Path("native-android/rider/build/outputs/apk/debug/rider-debug.apk")
target.parent.mkdir(parents=True, exist_ok=True)
with target.open("wb") as out:
    for i in range(3):
        with (root / f"rider-debug.apk.part0{i}").open("rb") as stream:
            while chunk := stream.read(1024 * 1024):
                out.write(chunk)
assert sha(target) == (root / "whole-apk.sha256").read_text().split()[0], "Whole APK mismatch"
Path("artifacts/rider-authenticated-review").mkdir(parents=True, exist_ok=True)
Path("artifacts/rider-authenticated-review/candidate.json").write_text(json.dumps({
    "source_head": head, "offline_run_id": run["id"], "apk_sha256": sha(target),
    "same_offline_tested_apk": True, "physical_road_test": False
}, indent=2) + "\n")
print("QUEUEGO_EXACT_OFFLINE_CANDIDATE_VERIFIED", head, sha(target))
