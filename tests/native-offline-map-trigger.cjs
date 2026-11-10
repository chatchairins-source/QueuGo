'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');

const workflow = fs.readFileSync('.github/workflows/build-native-rider-pilot.yml', 'utf8');
const packageJson = JSON.parse(fs.readFileSync('package.json', 'utf8'));
assert.ok(packageJson.scripts.test.includes('node tests/native-offline-map-trigger.cjs'), 'offline-map workflow gate regression must run in npm test');
assert.match(
  workflow,
  /Build and bundle pinned nationwide offline map[\\s\\S]{0,400}if: \\$\\{\\{[^\\n]*startsWith\\(github\\.head_ref, 'work-native-express-offline-map-'\\)/,
  'the explicit Express offline-map follow-up PR must build the pinned country archive'
);
assert.match(workflow, /Test installed country archive and camera without internet[\\s\\S]{0,250}if: \\$\\{\\{ env\\.QG_OFFLINE_MAP_ASSETS != '' \\}\\}/, 'a bundled country archive must activate the no-network runtime gate');
assert.match(workflow, /Export freshly built role APKs after runtime gates[\\s\\S]{0,250}if: \\$\\{\\{ env\\.QG_OFFLINE_MAP_ASSETS != '' \\}\\}/, 'role APK export must remain gated on the offline runtime path');
console.log('Native offline map CI trigger: targeted branch build, no-network runtime and artifact gates PASS');
