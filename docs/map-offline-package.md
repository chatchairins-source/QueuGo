# Buriram offline data package

The actual first package built from pinned OSM is 118,953,852 bytes compressed and 383,511,217 bytes expanded. SHA256: 9bc6bf7170ede0a81dc43faf5ebd57b569fc0ec9061f2ba151e5feb786ec8266. It contains Shortbread vector PMTiles, Valhalla routing tiles, normalized SQLite (ways/nodes/turn rules/access/signals/RTree/places) and a per-file version/checksum manifest. Vector source version differs from the road-graph snapshot and is recorded separately. This is not an Android runtime/APK, and is not publicly published yet.

```
python ops/maps/offline_package.py build --database /tmp/queuego-buriram/roads.sqlite --graph /tmp/queuego-bench/valhalla-th --vector /tmp/queuego-buriram/buriram-shortbread.pmtiles --destination /tmp/queuego-packages/buriram-map-v1 --version buriram-map-v1
python ops/maps/offline_package.py install --archive /tmp/queuego-packages/buriram-map-v1.tar.gz --sha256 9bc6bf7170ede0a81dc43faf5ebd57b569fc0ec9061f2ba151e5feb786ec8266 --store /tmp/queuego-installed
python ops/maps/offline_package.py activate --store /tmp/queuego-installed --version buriram-map-v1
python ops/maps/offline_package.py resolve --store /tmp/queuego-installed
python ops/maps/offline_package.py rollback --store /tmp/queuego-installed
```

Download uses HTTPS and a caller-provided trusted SHA256, validates archive entries and disk capacity, verifies every component, then installs an immutable version. Activation verifies the installed package before atomically replacing/fsyncing the pointer; failed installs preserve the active pointer. Rollback requires a verified previous version. Resolve creates a runtime config with absolute installed tile paths. Package activation does not hot-swap a running Actor: restart the service with resolved paths after the gate. Delta updates are reserved, NOT implemented. Unsigned checksum distribution requires a trusted release channel; package signing remains a deployment requirement.

Serve all runtime assets locally before claiming browser offline operation:

```
python ops/maps/fetch_web_assets.py /tmp/queuego-map-assets
python queuego-map-engine/server.py --database /tmp/queuego-installed/buriram-map-v1/roads.sqlite --config /tmp/queuego-installed/runtime-buriram-map-v1.json --vector /tmp/queuego-installed/buriram-map-v1/vector.pmtiles --assets-root /tmp/queuego-map-assets --port 8766
```

MapLibre JS/CSS, PMTiles JS, Noto Thai UI font and label glyphs are fetched once with pinned checksums (`ops/maps/web-assets.json`). Runtime must not fetch those CDNs. Included glyph ranges cover basic Latin, extended Latin, Thai and general punctuation; other Unicode ranges require additional licensed glyph assets before claiming full language coverage. Local browser evidence blocks ALL external requests and renders actual PMTiles, performs routes/matrix/search/nearest against the installed real graph. This proves a local service can operate without the internet; it does not make a remote browser/server link offline or certify Android background navigation.

Licences: OSM ODbL attribution and source provenance remain in data; Valhalla MIT, MapLibre BSD-3-Clause plus notices, PMTiles BSD-3-Clause, Noto fonts SIL OFL. The asset fetch manifest includes licence files. Open-source Google Fonts assets are fonts, not Google Maps data. No Google Maps geometry/content was ingested.

Validation:

```
python tests/map-package-runtime.py --archive /tmp/queuego-packages/buriram-map-v1.tar.gz --sha256 9bc6bf7170ede0a81dc43faf5ebd57b569fc0ec9061f2ba151e5feb786ec8266 --store /tmp/queuego-installed --output evidence/map-engine/offline-package-validation.json
node tests/map-engine-browser.cjs
```

Browser test requires an actual service on localhost:8766 and Chromium (`QUEUEGO_CHROME` may select the executable). The service and browser need the same network namespace in isolated runners. Production hosting/auth/rate limits/TLS, Android runtime packaging, all restriction/road fixtures, physical GPS and sole-navigation release remain OPEN.
