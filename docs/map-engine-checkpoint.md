# QueueGo Map engine checkpoint

Branch: work-map-roadgraph-buriram-20261017
Baseline main: a78bca91f723db3f2117030e91c5d9901413e00a
Previous published checkpoint: 309b431bab1b921812fa22e65d3c3fec7f5345e6
Current HEAD: use `git rev-parse HEAD` (a commit cannot embed its own SHA).

Completed: licensed OSM ingestion, reference-complete Buriram envelope, normalized topology, actual three-engine builds/benchmark, core selection, local shared service and real-route smoke checks. Evidence: evidence/map-engine/*.json.

Data: Thailand OSM 2026-10-15T20:21:00Z; source SHA256 02fe975e34a3d1229306441f69c26824b0a31198bae1e972aef4377a1eb7dbaf; extract SHA256 426e2d638ba73005a681b0ddb30ffa2638ce9fc392b329cef89f04e70fbe442d.

Reproduce (use a dedicated Python venv with ops/maps/requirements.txt):

```
python ops/maps/extract_region.py --help
python ops/maps/normalize_osm.py --help
python ops/maps/build_engine_graph.py valhalla /tmp/queuego-buriram/buriram.osm.pbf /tmp/queuego-bench/valhalla-th --admin-pbf /tmp/queuego-thailand.osm.pbf
python ops/maps/benchmark_engines.py --help
python queuego-map-engine/server.py --database /tmp/queuego-buriram/roads.sqlite --config /tmp/queuego-bench/valhalla-th/valhalla.json --vector /tmp/queuego-buriram/buriram-shortbread.pmtiles
```

Tests completed: successful import/integrity/FK checks; all three graph builds; 30-query HTTP benchmark and native capabilities; real core route alternatives, matrix, geocode, progress and HMM smoke. These do not replace release validation.

Next: connect Map View to shared API, audit real restrictions/one-way and road categories, package/checksum/activation/rollback, full npm and actual API/browser tests. All release gates not yet individually evidenced stay OPEN. Service is loopback only; public deployment is not configured. No Android package contains this core yet. No physical GPS/road certification, live traffic or verified postal-address database.

Web API integration checkpoint: full npm test passed; python tests/map-engine-runtime.py --database /tmp/queuego-buriram/roads.sqlite --config /tmp/queuego-bench/valhalla-th/valhalla.json passed 273 checks. Actual browser, all restriction/category coverage, restoration and offline activation/rollback remain pending.

Offline/browser checkpoint: actual PMTiles + Valhalla data archive built, checksummed, installed, activated and rolled back. 6 package checks and real Chromium with external network blocked passed. Local assets include MapLibre/PMTiles notices and Noto OFL fonts. Full turn-rule and road-category coverage, navigation restoration, new CI, public hosting and Android/physical runtime remain OPEN.

Latest local evidence: actual Chromium reload recalculates saved stops and restores route overlays; visibility keeps map identity. Fourteen real OSM category/access queries passed (see real-road-categories.json). Node-snap/directional turn fixtures still leave approaches uncovered; two source relations are malformed upstream. CI first run 38984983271 failed missing generated web assets; source build now prepares checksum-pinned assets before unchanged reference validation. Candidate job will rerun on push. No public server/Android release.
