#!/usr/bin/env bash
# Recover DATA only; application classes always compile from the current checkout.
# Same immutable country source already used by rider-offline-routing.yml.
set -euo pipefail
: "${RUNNER_TEMP:?RUNNER_TEMP required}"
: "${GITHUB_ENV:?GITHUB_ENV required}"
: "${GITHUB_STEP_SUMMARY:?GITHUB_STEP_SUMMARY required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY required}"
reference="$RUNNER_TEMP/native-country-reference.zip"
assets="$RUNNER_TEMP/queuego-offline-assets"
trap 'rm -f "$reference"' EXIT
expected=a2a6985556e6ba26ef4509ba3605266ae6aa1d541bc6d772b076eac1f7a0e9b0
source_url=https://download.geofabrik.de/asia/thailand-261029.osm.pbf
timeout 600s gh api "repos/$GITHUB_REPOSITORY/actions/artifacts/12965729022/zip" > "$reference"
python3 native-android/qa/prepare-verified-country-map-assets.py \
  --reference-zip "$reference" --expected-zip-sha256 "$expected" \
  --expected-source-url "$source_url" --assets-root "$assets"
# The validator checks the reference ZIP digest, PMTiles country bounds/layers,
# embedded map metadata digest and source URL. An expired/unavailable artifact,
# checksum mismatch or regional map remains a fatal error.
echo "QG_OFFLINE_MAP_ASSETS=$assets" >> "$GITHUB_ENV"
echo "::warning::Monthly download transport exhausted; using independently verified 2026-10-29 country DATA, never predecessor app code"
{
  echo '### Native country data recovery'
  echo 'Monthly 2026-11-01 source was unreachable after bounded HTTPS/IPv4 attempts.'
  echo "Actual rendering source: $source_url"
  echo "Reference artifact SHA-256: $expected"
  echo 'Current-source build, installed offline rendering and all runtime gates remain mandatory.'
} >> "$GITHUB_STEP_SUMMARY"
