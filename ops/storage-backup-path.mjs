import path from 'node:path';

function segments(value) {
  if(typeof value!=='string'||!value||value.includes('\\')||value.includes('\0')) {
    throw new Error('Unsafe Storage backup path');
  }
  const parts=value.split('/');
  if(parts.some(part=>!part||part==='.'||part==='..')) {
    throw new Error('Unsafe Storage backup path');
  }
  return parts;
}

export function storageBackupBucket(bucket) {
  if(segments(bucket).length!==1)throw new Error('Unsafe Storage backup path');
  return bucket;
}

// Validate original names before path normalization can erase traversal.
// Percent-encoded characters remain literal filenames; never URL-decode them.
export function storageBackupPath(root,bucket,object) {
  storageBackupBucket(bucket);
  const parts=segments(object);
  const base=path.resolve(root,bucket);
  const file=path.resolve(base,...parts);
  if(!file.startsWith(base+path.sep))throw new Error('Unsafe Storage backup path');
  return file;
}
