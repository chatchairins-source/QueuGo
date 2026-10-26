#!/usr/bin/env python3
"""Direct exact-source OSM-vs-HDX geometry diagnostics for quarantined Thai admin rows.

Read-only only: no buffer, snap, make-valid, union, coordinate rewrite, SQL or Production write.
The report compares topological relationships only and never promotes either source.
"""
import argparse, hashlib, json, zipfile
from collections import Counter
from pathlib import Path
import shapely
from shapely.geometry import shape

OSM_SHA256='2cc723c57b9a0b4ae25e92ac2fe4f5dad84db4a6dcb8e187a4062c73b562772c'
MAPPING_SHA256='1272a3a10d12a2d2e6757cd55f12a8363f12ecfa261323b5dbd040eeb0577909'
HDX_SHA256='02370dc5bd824563de71fd71cc1ee0b88df717c935a09bd7f15611560ef8bfd1'
HDX_BYTES=436725731
EXCLUDED={'6211','9202','104101','310108','671003','840501','621101','621102','621103'}

def sha256_file(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream,'sha256').hexdigest()

def validate_polygon(geometry,label):
    if geometry.geom_type not in ('Polygon','MultiPolygon'):
        raise ValueError(f'{label}: polygon geometry required')
    if geometry.is_empty or not geometry.is_valid:
        raise ValueError(f'{label}: valid non-empty geometry required')

def relation(osm_geometry,hdx_geometry):
    validate_polygon(osm_geometry,'OSM'); validate_polygon(hdx_geometry,'HDX')
    if osm_geometry.equals(hdx_geometry): classification='topologically_equal'
    elif osm_geometry.covers(hdx_geometry): classification='osm_covers_hdx'
    elif hdx_geometry.covers(osm_geometry): classification='hdx_covers_osm'
    elif osm_geometry.overlaps(hdx_geometry): classification='overlap_non_containing'
    elif osm_geometry.touches(hdx_geometry): classification='touch_only'
    elif osm_geometry.intersects(hdx_geometry): classification='intersects_non_overlap'
    else: classification='disjoint'
    return {'classification':classification,'de9im':osm_geometry.relate(hdx_geometry),
      'intersects':bool(osm_geometry.intersects(hdx_geometry)),
      'osm_covers_hdx':bool(osm_geometry.covers(hdx_geometry)),
      'hdx_covers_osm':bool(hdx_geometry.covers(osm_geometry)),
      'overlaps':bool(osm_geometry.overlaps(hdx_geometry)),
      'touches':bool(osm_geometry.touches(hdx_geometry)),
      'equals':bool(osm_geometry.equals(hdx_geometry))}

def source_parent_evidence(child_osm,parent_osm,child_hdx,parent_hdx):
    for label,g in [('child OSM',child_osm),('parent OSM',parent_osm),('child HDX',child_hdx),('parent HDX',parent_hdx)]:
        validate_polygon(g,label)
    return {'osm_parent_covers_osm_child':bool(parent_osm.covers(child_osm)),
      'hdx_parent_covers_hdx_child':bool(parent_hdx.covers(child_hdx)),
      'hdx_parent_covers_osm_child':bool(parent_hdx.covers(child_osm)),
      'osm_parent_covers_hdx_child':bool(parent_osm.covers(child_hdx)),
      'parent_cross_source_relation':relation(parent_osm,parent_hdx)}

def load_osm(path):
    if sha256_file(path)!=OSM_SHA256: raise ValueError('OSM normalized checksum mismatch')
    features=[json.loads(raw) for raw in path.read_text().split('\x1e') if raw.strip()]
    if len(features)!=2044: raise ValueError('unexpected OSM admin feature count')
    out={}
    for feature in features:
        source_id=feature['properties']['source_record_id']
        if source_id in out: raise ValueError('duplicate OSM source identity')
        geometry=shape(feature['geometry']); validate_polygon(geometry,source_id)
        out[source_id]={'geometry':geometry,'admin_type':feature['properties']['admin_type']}
    return out

def load_mapping(path):
    if sha256_file(path)!=MAPPING_SHA256: raise ValueError('mapping checksum mismatch')
    document=json.loads(path.read_text())
    if document.get('execution')!='review_only_no_database_write': raise ValueError('review-only mapping required')
    rows={}
    for row in document['matches']:
        if row['oad_id'] in rows: raise ValueError('duplicate OAD mapping identity')
        rows[row['oad_id']]=row
    return rows

def target_chain(rows,start):
    chain=[]; current=rows.get(start)
    if current is None: raise ValueError('missing excluded OAD mapping row '+start)
    while current is not None:
        chain.append(current['oad_id'])
        parent_id=current.get('parent_id')
        if not parent_id: break
        current=rows.get(parent_id)
        if current is None: raise ValueError('missing mapped ancestor '+parent_id)
    return chain

def load_hdx(path,target_ids):
    if path.stat().st_size!=HDX_BYTES or sha256_file(path)!=HDX_SHA256:
        raise ValueError('HDX checksum/size mismatch')
    out={}
    with zipfile.ZipFile(path) as archive:
        for level in (1,2,3):
            for line in archive.open(f'tha_admin{level}.geojson'):
                if not line.lstrip().startswith(b'{ "type": "Feature",'): continue
                feature=json.loads(line.rstrip().rstrip(b','))
                props=feature['properties']; pcode=props[f'adm{level}_pcode']
                oad_id=pcode[2:] if pcode.startswith('TH') else None
                if oad_id not in target_ids: continue
                if oad_id in out: raise ValueError('duplicate HDX target pcode '+pcode)
                geometry=shape(feature['geometry']); validate_polygon(geometry,pcode)
                out[oad_id]={'pcode':pcode,'geometry':geometry,
                  'name':props.get(f'adm{level}_name1') or props.get(f'adm{level}_name'),'level':level}
    missing=sorted(target_ids-set(out))
    if missing: raise ValueError('missing HDX target geometry: '+','.join(missing))
    return out

