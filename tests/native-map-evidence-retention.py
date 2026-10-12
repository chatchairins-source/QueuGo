"""Unit fixtures for the real collector; never Android/physical certification."""
import ast
import hashlib
import json
import math
import os
from pathlib import Path
import struct
import subprocess
import tempfile
from types import SimpleNamespace
import zlib

source = Path("native-android/qa/capture-rider-launch.py").read_text()
tree = ast.parse(source)
functions = [node for node in tree.body if isinstance(node, ast.FunctionDef)
             and node.name in ("valid_real_map_evidence", "retain_real_map_frames")]
assert len(functions) == 2

def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)

png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0))
       + chunk(b"IDAT", zlib.compress(b"\x00\xff\xff\xff")) + chunk(b"IEND", b""))
metrics = json.dumps({"longitude": 100.5, "latitude": 13.75, "zoom": 13,
                      "first_frame_gray_fraction": .2, "final_gray_fraction": .2,
                      "capture_after_ms": 61000}).encode()
responses = {"initial.png": png, "initial-first-frame.png": png, "initial-camera.json": metrics}

def adb_result(args, **kwargs):
    name = args[-1].rsplit("/", 1)[-1]
    # Reproduces observed exec-out: missing remote files can still exit zero.
    raw = responses.get(name, ("cat: files/map-runtime/" + name + ": No such file or directory\n").encode())
    return SimpleNamespace(returncode=0, stdout=raw)

environment = SimpleNamespace(environ={"GITHUB_SHA": "unit-fixture-head", "GITHUB_RUN_ID": "unit-fixture-run"})
namespace = {"hashlib": hashlib, "json": json, "math": math, "struct": struct, "zlib": zlib,
             "os": environment, "adb": ["fixture-adb"], "package": "com.queuego.rider",
             "subprocess": SimpleNamespace(run=adb_result, TimeoutExpired=subprocess.TimeoutExpired)}
exec(compile(ast.Module(body=functions, type_ignores=[]), "collector-unit-fixtures", "exec"), namespace)
valid = namespace["valid_real_map_evidence"]
assert valid("initial.png", png)
assert not valid("initial.png", b"cat: missing file")
assert not valid("initial.png", png[:33])
corrupt = bytearray(png); corrupt[30] ^= 1
assert not valid("initial.png", bytes(corrupt))
assert valid("initial-camera.json", metrics)
assert not valid("resumed-camera.json", b"cat: missing file")
assert not valid("resumed-camera.json", b'{}')
assert not valid("resumed-camera.json", metrics.replace(b'61000', b'NaN'))

with tempfile.TemporaryDirectory() as temp:
    directory = Path(temp)
    # An earlier generated file must not survive an unavailable/invalid response.
    (directory / "resumed.png").write_bytes(png)
    result = namespace["retain_real_map_frames"](directory)
    assert len(result["files"]) == 3
    assert len(result["unavailable_files"]) == 6
    assert not (directory / "resumed.png").exists()
    assert not result["physical_device"] and not result["visual_parity_certified"]
    assert all(hashlib.sha256((directory / row["file"]).read_bytes()).hexdigest() == row["sha256"]
               for row in result["files"])
    for state in ("resumed", "recreated"):
        responses[state + ".png"] = png
        responses[state + "-first-frame.png"] = png
        responses[state + "-camera.json"] = metrics
    assert len(namespace["retain_real_map_frames"](directory)["files"]) == 9

print("Actual map collector unit contracts: reject zero-exit ADB errors/corrupt PNG/invalid JSON; preserve failure evidence")
