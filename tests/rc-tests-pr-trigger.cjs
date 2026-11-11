'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const workflow = fs.readFileSync(path.join(root, '.github/workflows/rc-tests.yml'), 'utf8');
const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
let checks = 0;
const check = (condition, message) => {
  assert.ok(condition, message);
  checks++;
};

check(/^  pull_request:\n/m.test(workflow), 'RC full-suite workflow must run on pull requests');
for (const pattern of [
  "      - '.github/workflows/rc-tests.yml'",
  "      - 'package.json'",
  "      - 'package-lock.json'",
  "      - 'supabase/migrations/**'",
  "      - 'tests/**'"
]) check(workflow.includes(pattern), 'RC PR path filter must include ' + pattern.trim());
check(/- run: npm test/.test(workflow), 'RC pull-request job must run the full npm test suite');
check(pkg.scripts?.test?.includes('node tests/rc-tests-pr-trigger.cjs'),
  'full npm test suite must enforce the RC PR trigger contract');

console.log(JSON.stringify({ checks, failures: 0, scope: 'full RC npm test PR trigger and regression' }));
