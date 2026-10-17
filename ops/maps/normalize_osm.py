#!/usr/bin/env python3
"""Normalize real OSM topology/provenance for audits, nearest/features and offline indexes.
The routing engines ingest the identical reference-complete PBF, not re-traced geometry.
"""
import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path
import sqlite3
import osmium


def distance(a, b):
    lon1, lat1, lon2, lat2 = map(math.radians, [a[0], a[1], b[0], b[1]])
    q = math.sin((lat2-lat1)/2)**2 + math.cos(lat1)*math.cos(lat2)*math.sin((lon2-lon1)/2)**2
    return 6371008.8 * 2 * math.atan2(math.sqrt(q), math.sqrt(max(0, 1-q)))


def encode(value): return json.dumps(value, ensure_ascii=False, separators=(',', ':'))


def speed(tags):
    text = tags.get('maxspeed', '')
    try:
        raw = text.lower().replace('mph', '').replace('km/h', '').strip()
        value = float(raw) * (1.609344 if 'mph' in text.lower() else 1)
        if 0 < value <= 160: return value, 'osm:maxspeed'
    except ValueError: pass
    estimate = {'motorway': 90, 'trunk': 80, 'primary': 60, 'secondary': 50, 'tertiary': 40, 'residential': 30, 'living_street': 15, 'service': 20, 'track': 15}.get(tags.get('highway'), 30)
    return estimate, 'queuego:road_class_estimate_v1'


