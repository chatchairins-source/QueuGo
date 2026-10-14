#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

TARGET = "realPmTilesAssetRendersVillageRoadsWhenInjected"
roots = [
    Path("native-android/rider-maplibre-osm/build/outputs/androidTest-results/connected"),
    Path("native-android/rider-maplibre-osm/build/outputs/androidTest-results"),
]

xml_files = []
for root in roots:
    if root.exists():
        xml_files.extend(root.rglob("*.xml"))

if not xml_files:
    raise SystemExit("No connected Android test XML results found for real PMTiles runtime")

matches = []
for path in xml_files:
    try:
        tree = ET.parse(path)
    except ET.ParseError:
        continue
    for case in tree.getroot().iter("testcase"):
        name = case.attrib.get("name", "")
        if TARGET not in name:
            continue
        matches.append((path, case))

if not matches:
    raise SystemExit(f"Real PMTiles runtime test was not present in XML results: {TARGET}")

for path, case in matches:
    if case.find("skipped") is not None:
        raise SystemExit(f"Real PMTiles runtime test was skipped: {path}")
    failure = case.find("failure")
    error = case.find("error")
    if failure is not None or error is not None:
        detail = (failure if failure is not None else error).attrib.get("message", "")
        raise SystemExit(f"Real PMTiles runtime test failed: {detail}")

print(f"QUEUEGO_SAWAIJEE_REAL_PMTILES_RUNTIME_EXECUTED {len(matches)}")
