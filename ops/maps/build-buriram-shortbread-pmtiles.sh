#!/usr/bin/env bash
set -euo pipefail

PLANETILER_VERSION="${PLANETILER_VERSION:-0.10.2}"
PLANETILER_BASE_URL="https://github.com/onthegomap/planetiler/releases/download/v${PLANETILER_VERSION}"
OSM_URL="${OSM_URL:-https://download.geofabrik.de/asia/thailand-latest.osm.pbf}"
BURIRAM_BOUNDS="${BURIRAM_BOUNDS:-102.369,14.109,103.592,15.819}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK_DIR="${QUEUEGO_MAP_WORK_DIR:-${ROOT_DIR}/.map-build}"
SOURCE_DIR="${WORK_DIR}/sources"
TOOLS_DIR="${WORK_DIR}/tools/planetiler-v${PLANETILER_VERSION}"
OUTPUT_DIR="${QUEUEGO_MAP_OUTPUT_DIR:-${ROOT_DIR}/artifacts/maps}"
OSM_PBF="${SOURCE_DIR}/thailand-latest.osm.pbf"
PLANETILER_JAR="${TOOLS_DIR}/planetiler.jar"
OUTPUT_PM_TILES="${OUTPUT_DIR}/buriram-shortbread.pmtiles"
OUTPUT_SHA256="${OUTPUT_PM_TILES}.sha256"
OUTPUT_MANIFEST="${OUTPUT_PM_TILES}.manifest.json"

mkdir -p "${SOURCE_DIR}" "${TOOLS_DIR}" "${OUTPUT_DIR}"

if [[ "${1:-}" == "--print-plan" ]]; then
  cat <<PLAN
planetiler_version=${PLANETILER_VERSION}
osm_url=${OSM_URL}
bounds=${BURIRAM_BOUNDS}
output=${OUTPUT_PM_TILES}
schema=shortbread
PLAN
  exit 0
fi

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 2
  }
}
require_command curl
require_command java
require_command sha256sum
require_command md5sum
require_command python3

download_planetiler() {
  if [[ ! -s "${PLANETILER_JAR}" ]]; then
    curl --fail --location --retry 5 --retry-delay 2 \
      "${PLANETILER_BASE_URL}/planetiler.jar" \
      --output "${PLANETILER_JAR}.download"
    mv "${PLANETILER_JAR}.download" "${PLANETILER_JAR}"
  fi

  curl --fail --location --retry 5 --retry-delay 2 \
    "${PLANETILER_BASE_URL}/planetiler.jar.sha256" \
    --output "${TOOLS_DIR}/planetiler.jar.sha256"

  local expected actual
  expected="$(awk '{print $1}' "${TOOLS_DIR}/planetiler.jar.sha256")"
  actual="$(sha256sum "${PLANETILER_JAR}" | awk '{print $1}')"
  [[ "${actual}" == "${expected}" ]] || {
    echo "Planetiler SHA-256 mismatch" >&2
    rm -f "${PLANETILER_JAR}"
    exit 3
  }
}

download_osm() {
  curl --fail --location --retry 5 --retry-delay 2 \
    "${OSM_URL}.md5" \
    --output "${OSM_PBF}.md5.remote"

  local expected
  expected="$(awk '{print $1}' "${OSM_PBF}.md5.remote")"

  if [[ -s "${OSM_PBF}" ]]; then
    local current
    current="$(md5sum "${OSM_PBF}" | awk '{print $1}')"
    if [[ "${current}" == "${expected}" ]]; then
      return
    fi
  fi

  # --continue-at resumes the 300MB+ Thailand extract after transient network loss.
  curl --fail --location --retry 8 --retry-delay 3 --continue-at - \
    "${OSM_URL}" \
    --output "${OSM_PBF}"

  local actual
  actual="$(md5sum "${OSM_PBF}" | awk '{print $1}')"
  [[ "${actual}" == "${expected}" ]] || {
    echo "Thailand OSM PBF MD5 mismatch" >&2
    rm -f "${OSM_PBF}"
    exit 4
  }
}

build_tiles() {
  local heap="${PLANETILER_HEAP:-4g}"
  java "-Xmx${heap}" -jar "${PLANETILER_JAR}" shortbread \
    --osm-path="${OSM_PBF}" \
    --bounds="${BURIRAM_BOUNDS}" \
    --output="${OUTPUT_PM_TILES}" \
    --storage=mmap \
    --force
}

write_manifest() {
  sha256sum "${OUTPUT_PM_TILES}" > "${OUTPUT_SHA256}"
  python3 - "${OUTPUT_PM_TILES}" "${OUTPUT_MANIFEST}" "${PLANETILER_VERSION}" "${BURIRAM_BOUNDS}" "${OSM_URL}" <<'PY'
import hashlib
import json
import pathlib
import sys

pmtiles = pathlib.Path(sys.argv[1])
manifest = pathlib.Path(sys.argv[2])
planetiler_version = sys.argv[3]
bounds = [float(x) for x in sys.argv[4].split(",")]
osm_url = sys.argv[5]

digest = hashlib.sha256()
with pmtiles.open("rb") as handle:
    for chunk in iter(lambda: handle.read(1024 * 1024), b""):
        digest.update(chunk)

payload = {
    "id": "buriram-shortbread",
    "schema": "shortbread",
    "format": "pmtiles",
    "planetiler_version": planetiler_version,
    "bounds_wsen": bounds,
    "source_url": osm_url,
    "file_name": pmtiles.name,
    "size_bytes": pmtiles.stat().st_size,
    "sha256": digest.hexdigest(),
    "attribution": "© OpenStreetMap contributors",
}
manifest.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY
}

download_planetiler
download_osm
build_tiles
write_manifest

echo "QueueGo Buriram PMTiles ready:"
echo "  ${OUTPUT_PM_TILES}"
echo "  ${OUTPUT_MANIFEST}"
