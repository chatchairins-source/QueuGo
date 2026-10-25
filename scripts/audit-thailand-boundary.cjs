#!/usr/bin/env node
'use strict';

// Offline review only. Never updates a service rule or imports a boundary.
const fs = require('node:fs');
const crypto = require('node:crypto');
const PIN = Object.freeze({
  boundary_id: 'THA-ADM0-76911100',
  source: 'geoBoundaries / OpenStreetMap / Wambacher',
  license: 'ODbL-1.0',
  attribution: '© OpenStreetMap contributors; geoBoundaries (https://www.geoboundaries.org)',
  url: 'https://media.githubusercontent.com/media/wmgeolab/geoBoundaries/9469f09/releaseData/gbOpen/THA/ADM0/geoBoundaries-THA-ADM0.geojson',
  sha256: '226eeef03694dc708201f142c992a819798841c0302c89e78cac8579b5058cde',
  bytes: 3166313,
  represented_year: '2017'
});

// Approximate locality samples, not verified delivery addresses or imported POIs.
const SAMPLES = [
  ['Bangkok',13.7563,100.5018], ['Pattaya',12.9236,100.8825],
  ['Rayong',12.6814,101.2816], ['Chanthaburi',12.6112,102.1038],
  ['Trat',12.2428,102.5175], ['Nakhon Si Thammarat',8.4304,99.9631],
  ['Krabi',8.0863,98.9063], ['Phang Nga',8.4501,98.5255],
  ['Phuket Town',7.8804,98.3923], ['Surat Thani',9.1382,99.3215],
  ['Ranong',9.9529,98.6085], ['Chumphon',10.4930,99.1800],
  ['Hat Yai',7.0084,100.4747], ['Satun',6.6238,100.0674],
  ['Trang',7.5594,99.6114], ['Ko Samui',9.5120,100.0136],
  ['Ko Pha Ngan',9.7319,100.0136], ['Ko Tao',10.0956,99.8400],
  ['Ko Chang',12.0480,102.3200], ['Ko Phi Phi Don',7.7407,98.7784],
  ['Ko Lipe',6.4877,99.3010], ['Ko Lanta',7.6244,99.0792]
];

function validateGeometry(geometry) {
  if (!geometry || !['Polygon','MultiPolygon'].includes(geometry.type)) throw Error('polygon geometry required');
  const polygons = geometry.type === 'Polygon' ? [geometry.coordinates] : geometry.coordinates;
  if (!Array.isArray(polygons) || !polygons.length) throw Error('empty boundary');
  for (const polygon of polygons) {
    if (!Array.isArray(polygon) || !polygon.length) throw Error('empty polygon');
    for (const ring of polygon) {
      if (!Array.isArray(ring) || ring.length < 4) throw Error('invalid ring');
      for (const point of ring) {
        if (!Array.isArray(point) || point.length !== 2 || !point.every(Number.isFinite) ||
            point[0] < 97 || point[0] > 106 || point[1] < 5 || point[1] > 21) throw Error('invalid Thailand coordinate');
      }
      if (ring[0][0] !== ring.at(-1)[0] || ring[0][1] !== ring.at(-1)[1]) throw Error('unclosed ring');
    }
  }
  return polygons;
}

// Planar point containment for offline comparison, including holes/edges.
// Production acceptance still requires PostGIS validity and both route endpoints.
function ringLocation(ring, x, y) {
  let inside = false;
  for (let i=0,j=ring.length-1;i<ring.length;j=i++) {
    const [xi,yi]=ring[i], [xj,yj]=ring[j];
    const cross=(x-xi)*(yj-yi)-(y-yi)*(xj-xi);
    if (Math.abs(cross)<1e-12 && x>=Math.min(xi,xj) && x<=Math.max(xi,xj) && y>=Math.min(yi,yj) && y<=Math.max(yi,yj)) return 0;
    if ((yi>y)!==(yj>y) && x<(xj-xi)*(y-yi)/(yj-yi)+xi) inside=!inside;
  }
  return inside ? 1 : -1;
}

function covers(polygons, latitude, longitude) {
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return false;
  return polygons.some(polygon => {
    const outer=ringLocation(polygon[0],longitude,latitude);
    if (outer===-1) return false;
    for (const hole of polygon.slice(1)) {
      const position=ringLocation(hole,longitude,latitude);
      if (position===0) return true;
      if (position===1) return false;
    }
    return true;
  });
}

function parsePinnedBoundary(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length!==PIN.bytes || crypto.createHash('sha256').update(buffer).digest('hex')!==PIN.sha256) throw Error('pinned boundary bytes/checksum mismatch');
  const collection=JSON.parse(buffer.toString('utf8'));
  if (collection.type!=='FeatureCollection' || collection.features?.length!==1) throw Error('one Thailand feature required');
  const feature=collection.features[0];
  if (feature.properties?.shapeGroup!=='THA' || feature.properties?.shapeType!=='ADM0') throw Error('Thailand ADM0 metadata required');
  return validateGeometry(feature.geometry);
}

function audit(polygons) {
  const results=SAMPLES.map(([name,lat,lng])=>({name,lat,lng,pickup_covered:covers(polygons,lat,lng),delivery_covered:covers(polygons,lat+0.001,lng)}));
  return {sample_count:results.length,covered:results.filter(p=>p.pickup_covered&&p.delivery_covered).length,results};
}

function liveAuditSql() {
  const values=SAMPLES.map(([name,lat,lng])=>`('${name.replace(/'/g,"''")}',${lat},${lng})`).join(',\n');
  return `-- Read only: approximate locality samples; no orders or POIs created.\nwith points(name,lat,lng) as (values\n${values}\n)\nselect name,lat,lng,public.queuego_service_area_route_status(lat::float8,lng::float8,(lat+0.001)::float8,lng::float8,'delivery') as decision from points;\n`;
}

if (require.main===module) {
  try {
    if (process.argv[2]==='--sql') process.stdout.write(liveAuditSql());
    else {
      if (!process.argv[2]) throw Error('Usage: node scripts/audit-thailand-boundary.cjs <pinned.geojson> | --sql');
      const polygons=parsePinnedBoundary(fs.readFileSync(process.argv[2]));
      console.log(JSON.stringify({status:'REVIEW_ONLY',source:PIN,polygons:polygons.length,...audit(polygons)},null,2));
    }
  } catch(error) { console.error(error.message); process.exitCode=1; }
}

module.exports={PIN,SAMPLES,validateGeometry,ringLocation,covers,parsePinnedBoundary,audit,liveAuditSql};
