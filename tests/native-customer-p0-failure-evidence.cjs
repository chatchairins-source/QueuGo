// Original Gradle failure evidence must survive rejected metadata recovery.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const workflow = fs.readFileSync('.github/workflows/native-customer-p0.yml', 'utf8');
const helper = fs.readFileSync('native-android/qa/gradle_maven_recovery.py', 'utf8');
assert(workflow.includes('--log artifacts/customer-p0/gradle-first-attempt.log --'));
assert(helper.includes('log_path.parent.mkdir(parents=True, exist_ok=True)'));
assert(helper.includes('first = build(command, log_path)'));
assert(helper.includes('log_path.with_suffix(".retry.log")'));
assert.match(workflow, /if: always\(\)/);
assert(workflow.includes('artifacts/customer-p0/'));
console.log('Customer original/retry Gradle log retention contract PASS; executable CLI/HTTP coverage is in test_gradle_maven_recovery.py');
