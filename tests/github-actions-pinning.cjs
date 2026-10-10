const fs=require('fs'),assert=require('assert');
const read=p=>fs.readFileSync(p,'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const expected={
  'actions/checkout':'3d3c42e5aac5ba805825da76410c181273ba90b1',
  'actions/setup-java':'de7274f081f381c8f8158605e0321c36c376e2e6',
  'actions/setup-node':'949feb2413d6458794dcd2491c4babbbce0c15c1',
  'gradle/actions/setup-gradle':'3f5f9adaf7d9fecd50b5935e54106014257a94e6',
  'actions/upload-artifact':'cf430e030ddbb5b0abf93d22962f4752f3646cd9'
};
const reviewedVersions={
  'actions/checkout':'v7.0.1',
  'actions/setup-java':'v6.0.1',
  'actions/setup-node':'v7.1.0',
  'gradle/actions/setup-gradle':'v6.4.0',
  'actions/upload-artifact':'v7.0.2'
};
const workflows=[
  '.github/workflows/build-native-rider-pilot.yml',
  '.github/workflows/build-native-release.yml',
  '.github/workflows/native-release-certification.yml',
  '.github/workflows/backup-restore-drill.yml',
  '.github/workflows/rc-tests.yml'
];

for(const path of workflows){
  const source=read(path);
  const matches=[...source.matchAll(/uses:\s*([^\s#]+)\s*(?:#.*)?$/gm)];
  ok(matches.length>0,'release-critical workflow must contain pinned actions: '+path);
  for(const match of matches){
    const spec=match[1];
    if(spec.startsWith('./')) continue;
    const parsed=spec.match(/^([^@]+)@([0-9a-f]{40})$/);
    ok(!!parsed,'external action must be pinned to a 40-hex commit SHA in '+path+': '+spec);
    const [,action,sha]=parsed;
    ok(expected[action]===sha,'unexpected/unreviewed action SHA in '+path+': '+spec);
    ok(reviewedVersions[action]&&source.includes(spec+' # '+reviewedVersions[action]),'reviewed stable action version comment missing in '+path+': '+spec);
  }
  ok(!/@v\d+(?:\.|\b)/.test(source),'floating major-version action tag must stay retired in '+path);
}

console.log(JSON.stringify({checks,failures:0,scope:'Node 24 stable release commit-SHA pinning for Native/backup release-critical GitHub Actions'}));
