import { createClient } from '@supabase/supabase-js';
import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import path from 'node:path';

const url=process.env.QG_RESTORE_SUPABASE_URL;
const key=process.env.QG_RESTORE_SERVICE_ROLE_KEY;
const root=process.env.QG_STORAGE_BACKUP_DIR||'backup/storage';
const manifestPath=process.env.QG_STORAGE_MANIFEST||path.join(path.dirname(root),'storage-manifest.json');
if(!url||!key)throw new Error('Missing local restore Storage credentials');

const manifest=JSON.parse(await readFile(manifestPath,'utf8'));
const supabase=createClient(url,key,{auth:{persistSession:false,autoRefreshToken:false}});
const {data:existing,error:listError}=await supabase.storage.listBuckets();
if(listError)throw listError;
const known=new Set((existing||[]).map(b=>b.id));

for(const bucket of manifest.buckets||[]){
  if(!known.has(bucket.id)){
    const {error}=await supabase.storage.createBucket(bucket.id,{
      public:Boolean(bucket.public),
      fileSizeLimit:bucket.fileSizeLimit??undefined,
      allowedMimeTypes:bucket.allowedMimeTypes??undefined
    });
    if(error)throw error;
    known.add(bucket.id);
  }
}

for(const object of manifest.objects||[]){
  const file=await readFile(path.join(root,object.bucket,...object.path.split('/')));
  const localHash=createHash('sha256').update(file).digest('hex');
  if(localHash!==object.sha256)throw new Error('Backup checksum mismatch for '+object.bucket+'/'+object.path);
  const {error}=await supabase.storage.from(object.bucket).upload(object.path,file,{
    upsert:true,
    contentType:object.contentType||'application/octet-stream'
  });
  if(error)throw error;
}

for(const object of manifest.objects||[]){
  const {data:blob,error}=await supabase.storage.from(object.bucket).download(object.path);
  if(error)throw error;
  const bytes=Buffer.from(await blob.arrayBuffer());
  const hash=createHash('sha256').update(bytes).digest('hex');
  if(bytes.length!==object.bytes||hash!==object.sha256){
    throw new Error('Restored object verification failed for '+object.bucket+'/'+object.path);
  }
}
console.log(JSON.stringify({verifiedObjects:(manifest.objects||[]).length,verifiedBuckets:(manifest.buckets||[]).length}));
