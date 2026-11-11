'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const workflow = fs.readFileSync('.github/workflows/native-country-offline-runtime.yml', 'utf8');
const pr = workflow.match(/  pull_request:\n([\s\S]*?)(?=\n(?:permissions|concurrency|jobs):)/);
assert.ok(pr, 'installed country runtime must have a pull_request trigger');
assert.match(pr[1], /branches: \['work-native-\*\*'\]/, 'successor PR bases must trigger installed-country acceptance');
assert.match(pr[1], /native-android\/qa\/prepare-thailand-map-assets\.py/, 'asset validator changes must retrigger acceptance');
assert.match(pr[1], /build-native-rider-pilot\.yml/, 'country map builder changes must retrigger acceptance');
assert.match(workflow, /ref: \$\{\{ github\.event\.pull_request\.head\.sha \|\| github\.sha \}\}/, 'checkout must use PR source HEAD rather than synthetic merge');
assert.match(workflow, /QG_NAV_RUNTIME_OFFLINE: '1'/, 'runtime must enforce offline mode');
assert.match(workflow, /inspect_archive\(target \/ 'thailand-shortbread\.pmtiles'\)/, 'reused archive must be checked');
assert.match(workflow, /actual\['sha256'\] == manifest\['sha256'\]/, 'archive SHA-256 must match its manifest');
assert.ok(pr[1].includes('native-android/rider-navigation-map/src/main/**'), 'production map source must retrigger country runtime');
assert.ok(pr[1].includes('native-android/rider-navigation-map/build.gradle.kts'), 'map Gradle changes must retrigger country runtime');
assert.ok(pr[1].includes('tests/native-country-runtime-pr-gate.cjs'), 'gate test edits must retrigger country runtime');

const exportWorkflow = fs.readFileSync('.github/workflows/export-tested-country-apks.yml', 'utf8');
const exportPr = exportWorkflow.match(/  pull_request:\n([\s\S]*?)(?=\n  push:)/);
assert.ok(exportPr, 'country APK export must have a pull_request trigger');
assert.match(exportPr[1], /branches: \['work-native-\*\*'\]/, 'successor PR bases must trigger country APK export');
for (const path of [
  'native-android/qa/prepare-thailand-map-assets.py',
  'native-android/**/src/main/**',
  'native-android/**/src/androidTest/**',
  'native-android/**/src/test/**',
  'native-android/**/build.gradle.kts',
  'native-android/settings.gradle.kts',
  'native-android/gradle.properties',
  'native-android/gradle/wrapper/**',
  'tests/native-country-runtime-pr-gate.cjs',
]) {
  assert.ok(exportPr[1].includes(path), `country APK export must retrigger on ${path}`);
}
const pilotWorkflow = fs.readFileSync('.github/workflows/build-native-rider-pilot.yml', 'utf8');
assert.match(exportWorkflow, /permissions:\n(?:  #[^\n]*\n)*  contents: write\n  actions: read/, 'reusable pilot currently requires caller write permission at startup');
assert.match(pilotWorkflow, /publish-rider-road-test:\n    if: \$\{\{ \(github\.ref == .*?\) && github\.event_name == 'push' \}\}/, 'legacy publisher must remain restricted to explicit road-test branches');
assert.doesNotMatch(exportWorkflow, /gh release (create|upload)/, 'country export workflow must not publish releases directly');
assert.match(exportWorkflow, /bundle_country: true/, 'country APK export must request the country build');
assert.match(exportWorkflow, /uses: \.\/\.github\/workflows\/build-native-rider-pilot\.yml/, 'country APK export must reuse the tested native pilot');

const exportPush = exportWorkflow.match(/  push:\n([\s\S]*?)(?=\npermissions:)/);
assert.ok(exportPush, 'country APK export must run on successor branch pushes');
assert.ok(exportPush[1].includes("branches: ['work-native-**', 'fix/native-country-export-**']"), 'native successor and isolated country-export fix pushes must trigger export');
for (const path of [
  'native-android/qa/prepare-thailand-map-assets.py',
  'native-android/**/src/main/**',
  'native-android/**/src/androidTest/**',
  'native-android/**/src/test/**',
  'native-android/**/build.gradle.kts',
  'native-android/settings.gradle.kts',
  'native-android/gradle.properties',
  'native-android/gradle/wrapper/**',
  'tests/native-country-runtime-pr-gate.cjs',
]) {
  assert.ok(exportPush[1].includes(path), `country export push must retrigger on ${path}`);
}
console.log('Installed-country runtime and APK export successor PR safety assertions PASS');
