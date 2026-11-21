#!/usr/bin/env python3
"""Export the existing normalized OSM places table for Web/Android read-only use."""
import argparse,hashlib,json,sqlite3
from collections import Counter
from pathlib import Path

BUSINESS = {
 'food': {'restaurant','cafe','fast_food','food_court','ice_cream','bar','pub','bakery'},
 'grocery': {'supermarket','convenience','greengrocer','general','butcher','seafood','beverages','deli','wholesale'},
 'laundry': {'laundry','dry_cleaning'},
 'fuel': {'fuel','charging_station'},
 'health': {'hospital','clinic','doctors','dentist','pharmacy','optician'},
 'hotel': {'hotel','motel','guest_house','hostel','resort'},
 'finance': {'bank','atm','bureau_de_change'},
 'education': {'school','kindergarten','college','university','language_school'},
}
def category(value,tags):
 for group,values in BUSINESS.items():
  if value in values:return group
 if tags.get('shop') or tags.get('office') or tags.get('craft'):return 'business'
 return 'place'

def export(database,output):
 db=sqlite3.connect(f'file:{Path(database).resolve()}?mode=ro',uri=True);db.row_factory=sqlite3.Row
 dataset=json.loads(db.execute("SELECT value FROM metadata WHERE key='dataset'").fetchone()[0])
 west,south,east,north=dataset['bounds'];records=[];seen=set()
 for row in db.execute('SELECT * FROM places ORDER BY osm_type,osm_id'):
  lon,lat=row['longitude'],row['latitude']
  if not west<=lon<=east or not south<=lat<=north:continue
  key=f"osm:{row['osm_type']}:{row['osm_id']}"
  if key in seen:raise ValueError('Duplicate OSM identity')
  seen.add(key);tags=json.loads(row['tags']);name=row['name'].strip()
  if not name:continue
  address={k[5:]:v for k,v in tags.items() if k.startswith('addr:') and str(v).strip()}
  records.append({
   'id':key,'name':name,'aliases':sorted({str(tags[k]).strip() for k in ('name','name:th','name:en','alt_name','official_name','brand') if tags.get(k)}),
   'lat':round(lat,7),'lng':round(lon,7),'category':category(row['category'],tags),'kind':row['category'],
   'address':address,'phone':tags.get('contact:phone') or tags.get('phone'),
   'website':tags.get('contact:website') or tags.get('website'),'opening_hours':tags.get('opening_hours'),
   'source_url':f"https://www.openstreetmap.org/{row['osm_type']}/{row['osm_id']}",
   'position_precision':'osm_node' if row['osm_type']=='node' else 'osm_area_representative_unverified',
   'verified':False,'orderable':False})
 db.close()
 if not records:raise ValueError('Refuse empty catalog')
 result={'schema_version':1,'region':'buriram-padded','coverage_note':dataset['coverage_note'],
  'bounds':dataset['bounds'],'source':'OpenStreetMap / Geofabrik Thailand','source_timestamp':dataset['source_replication_timestamp'],
  'source_extract_sha256':dataset['extract_sha256'],'licence':'ODbL-1.0','licence_url':'https://opendatacommons.org/licenses/odbl/1.0/',
  'attribution':'© OpenStreetMap contributors','attribution_url':'https://www.openstreetmap.org/copyright',
  'records':records}
 out=Path(output);out.parent.mkdir(parents=True,exist_ok=True)
 out.write_text(json.dumps(result,ensure_ascii=False,separators=(',',':'))+'\n')
 print(json.dumps({'records':len(records),'categories':dict(Counter(x['category'] for x in records)),
  'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'bytes':out.stat().st_size},ensure_ascii=False))
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('database');p.add_argument('output');a=p.parse_args();export(a.database,a.output)
