'use strict';
// Publish browser assets only; keep operations, migrations and native sources private.
const fs = require('node:fs');
const path = require('node:path');
const {execFileSync} = require('node:child_process');
const root = path.resolve(__dirname, '..');
const output = path.join(root, 'dist-render');
const extensions = new Set(['.html','.js','.css','.json','.webmanifest','.svg','.png','.jpg','.jpeg','.webp','.gif','.ico','.woff','.woff2','.ttf','.mp3','.wav','.ogg','.pmtiles','.pbf']);
const directories = ['admin','merchant','rider','laundry','map-view','map-lab','assets','catalog','data'];
fs.rmSync(output, {recursive:true, force:true});
fs.mkdirSync(output, {recursive:true});
let files = 0;
function copy(relative) {
  const source = path.join(root, relative);
  const stat = fs.lstatSync(source);
  if (stat.isSymbolicLink()) throw new Error('Refusing symlink: '+relative);
  if (stat.isDirectory()) {
    for (const name of fs.readdirSync(source)) if (!name.startsWith('.')) copy(path.join(relative,name));
  } else if (extensions.has(path.extname(source).toLowerCase())) {
    const target = path.join(output,relative);
    fs.mkdirSync(path.dirname(target),{recursive:true});
    fs.copyFileSync(source,target);
    files++;
  }
}
for (const name of fs.readdirSync(root)) {
  if (fs.lstatSync(path.join(root,name)).isFile() && !['package.json','package-lock.json'].includes(name)) copy(name);
}
for (const directory of directories) if (fs.existsSync(path.join(root,directory))) copy(directory);
for (const page of ['index.html','merchant/index.html','rider/index.html','admin/index.html','table-order.html']) {
  if (!fs.existsSync(path.join(output,page))) throw new Error('Missing entry point: '+page);
}
const commit = execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim();
fs.writeFileSync(path.join(output,'deployment.json'),JSON.stringify({application:'QueueGo',commit,files})+'\n');
console.log(`QueueGo static package: ${files} assets, commit ${commit}`);
