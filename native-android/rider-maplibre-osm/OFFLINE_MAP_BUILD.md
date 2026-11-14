# QueueGo Buriram offline map build

The Rider MapLibre module is isolated from the production Rider APK until its runtime and
performance gates pass.

Build the Buriram Shortbread archive with the pinned Planetiler image:

```bash
native-android/qa/build-buriram-shortbread-pmtiles.sh
```

Defaults:

- Planetiler 0.10.2
- raw Thailand OSM PBF from Geofabrik
- padded Buriram bounds: `102.369,14.109,103.592,15.819`
- Shortbread profile
- PMTiles output: `.queuego-map-data/buriram-shortbread.pmtiles`
- max source zoom 14; MapLibre may overzoom local vector geometry on closer navigation zooms

Useful overrides:

```bash
QG_MAP_DATA_DIR=/srv/queuego/maps \
QG_PLANETILER_MEMORY=8g \
QG_PM_TILES_MAXZOOM=14 \
native-android/qa/build-buriram-shortbread-pmtiles.sh
```

Validate the command without Docker or downloads:

```bash
QG_PLANETILER_DRY_RUN=1 native-android/qa/build-buriram-shortbread-pmtiles.sh
```

The build rejects empty/invalid output, checks the `PMTiles` magic header, and writes a
`.sha256` integrity file beside the archive. Do not bulk-download tiles from
`vector.openstreetmap.org`; offline archives are built from raw OSM data instead.
