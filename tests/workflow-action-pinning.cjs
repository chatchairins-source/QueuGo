const fs=require('fs'),assert=require('assert');
let checks=0; const ok=(v,m)=>{assert.ok(v,m);checks++};

const pins={
  'actions/checkout':'11d5960a326750d5838078e36cf38b85af677262',
  'actions/setup-java':'b6effb05e454b25005698d916606bdc6ffcbf961',
  'gradle/actions/setup-gradle':'ed408507eac070d1f99cc633dbcf757c94c7933a',
  'actions/upload-artifact':'ea165f8d65b6e75b540449e92b4886f43607fa02',
  'actions/setup-node':'49933ea5288caeca8642d1e84afbd3f7d6820020',
};

const critical=[
  '.github/workflows/build-native-rider-pilot.yml',
  '.github/workflows/build-native-release.yml',
  '.github/workflows/native-release-certification.yml',
  '.github/workflows/backup-restore-drill.yml',
];

for(const path of critical){
  const source=fs.readFileSync(path,'utf8');
  ok(!/uses:\s+[A-Za-z0-9_.\-/]+@v\d+/i.test(source),path+' must not use floating major-version action tags');
  const refs=[...source.matchAll(/uses:\s+([A-Za-z0-9_.\-/]+)@([0-9a-f]{40})/g)];
  ok(refs.length>0,path+' must contain commit-SHA-pinned actions');
  for(const [,name,sha] of refs){
    if(pins[name]){
      ok(sha===pins[name],path+' action '+name+' must use the certified SHA');
    }
  }
}

console.log(JSON.stringify({checks,failures:0,scope:'Release-critical GitHub Actions pinned to observed commit SHAs'}));