def validate_crosswalk(path):
    document=json.loads(path.read_text())
    if document.get('execution')!='review_only_no_database_write' or document.get('production_import_allowed') is not False:
        raise ValueError('review-only OSM crosswalk report required')
    review=document.get('osm_crosswalk_parent_review') or {}
    failures={row['oad_id']:row for row in review.get('failures',[])}
    if set(failures)!=EXCLUDED: raise ValueError('unexpected OSM exclusion set')
    if review.get('production_import_allowed') is not False: raise ValueError('OSM crosswalk must remain fail closed')
    return failures

def build_rows(rows,osm,hdx,failures):
    output=[]
    for oad_id in sorted(EXCLUDED):
        mapping=rows[oad_id]
        if mapping['status']!='deterministic': raise ValueError('excluded mapping must be deterministic')
        source_id=mapping['osm_source_record_id']
        if source_id not in osm or osm[source_id]['admin_type']!=mapping['admin_type']:
            raise ValueError('missing or mismatched OSM source '+source_id)
        osm_geometry=osm[source_id]['geometry']; hdx_geometry=hdx[oad_id]['geometry']
        chain=target_chain(rows,oad_id); ancestors=[]
        for node_id in chain:
            node=rows[node_id]
            if node['status']!='deterministic': raise ValueError('ancestor mapping must be deterministic')
            node_source=node['osm_source_record_id']
            if node_source not in osm or node_id not in hdx: raise ValueError('missing cross-source ancestor '+node_id)
            ancestors.append({'oad_id':node_id,'admin_type':node['admin_type'],
              'osm_source_record_id':node_source,'hdx_pcode':hdx[node_id]['pcode'],
              'cross_source_relation':relation(osm[node_source]['geometry'],hdx[node_id]['geometry'])})
        direct_parent=None
        if len(chain)>1:
            parent_id=chain[1]; parent=rows[parent_id]
            direct_parent={'oad_id':parent_id,**source_parent_evidence(
              osm_geometry,osm[parent['osm_source_record_id']]['geometry'],hdx_geometry,hdx[parent_id]['geometry'])}
        inherited=[{'oad_id':x,'status':failures[x]['status']} for x in chain[1:] if x in failures]
        output.append({'oad_id':oad_id,'admin_type':mapping['admin_type'],
          'osm_failure_status':failures[oad_id]['status'],'osm_source_record_id':source_id,
          'hdx_pcode':hdx[oad_id]['pcode'],'direct_cross_source_relation':relation(osm_geometry,hdx_geometry),
          'source_parent_evidence':direct_parent,'ancestor_chain':ancestors,
          'inherited_osm_failure_chain':inherited,
          'diagnostic_scope':'raw exact-source topology only; no geometry repair or canonical source selection',
          'geometry_override_allowed':False,'canonical_geometry_approved':False,
          'eligible':False,'status':'quarantined'})
    return output

def build_report(osm_path,mapping_path,crosswalk_path,hdx_path):
    if shapely.__version__!='2.1.2': raise ValueError('pinned Shapely version required')
    osm=load_osm(osm_path); rows=load_mapping(mapping_path); failures=validate_crosswalk(crosswalk_path)
    target_ids=set()
    for oad_id in EXCLUDED: target_ids.update(target_chain(rows,oad_id))
    hdx=load_hdx(hdx_path,target_ids)
    diagnostics=build_rows(rows,osm,hdx,failures)
    classes=Counter(row['direct_cross_source_relation']['classification'] for row in diagnostics)
    parent_patterns=Counter()
    for row in diagnostics:
        p=row['source_parent_evidence']
        if p is not None:
            key=f"osm_{'covers' if p['osm_parent_covers_osm_child'] else 'rejects'}__hdx_{'covers' if p['hdx_parent_covers_hdx_child'] else 'rejects'}"
            parent_patterns[key]+=1
    return {'execution':'review_only_no_database_write',
      'source_pins':{'osm_normalized_sha256':OSM_SHA256,'oad_osm_mapping_sha256':MAPPING_SHA256,
        'hdx_sha256':HDX_SHA256,'hdx_bytes':HDX_BYTES},
      'engine':f'Shapely {shapely.__version__} / GEOS {shapely.geos_version_string}',
      'summary':{'quarantined_rows':len(diagnostics),
        'direct_cross_source_classifications':dict(sorted(classes.items())),
        'source_parent_patterns':dict(sorted(parent_patterns.items())),
        'geometry_override_allowed_rows':0,'eligible_rows':0},
      'rows':diagnostics,'canonical_source_selected':False,'production_import_allowed':False,
      'limits':'Cross-source diagnostic only. Same-code comparison does not establish current canonical identity, source freshness, licensed promotion eligibility or PostGIS release acceptance. No buffer, snap, make-valid, union or coordinate rewrite is performed.',
      'next':'Use exact disagreement patterns to target newer/current independently licensed boundary evidence for the nine quarantined identities. Preserve all OSM quarantines and current primary-code blockers until independent identity, freshness, provenance, hierarchy and PostGIS gates pass.'}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('osm',type=Path); parser.add_argument('mapping',type=Path)
    parser.add_argument('crosswalk',type=Path); parser.add_argument('hdx',type=Path); parser.add_argument('output',type=Path)
    args=parser.parse_args()
    if args.output.exists(): parser.error('output must be a new file')
    report=build_report(args.osm,args.mapping,args.crosswalk,args.hdx)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(report['summary'],ensure_ascii=False))
if __name__=='__main__': main()
