'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');

const workflow = fs.readFileSync('.github/workflows/build-native-rider-pilot.yml', 'utf8');
const packageJson = JSON.parse(fs.readFileSync('package.json', 'utf8'));
assert.ok(packageJson.scripts.test.includes('node tests/native-offline-map-trigger.cjs'), 'offline-map workflow gate regression must run in npm test');
const manifestStart = workflow.indexOf('      - name: Verify and record source-bound APK checksums');
const bundleStart = workflow.indexOf('          name: queuego-native-pilot-apks', manifestStart);
const bundleEnd = workflow.indexOf('      - name: Regression test bounded Android SDK download recovery', bundleStart);
assert.ok(manifestStart >= 0 && bundleStart > manifestStart && bundleEnd > bundleStart, 'the Native Pilot APK manifest must be generated before upload');
const manifestStep = workflow.slice(manifestStart, bundleStart);
assert.ok(manifestStep.includes('python3 tests/native-apk-manifest.py'), 'APK manifest regression must run in the artifact build job');
assert.ok(manifestStep.includes('--source-sha "$(git rev-parse HEAD)"'), 'manifest must bind byte hashes to the exact checked out source commit');
assert.ok(manifestStep.includes('create-apk-manifest.py') && manifestStep.includes('APK-MANIFEST.json'), 'artifact must contain the generated source-bound checksum manifest');
assert.ok(workflow.slice(bundleStart, bundleEnd).includes('artifacts/native-pilot-apks/APK-MANIFEST.json'), 'the uploaded primary APK bundle must include its manifest');


const countryStart = workflow.indexOf('      - name: Build and bundle pinned nationwide offline map');
const countryEnd = workflow.indexOf('      - name: Tracking coordinate and terminal-state unit tests', countryStart);
assert.ok(countryStart >= 0 && countryEnd > countryStart, 'the nationwide map build step must be present');
const countryGate = workflow.slice(countryStart, countryEnd);
assert.ok(countryGate.includes("startsWith(github.head_ref, 'work-native-express-offline-map-')"), 'the explicit follow-up PR must build the pinned country archive');
assert.ok(countryGate.includes('https://www.osmdata.org/download/water-polygons-split-3857.zip') && countryGate.includes('https://ftp.fau.de/mirrors/openstreetmapdata.com-archive/2026-10-28/water-polygons-split-3857.zip'), 'water polygon download must have the reachable daily mirror and a dated archive fallback');
assert.ok(countryGate.includes('thailand-261109.osm.pbf'), 'the builder must use the available fixed Geofabrik snapshot');
assert.ok(countryGate.includes('thailand-261109.osm.pbf.md5') && countryGate.includes('md5sum --check source-check.md5'), 'the downloaded snapshot must be checked against its official sidecar');
assert.ok(countryGate.includes('330076369') && countryGate.includes('--source-date 2026-11-09'), 'the snapshot size and data date must stay pinned');
assert.ok(countryGate.includes('ln -s "$(basename "$source_pbf")" "$QG_MAP_DATA_DIR/thailand-latest.osm.pbf"'), 'container-mounted OSM input must use a relative symlink so its target exists under /data');
assert.ok(!countryGate.includes('thailand-261029.osm.pbf'), 'the removed October snapshot must not return');

const runtimeStart = workflow.indexOf('      - name: Test installed country archive and camera without internet');
const runtimeEnd = workflow.indexOf('      - name: Save nationwide offline evidence', runtimeStart);
assert.ok(runtimeStart >= 0 && runtimeEnd > runtimeStart, 'the installed-country no-network runtime step must be present');
assert.ok(workflow.slice(runtimeStart, runtimeEnd).includes("if: ${{ env.QG_OFFLINE_MAP_ASSETS != '' }}"), 'bundling must activate no-network runtime');

const exportStart = workflow.indexOf('      - name: Export freshly built role APKs after runtime gates');
const exportEnd = workflow.indexOf('      - uses: actions/upload-artifact', exportStart);
assert.ok(exportStart >= 0 && exportEnd > exportStart, 'tested APK export step must be present');
assert.ok(workflow.slice(exportStart, exportEnd).includes("if: ${{ env.QG_OFFLINE_MAP_ASSETS != '' }}"), 'APK export must remain gated behind offline runtime assets');

const raceStart = workflow.indexOf('  express-races:');
const raceEnd = workflow.indexOf('\n    runs-on:', raceStart);
assert.ok(raceStart >= 0 && raceEnd > raceStart, 'Express PostgreSQL race job must be present');
assert.ok(workflow.slice(raceStart, raceEnd).includes("startsWith(github.head_ref, 'work-native-express-')"), 'Express race tests must run for pull-request head branches, not only push refs');

const { spawnSync } = require('node:child_process');
const builderDryRun = spawnSync('bash', ['native-android/qa/build-buriram-shortbread-pmtiles.sh'], {
  encoding: 'utf8',
  env: { ...process.env, QG_PLANETILER_DRY_RUN: '1', QG_PLANETILER_DOWNLOAD: '0', QG_PLANETILER_OCEAN_PATH: __filename, QG_MAP_DATA_DIR: '/tmp/queuego-map-dry-run' },
});
assert.equal(builderDryRun.status, 0, builderDryRun.stderr);
assert.ok(builderDryRun.stdout.includes('--ocean-path=/data/native-offline-map-trigger.cjs'), 'builder must pass the staged ocean polygons file to Planetiler');
assert.ok(!builderDryRun.stdout.includes('--download'), 'offline map CI must not ask Planetiler to redownload checked inputs');

console.log('Native acceptance selectors: country archive, no-network runtime, source-bound APK manifest, gated APK export and Express PR race tests PASS');
