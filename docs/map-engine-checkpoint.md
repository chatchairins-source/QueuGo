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

Published code checkpoint before next push: 765caa628279511d91f8b4e217cfed05c07001a3, PR379 draft. CI 38985382067 reproduced exact extraction SHA and passed full npm/graph/runtime/category stages, failed browser binary version mismatch. Fixed by installing browser via pinned playwright-core CLI. Local latest: 280 runtime assertions; 14 road/access cases; actual Chromium restoration/retention/offline rendering; 3 installed-entrypoint checks. Docker image remains untested. New API and Thailand expansion docs describe implemented/future boundaries explicitly.

Last published source before safety-policy checkpoint: 2b37a33127a6931af17fe2b99d816f6bde0abf60. Latest local graph validation: 283 assertions + 14 real road/access cases pass. Unknown source turn rules now fail closed on all member ways, including matrix pair auditing. CI includes pinned Chromium plus a real offline/read-only container check; all-turn/field and public-hosting release gates remain OPEN.

## Continuation: latest main integration (2026-10-17)

Fetched main 513398a1f6f09cb8b58aceabe8dcb32616d2ae2c and candidate 2a1b6307cb6dde3c5a3260787560cd1f18153076. Backup: backup/map-engine-before-main-513398a1. Integrated main mobile search/keyboard fixes, preserving local core routing and installed-data search; static discovery fallback remains available when no core is present. Dataset versions/build commands above are unchanged. Actual offline Chromium passed again (4,659 rendered features; route 5,799m; retained instance and restored route). New merge checkpoint requires its own CI. Local WebKit download returned an incomplete ZIP; mobile browser evidence must come from CI, not this failed download.

Prior exact-head 2a1b6307 CI is GREEN: Map Engine38986601941 and38986605164, RC38986605032. Candidate container ran with network=none, read-only data,512MiB limit; image416,739,656 bytes. Canonical artifact https://github.com/chatchairins-source/QueuGo/actions/runs/38986601941/artifacts/12083034902 expires2026-10-31. No public service or Android core is implied. Release gate stays OPEN for source-rule coverage/field validation and staging runtime. Repository has no configured container deployment recipe/target beyond candidate Dockerfile; GitHub environments/variables discovery is unsupported by connected Fetch, so account-level deployment configuration is not verified. Next: exact merged-head CI, then obtain/verify an authorized non-Production container host and field evidence.


## Turn-source safety checkpoint — 2026-10-17

The shared core now distinguishes malformed source relations from structurally complete relations whose member one-way directions make the declared from→via→to movement impossible. Both classes fail closed by quarantining their OSM member ways. Current known sets for the installed Buriram extract are malformed `{2148441, 17320674}`, disconnected-member `{6982243}`, and direction-conflicting `{1538817}`. No relation roles or one-way tags are guessed or rewritten.

The turn audit now reports `source_incomplete`, `source_member_conflict`, and `source_direction_conflict` explicitly instead of treating those cases as generic routing exceptions. Non-promoting diagnostics remain separate from release-gate proof.

### Directional stabilization from639ce6b6 (2026-10-17)
Actual pinned installed package was recovered from CI39008952954 artifact12092710268; archive ZIP SHA256cabf280393597a9c0e60801efdd104fcf07295f921bd897e7c1a0d702ea755f4. Dataset stays426e2d638ba73005a681b0ddb30ffa2638ce9fc392b329cef89f04e70fbe442d /2026-10-15T20:21:00Z. Backup:backup/map-engine-before-directional-stabilize-20261017.

Complete trace recovery fixes a real535m loop collapsing to17m in edge_walk. Source cardinality adds two malformed relations to quarantine; total6 source-open relations and12 quarantined ways. Local real graph287 checks and14 road cases pass; directional evidence covers all62, with56 engine/runtime passes and6 source-open records,0 failed/uncovered. Isolated trace/cardinality checks12 and release-report checks17 pass. Exact successor CI remains required. Physical/staging/Android gates remain OPEN. Next: integrate fetched mainf582a273 without resetting, verify candidate CI and retain external/physical gates.
