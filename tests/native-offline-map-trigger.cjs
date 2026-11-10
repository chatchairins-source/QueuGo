'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');

const workflow = fs.readFileSync('.github/workflows/build-native-rider-pilot.yml', 'utf8');
const packageJson = JSON.parse(fs.readFileSync('package.json', 'utf8'));
assert.ok(packageJson.scripts.test.includes('node tests/native-offline-map-trigger.cjs'), 'offline-map workflow gate regression must run in npm test');

const countryStart = workflow.indexOf('      - name: Build and bundle pinned nationwide offline map');
const countryEnd = workflow.indexOf('      - name: Tracking coordinate and terminal-state unit tests', countryStart);
assert.ok(countryStart >= 0 && countryEnd > countryStart, 'the nationwide map build step must be present');
const countryGate = workflow.slice(countryStart, countryEnd);
assert.ok(countryGate.includes("startsWith(github.head_ref, 'work-native-express-offline-map-')"), 'the explicit follow-up PR must build the pinned country archive');
assert.ok(countryGate.includes('thailand-261109.osm.pbf'), 'the builder must use the available fixed Geofabrik snapshot');
assert.ok(countryGate.includes('thailand-261109.osm.pbf.md5') && countryGate.includes('md5sum --check source-check.md5'), 'the downloaded snapshot must be checked against its official sidecar');
assert.ok(countryGate.includes('330076369') && countryGate.includes('--source-date 2026-11-09'), 'the snapshot size and data date must stay pinned');
assert.ok(countryGate.includes('ln -s "$source_pbf" "$QG_MAP_DATA_DIR/thailand-latest.osm.pbf"'), 'Planetiler must read the checksum-verified snapshot at its configured OSM input path');
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

console.log('Native acceptance selectors: country archive, no-network runtime, gated APK export and Express PR race tests PASS');
