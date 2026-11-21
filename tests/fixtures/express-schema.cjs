const fs=require('node:fs');
module.exports=()=>require('./passenger-ride-schema.cjs')()+`
 drop table qg_delivery_proofs;
 create table qg_delivery_proofs(order_id uuid primary key,rider_id uuid,gps_lat float8,gps_lng float8,photo_path text,extra_photo_path text,otp_verified boolean not null default false,verified_at timestamptz not null default now());
 create table qg_pickup_proofs(order_id uuid primary key,rider_id uuid,gps_lat float8,gps_lng float8,photo_path text not null,created_at timestamptz not null default now());
`+fs.readFileSync('supabase/migrations/20261018031500_express_phase1_shared_dispatch.sql','utf8');
