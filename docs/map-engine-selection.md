# QueueGo routing core decision — Buriram, 2026-10-17

Decision: Valhalla 3.9.1, exposed through one QueueGo service and reusable regional graph packages. Actual measurements are in `evidence/map-engine/engine-benchmark.json`; all engines built the SAME reference-complete OSM extract (SHA256 426e2d638ba73005a681b0ddb30ffa2638ce9fc392b329cef89f04e70fbe442d). No public routing endpoint was benchmarked.

| Measured item | Valhalla 3.9.1 | OSRM 6.0.0 | GraphHopper 11.0 |
|---|---:|---:|---:|
| Warm HTTP median / p95, ms | 7.848 / 13.128 | 2.411 / 4.451 | 10.825 / 15.232 |
| Server peak RSS, KiB | 84,112 | 107,108 | 235,988 |
| Graph/config bytes | 75,875,901 | 156,277,979 | 24,165,998 |
| Alternatives / matching | passed / passed | passed / passed | passed / passed |
| Native matrix in tested distribution | passed | passed | HTTP 404 |
| Motorcycle | native costing | separate reviewed Lua graph required | custom model, imported car access needs review |
| Licence | MIT | BSD-2-Clause | Apache-2.0 |

Five warmups, 30 timed sequential requests, two active CPUs; one real city query, not a national or Android benchmark. Valhalla uses the official Actor behind Python HTTP because native ZeroMQ interface enumeration is blocked in this host. Other engines use native HTTP. Memory is child `wait4` peak RSS at shutdown; namespace PID files were NOT used. Different cost models return different routes, so latency is not a quality ranking.

| Selection criterion | Assessment |
|---|---|
| Thailand / OSM | All imported the snapshot. Thailand admin data and left-hand driving were explicitly configured. Coverage is only what OSM contains. |
| Turn restrictions | All support OSM restrictions. Buriram has 62 relations; import success alone does not certify every restriction. Real-road audit is a release gate. |
| Custom costing | Valhalla changes costing at request time; OSRM uses build-time Lua; GraphHopper flexible custom models work with reviewed encoded access. |
| Multi-stop / matrix | All route ordered stops; OSRM trip/table and Valhalla optimized_route/matrix tested. GH matrix would require our own orchestration or another distribution. |
| Matching | All tested against route-derived real OSM geometry. These traces are not recorded motorcycle GPS. |
| Offline / Android | Valhalla C++ and regional tiles fit a shared future native core; OSRM server packaging and GH Java graph are possible. Android size, JNI, battery and road testing remain unmeasured. No claim that a Python server is Android offline navigation. |
| Memory / size | See measured values above; Valhalla total includes the Thailand admin SQLite used for build. Mobile runtime package may omit build-only data after verification. |
| Updates / maintenance | Pin source PBF and engine versions; rebuild immutable regional packages, checksum, activate atomically and retain a rollback version. None has an implemented QueueGo delta pipeline yet. |

Valhalla wins the combined native motorcycle, dynamic costing, matching, matrix and regional packaging requirements. OSRM is faster in this small benchmark; speed alone does not cover the required profiles and offline architecture. GraphHopper is viable but the tested matrix surface is absent and motorcycle access needs additional work.

QueueGo route preferences rank computed candidates. Fewer signals is not a proof of a globally minimum-signal route. Reliable currently penalizes known rough surface; it must not be labelled a certified safe route. No historical QueueGo speed, live closures or signal-phase feed is present. Estimated signal delay is explicitly a planning assumption.

Primary references: https://valhalla.github.io/valhalla/ ; https://github.com/valhalla/valhalla ; https://github.com/Project-OSRM/osrm-backend ; https://github.com/graphhopper/graphhopper . Data licence and quarantined sources: `docs/map-data-sources.md`.
