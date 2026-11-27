import { readFile } from 'node:fs/promises';
import { criticalBackupTables } from './backup-inventory-contract.mjs';

const sourcePath=process.argv[2];
const restoredPath=process.argv[3];
if(!sourcePath||!restoredPath)throw new Error('Usage: compare-inventory <source.json> <restored.json>');
const source=JSON.parse((await readFile(sourcePath,'utf8')).trim());
const restored=JSON.parse((await readFile(restoredPath,'utf8')).trim());
const critical=criticalBackupTables;
const failures=[];
for(const key of critical){
  const a=source[key],b=restored[key];
  if(!Number.isSafeInteger(a)||!Number.isSafeInteger(b)||a<0||b<0)failures.push(key+':invalid count');
  else if(a!==b)failures.push(key+':restored count differs from dump snapshot ('+a+' vs '+b+')');
}
if(failures.length)throw new Error('Restore inventory mismatch: '+failures.join(', '));
console.log(JSON.stringify({verified:critical.length,source,restored}));
