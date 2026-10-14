'use strict';
// Restores the private snapshot's typed rows into a disposable DB only.
// This verifies data fidelity, not a full Supabase/auth/storage disaster recovery.
const fs=require('fs'),crypto=require('crypto'),{PGlite}=require('@electric-sql/pglite');
const qi=s=>'"'+s.replace(/"/g,'""')+'"';
(async()=>{
 const input=process.argv[2],output=process.argv[3];if(!input||!output)throw Error('usage: node backup-verify.cjs private.json report.json');
 const raw=fs.readFileSync(input),snapshot=JSON.parse(raw);if(snapshot.format!=='queuego-application-snapshot-v1')throw Error('unsupported snapshot');
 const db=new PGlite(),report={snapshot_sha256:crypto.createHash('sha256').update(raw).digest('hex'),captured_at:snapshot.captured_at,scope:snapshot.scope,tables:[],rows:0,failures:0,verification:'Typed-row round-trip in disposable PGlite; functions, RLS, authentication and Storage bytes are not restored/certified'};
 // Production serialized timestamptz in UTC; avoid the local runtime's Bangkok default.
 await db.exec("set time zone 'UTC'");
 try{
 const enums=new Set();for(const e of snapshot.enums||[]){if(!['public','storage'].includes(e.schema)||!e.labels?.length)throw Error('invalid enum');await db.exec('create schema if not exists '+qi(e.schema));await db.exec('create type '+qi(e.schema)+'.'+qi(e.name)+' as enum ('+e.labels.map(s=>"'"+s.replace(/'/g,"''")+"'").join(',')+')');enums.add(e.schema+'.'+e.name);}
 for(const t of snapshot.tables){
  if(!['public','storage'].includes(t.nspname)||!t.columns.length)throw Error('unexpected snapshot table');
  await db.exec('create schema if not exists '+qi(t.nspname));
  const name=qi(t.nspname)+'.'+qi(t.relname),cols=t.columns.map(c=>{if(!/^(?:bigint|boolean|date|double precision|integer|jsonb|numeric(?:\(\d+,\d+\))?|smallint|text(?:\[\])?|time without time zone|timestamp with time zone|uuid)$/.test(c.type)&&!enums.has(c.type))throw Error('unverified column type');return qi(c.name)+' '+c.type;});
  await db.exec('create table '+name+' ('+cols.join(',')+')');
  const data=Buffer.from(t.data_b64,'base64').toString('utf8');
  await db.query('insert into '+name+' select * from jsonb_populate_recordset(null::'+name+',$1::jsonb)',[data]);
  const r=(await db.query("select count(*)::int n,coalesce(jsonb_agg(to_jsonb(t) order by to_jsonb(t)::text),'[]'::jsonb)::text data from "+name+' t')).rows[0];
  // Table row order is not contractual; compare full typed values as multisets.
  const differences=(await db.query('with original as (select value from jsonb_array_elements($1::jsonb)),restored as (select to_jsonb(t) value from '+name+' t) select count(*)::int n from ((select value from original except all select value from restored) union all (select value from restored except all select value from original)) d',[data])).rows[0].n;
  if(differences)throw Error('typed values differ for '+name+' ('+differences+' records)');
  report.tables.push({schema:t.nspname,name:t.relname,rows:r.n,exact_round_trip:true});report.rows+=r.n;
 }
 fs.writeFileSync(output,JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({tables:report.tables.length,rows:report.rows,failures:0,snapshot_sha256:report.snapshot_sha256,verification:report.verification}));
 }finally{await db.close();}
})().catch(e=>{console.error(e.message);process.exit(1)});
