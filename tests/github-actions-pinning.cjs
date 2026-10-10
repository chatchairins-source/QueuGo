const fs=require('fs'),assert=require('assert');
const read=p=>fs.readFileSync(p,'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const expected={
  'actions/checkout':'11d5960a326750d5838078e36cf38b85af677262',
  'actions/setup-java':'b6effb05e454b25005698d916606bdc6ffcbf961',
  'actions/setup-node':'49933ea5288caeca8642d1e84afbd3f7d6820020',
  'gradle/actions/setup-gradle':'ed408507eac070d1f99cc633dbcf757c94c7933a',
  'actions/upload-artifact':'ea165f8d65b6e75b540449e92b4886f43607fa02'
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
  }
  ok(!/@v\d+(?:\.|\b)/.test(source),'floating major-version action tag must stay retired in '+path);
}

console.log(JSON.stringify({checks,failures:0,scope:'Commit-SHA pinning for Native/backup release-critical GitHub Actions'}));