def build(pbf, database, schema):
    pbf, database = Path(pbf), Path(database)
    assert not database.exists(), 'Refuse to overwrite an immutable normalized graph'
    manifest = json.loads(pbf.with_suffix('.manifest.json').read_text())
    ways, node_attrs, relations, places = [], {}, [], []
    references = Counter()
    class Roads(osmium.SimpleHandler):
        def node(self, node):
            tags = dict(node.tags)
            if tags:
                node_attrs[node.id] = (tags, node.version, str(node.timestamp))
                if tags.get('name') or tags.get('name:th'):
                    if any(tags.get(k) for k in ('place','amenity','shop','tourism','railway','public_transport')):
                        places.append(('node', node.id, tags.get('name:th') or tags['name'], node.lon, node.lat,
                                       tags.get('amenity') or tags.get('shop') or tags.get('place') or tags.get('tourism') or tags.get('railway') or tags.get('public_transport'), tags))
        def way(self, way):
            tags = dict(way.tags)
            if not tags.get('highway'): return
            refs = [n.ref for n in way.nodes]
            if len(refs) < 2: return
            assert all(n.location.valid() for n in way.nodes), f'Missing reference in OSM way {way.id}'
            coordinates = [(n.lon, n.lat) for n in way.nodes]
            references.update(refs)
            ways.append((way.id, refs, coordinates, tags, way.version, str(way.timestamp)))
        def relation(self, relation):
            if relation.tags.get('type') == 'restriction':
                relations.append((relation.id, [{'type':m.type,'ref':m.ref,'role':m.role} for m in relation.members], dict(relation.tags), relation.version, str(relation.timestamp)))
    Roads().apply_file(str(pbf), locations=True, idx='flex_mem')
    assert ways, 'No real roads in extract'
    database.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(database)
    connection.executescript(Path(schema).read_text())
    connection.execute('PRAGMA synchronous=OFF'); connection.execute('PRAGMA journal_mode=MEMORY')
    connection.execute('INSERT INTO sources VALUES(?,?,?,?,?,?,?)', ('osm',manifest['source'],'ODbL-1.0',manifest['attribution'],manifest['download_url'],manifest['extract_sha256'],manifest['source_replication_timestamp']))
    coordinates_by_node = {}
    for _, refs, coordinates, _, _, _ in ways:
        coordinates_by_node.update(zip(refs, coordinates))
    # Named POIs and signals/barriers retain original node identities, including isolated ones.
    class FeatureCoordinates(osmium.SimpleHandler):
        def node(self, node):
            if node.id in node_attrs and node.location.valid(): coordinates_by_node.setdefault(node.id, (node.lon,node.lat))
    FeatureCoordinates().apply_file(str(pbf))
    for node_id, (lon,lat) in coordinates_by_node.items():
        tags, version, updated = node_attrs.get(node_id, ({},None,None))
        signal = int(tags.get('highway') == 'traffic_signals')
        connection.execute('INSERT INTO nodes VALUES(?,?,?,?,?,?,?,?,?,?,?,?)', (node_id,lon,lat,encode(tags),signal,tags.get('barrier'),tags.get('railway'),'osm',version,updated,None,'open_data_unverified'))
        connection.execute('INSERT INTO node_spatial VALUES(?,?,?,?,?)',(node_id,lon,lon,lat,lat))
    for way_id, refs, coordinates, tags, version, updated in ways:
        connection.execute('INSERT INTO ways VALUES(?,?,?,?,?,?,?)',(way_id,encode(refs),encode(coordinates),encode(tags),'osm',version,updated))
        breaks = [0] + [i for i in range(1,len(refs)-1) if references[refs[i]] > 1 or node_attrs.get(refs[i],({},))[0].get('highway') == 'traffic_signals' or node_attrs.get(refs[i],({},))[0].get('barrier')] + [len(refs)-1]
        for start,end in zip(breaks,breaks[1:]):
            shape = coordinates[start:end+1]
            length = sum(distance(a,b) for a,b in zip(shape,shape[1:]))
            if length <= 0: continue
            identifier = f'osm:way:{way_id}:' + hashlib.sha256(encode(refs[start:end+1]).encode()).hexdigest()[:20]
            kph, speed_source = speed(tags)
            oneway = tags.get('oneway', 'yes' if tags.get('junction') == 'roundabout' or tags.get('highway') == 'motorway' else 'no')
            values = (identifier,way_id,refs[start],refs[end],encode(shape),length,tags['highway'],tags.get('name:th') or tags.get('name'),oneway,tags.get('oneway:motorcycle'),tags.get('access'),tags.get('vehicle'),tags.get('motor_vehicle'),tags.get('motorcycle'),tags.get('bridge'),tags.get('tunnel'),tags.get('layer'),int(tags['highway'].endswith('_link')),tags.get('service'),tags.get('junction'),tags.get('maxspeed'),kph,speed_source,tags.get('construction') or ('yes' if tags['highway'] == 'construction' else None),tags.get('closure') or tags.get('closed'),encode(tags),'osm',version,updated,None,'open_data_unverified')
            cursor = connection.execute('INSERT INTO segments('+','.join('id way_id from_node to_node geometry distance_m road_class name oneway motorcycle_oneway access vehicle_access motor_vehicle_access motorcycle_access bridge tunnel layer ramp service junction maxspeed_raw speed_kph speed_source construction closure attributes source_id source_version source_updated_at last_verified confidence'.split())+') VALUES('+','.join('?' for _ in values)+')', values)
            connection.execute('INSERT INTO segment_spatial VALUES(?,?,?,?,?)',(cursor.lastrowid,min(p[0] for p in shape),max(p[0] for p in shape),min(p[1] for p in shape),max(p[1] for p in shape)))
    for relation_id,members,tags,version,updated in relations:
        def refs(role, kind): return [m['ref'] for m in members if m['role']==role and m['type']==kind]
        connection.execute('INSERT INTO turn_restrictions VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',(relation_id,encode(refs('from','w')),encode(refs('to','w')),encode(refs('via','n')),encode(refs('via','w')),tags.get('restriction'),tags.get('restriction:conditional'),tags.get('except'),encode(members),encode(tags),'osm',version,updated,None,'open_data_unverified'))
    for kind,osm_id,name,lon,lat,category,tags in places:
        cursor=connection.execute('INSERT INTO places(osm_type,osm_id,name,longitude,latitude,category,tags,source_id) VALUES(?,?,?,?,?,?,?,?)',(kind,osm_id,name,lon,lat,category,encode(tags),'osm'))
        connection.execute('INSERT INTO place_spatial VALUES(?,?,?,?,?)',(cursor.lastrowid,lon,lon,lat,lat))
        connection.execute('INSERT INTO place_search(rowid,name) VALUES(?,?)',(cursor.lastrowid,name))
    connection.execute('INSERT INTO metadata VALUES(?,?)',('dataset',encode(manifest)))
    connection.commit()
    assert not connection.execute('PRAGMA foreign_key_check').fetchall()
    assert connection.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    counts = {table:connection.execute(f'SELECT count(*) FROM {table}').fetchone()[0] for table in ('nodes','ways','segments','turn_restrictions','places')}
    counts.update(traffic_signals=connection.execute('SELECT count(*) FROM nodes WHERE traffic_signal=1').fetchone()[0], barriers=connection.execute('SELECT count(*) FROM nodes WHERE barrier IS NOT NULL').fetchone()[0])
    connection.close()
    report={'schema_version':1,'dataset':manifest,'counts':counts,'database_bytes':database.stat().st_size,'conflation':'OSM identity and shared nodes only; no geometric union','physical_verification':False}
    database.with_suffix('.report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(report,ensure_ascii=False))

if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('pbf');p.add_argument('database');p.add_argument('--schema',default='queuego-map-engine/schema.sql');a=p.parse_args();build(a.pbf,a.database,a.schema)
