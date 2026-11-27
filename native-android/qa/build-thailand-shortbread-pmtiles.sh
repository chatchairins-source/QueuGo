#!/usr/bin/env bash
set -euo pipefail
# Reuse the pinned builder; never inherit the regional default bounds/output.
export QG_BURIRAM_BOUNDS='97.1,5.4,105.8,20.6'
export QG_PM_TILES_OUTPUT_NAME='thailand-shortbread.pmtiles'
exec bash "$(dirname "$0")/build-buriram-shortbread-pmtiles.sh"
