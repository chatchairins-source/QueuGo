#!/usr/bin/env bash
set -euo pipefail

PLANETILER_VERSION="${PLANETILER_VERSION:-0.10.2}"
QG_PLANETILER_MEMORY="${QG_PLANETILER_MEMORY:-4g}"
QG_BURIRAM_BOUNDS="${QG_BURIRAM_BOUNDS:-102.369,14.109,103.592,15.819}"
QG_THAILAND_PBF_URL="${QG_THAILAND_PBF_URL:-https://download.geofabrik.de/asia/thailand-latest.osm.pbf}"
QG_PM_TILES_OUTPUT_NAME="${QG_PM_TILES_OUTPUT_NAME:-buriram-shortbread.pmtiles}"
QG_PM_TILES_MAXZOOM="${QG_PM_TILES_MAXZOOM:-14}"
QG_MAP_DATA_DIR="${QG_MAP_DATA_DIR:-$(pwd)/.queuego-map-data}"
QG_PLANETILER_DRY_RUN="${QG_PLANETILER_DRY_RUN:-0}"

if [[ ! "${QG_PM_TILES_OUTPUT_NAME}" =~ ^[A-Za-z0-9._-]+\.pmtiles$ ]]; then
  echo "Invalid QG_PM_TILES_OUTPUT_NAME: ${QG_PM_TILES_OUTPUT_NAME}" >&2
  exit 2
fi

IFS=',' read -r WEST SOUTH EAST NORTH EXTRA <<< "${QG_BURIRAM_BOUNDS},"
if [[ -n "${EXTRA:-}" || -z "${WEST:-}" || -z "${SOUTH:-}" || -z "${EAST:-}" || -z "${NORTH:-}" ]]; then
  echo "QG_BURIRAM_BOUNDS must be W,S,E,N" >&2
  exit 2
fi

mkdir -p "${QG_MAP_DATA_DIR}"
QG_MAP_DATA_DIR="$(cd "${QG_MAP_DATA_DIR}" && pwd)"
OUTPUT_PATH="${QG_MAP_DATA_DIR}/${QG_PM_TILES_OUTPUT_NAME}"
PBF_PATH_IN_CONTAINER="/data/thailand-latest.osm.pbf"
OUTPUT_PATH_IN_CONTAINER="/data/${QG_PM_TILES_OUTPUT_NAME}"

PLANETILER_IMAGE="ghcr.io/onthegomap/planetiler:${PLANETILER_VERSION}"

CMD=(
  docker run --rm
  -u "$(id -u):$(id -g)"
  -e "JAVA_TOOL_OPTIONS=-Xmx${QG_PLANETILER_MEMORY}"
  -v "${QG_MAP_DATA_DIR}:/data"
  "${PLANETILER_IMAGE}"
  shortbread
  "--osm-url=${QG_THAILAND_PBF_URL}"
  "--osm-path=${PBF_PATH_IN_CONTAINER}"
  --download
  "--bounds=${QG_BURIRAM_BOUNDS}"
  "--output=${OUTPUT_PATH_IN_CONTAINER}"
  --storage=mmap
  "--maxzoom=${QG_PM_TILES_MAXZOOM}"
  --force
)

if [[ "${QG_PLANETILER_DRY_RUN}" == "1" ]]; then
  printf '%q ' "${CMD[@]}"
  printf '\n'
  exit 0
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required to build the Buriram PMTiles archive." >&2
  exit 3
fi

echo "Building QueueGo Buriram Shortbread PMTiles"
echo "Planetiler: ${PLANETILER_VERSION}"
echo "Bounds: ${QG_BURIRAM_BOUNDS}"
echo "OSM source: ${QG_THAILAND_PBF_URL}"
echo "Output: ${OUTPUT_PATH}"

"${CMD[@]}"

if [[ ! -s "${OUTPUT_PATH}" ]]; then
  echo "Planetiler did not create a non-empty PMTiles archive." >&2
  exit 4
fi

MAGIC="$(head -c 7 "${OUTPUT_PATH}")"
if [[ "${MAGIC}" != "PMTiles" ]]; then
  echo "Invalid PMTiles header: ${MAGIC}" >&2
  exit 5
fi

SPEC_VERSION="$(od -An -t u1 -j 7 -N 1 "${OUTPUT_PATH}" | tr -d '[:space:]')"
TILE_TYPE="$(od -An -t u1 -j 99 -N 1 "${OUTPUT_PATH}" | tr -d '[:space:]')"
if [[ "${SPEC_VERSION}" != "3" ]]; then
  echo "Unsupported PMTiles spec version: ${SPEC_VERSION}" >&2
  exit 6
fi
if [[ "${TILE_TYPE}" != "1" ]]; then
  echo "Buriram archive is not MVT vector PMTiles: tile type ${TILE_TYPE}" >&2
  exit 7
fi

SHA_FILE="${OUTPUT_PATH}.sha256"
sha256sum "${OUTPUT_PATH}" > "${SHA_FILE}"

echo "PMTiles archive verified:"
cat "${SHA_FILE}"
