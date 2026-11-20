'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const yaml = fs.readFileSync(path.resolve(__dirname, '../.github/workflows/rider-android-authenticated-review.yml'), 'utf8');
const check = yaml.split('  credential-check:')[1]?.split('\n  capture:')[0];
assert.ok(check, 'authenticated review credential gate must exist');
assert.ok(check.includes('REVIEW_GITHUB_API_TOKEN: ${{ github.token }}'), 'use scoped GitHub Actions token instead of unauthenticated commit API requests');
assert.ok(check.includes('"Authorization": "Bearer " + token'), 'commit lookup must authenticate to avoid anonymous rate limit');
assert.ok(check.includes('if not token:') && check.includes('raise RuntimeError('), 'missing token must fail closed');
assert.ok(check.includes('urllib.error.HTTPError as error'), 'GitHub API errors must surface without leaking authorization headers');
assert.ok(check.includes('urllib.request.urlopen(request, timeout=20)'), 'commit fetch must remain bounded');
assert.ok(check.includes('"[merchant-visual-review]" in commit_message'), 'merchant-only branch selection must remain intact');
for (const role of ['RIDER', 'MERCHANT', 'CUSTOMER']) {
  assert.ok(check.includes('QG_' + role + '_REVIEW_PHONE'), 'role phone secret gate missing ' + role);
  assert.ok(check.includes('QG_' + role + '_REVIEW_PASSWORD'), 'role password secret gate missing ' + role);
}
assert.ok(yaml.includes('needs.credential-check.outputs.ready == \'true\''), 'capture must remain gated on real review credentials');
assert.ok(!check.includes('print(token)') && !check.includes('print(request.headers)'), 'API bearer token must not be logged');
console.log('Authenticated review GitHub API token guard passed');
