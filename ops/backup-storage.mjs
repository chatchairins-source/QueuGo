import { createClient } from '@supabase/supabase-js';
import { mkdir, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import path from 'node:path';

const url=process.env.QG_SUPABASE_URL;
const key=process.env.QG_SUPABASE_SERVICE_ROLE_KEY;
const root=process.env.QG_STORAGE_BACKUP_DIR||'backup/storage';
if(!url||!key)throw new Error('Missing Supabase Storage backup credentials');

const supabase=createClient(url,key,{auth:{persistSession:false,autoRefreshToken:false}});
const {data:buckets,error:bucketError}=await supabase.storage.listBuckets();
if(bucketError)throw bucketError;

const manifest={
  version:1,
  createdAt:new Date().toISOString(),
  buckets:[],
  objects:[]
};

async function walk(bucketId,prefix=''){
  let offset=0;
  while(true){
    const {data,error}=await supabase.storage.from(bucketId).list(prefix,{
      limit:100,
      offset,
      sortBy:{column:'name',order:'asc'}
    });
    if(error)throw error;
    const rows=Array.isArray(data)?data:[];
    for(const item of rows){
      const objectPath=prefix?prefix+'/'+item.name:item.name;
      if(item.id){
        const {data:blob,error:downloadError}=await supabase.storage.from(bucketId).download(objectPath);
        if(downloadError)throw downloadError;
        const bytes=Buffer.from(await blob.arrayBuffer());
        const filePath=path.join(root,bucketId,...objectPath.split('/'));
        await mkdir(path.dirname(filePath),{recursive:true});
        await writeFile(filePath,bytes);
        manifest.objects.push({
          bucket:bucketId,
          path:objectPath,
          bytes:bytes.length,
          sha256:createHash('sha256').update(bytes).digest('hex'),
          contentType:item.metadata?.mimetype||item.metadata?.contentType||null
        });
      }else{
        await walk(bucketId,objectPath);
      }
    }
    if(rows.length<100)break;
    offset+=rows.length;
  }
}

for(const bucket of buckets||[]){
  manifest.buckets.push({
    id:bucket.id,
    name:bucket.name,
    public:Boolean(bucket.public),
    fileSizeLimit:bucket.file_size_limit??null,
    allowedMimeTypes:bucket.allowed_mime_types??null
  });
  await walk(bucket.id);
}
manifest.objects.sort((a,b)=>(a.bucket+'/'+a.path).localeCompare(b.bucket+'/'+b.path));
await mkdir(path.dirname(root),{recursive:true});
await writeFile(path.join(path.dirname(root),'storage-manifest.json'),JSON.stringify(manifest,null,2)+'\n');
console.log(JSON.stringify({buckets:manifest.buckets.length,objects:manifest.objects.length,bytes:manifest.objects.reduce((n,o)=>n+o.bytes,0)}));
