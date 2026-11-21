// Contract: failed Customer P0 Gradle builds retain the original failure evidence.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const workflow = fs.readFileSync('.github/workflows/native-customer-p0.yml', 'utf8');
assert.match(workflow, /mkdir -p artifacts\/customer-p0/);
assert.match(workflow, /trap '[^\n]*cp "\$first_log" artifacts\/customer-p0\/gradle-first-attempt\.log/);
assert.match(workflow, /if: always\(\)/);
assert.match(workflow, /artifacts\/customer-p0\//);
console.log('Customer P0 original Gradle failure evidence retention contract PASS');
