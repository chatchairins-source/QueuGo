#!/usr/bin/env python3
"""Exact-source, read-only GEOS validity and source-parent containment audit.

No geometry repair, snapping, buffering, centroid replacement, cross-source union,
SQL generation or Production write is performed.
"""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile
import shapely
from shapely.geometry import shape
from shapely.prepared import prep

PINS = {
    'osm': ('2cc723c57b9a0b4ae25e92ac2fe4f5dad84db4a6dcb8e187a4062c73b562772c', None),
    'hdx': ('02370dc5bd824563de71fd71cc1ee0b88df717c935a09bd7f15611560ef8bfd1', 436725731),
}


def validity_reason(geometry):
    if geometry.geom_type not in ('Polygon', 'MultiPolygon'):
        return 'polygon geometry required'
    if geometry.is_empty:
        return 'empty geometry'
    return None if geometry.is_valid else shapely.is_valid_reason(geometry)


def contains_child(parent, child):
    return bool(parent.covers(child))


OSM_MAPPING_SHA256 = '1272a3a10d12a2d2e6757cd55f12a8363f12ecfa261323b5dbd040eeb0577909'


def audit_osm_crosswalk(features, mapping):
    """Validate the chosen OAD->OSM polygon hierarchy, without fixing geometry."""
    by_id = {}
    used = set()
    sources = {f['properties']['source_record_id']: f for f in features}
    if len(sources) != len(features):
        raise ValueError('duplicate OSM source identity')
    for row in mapping:
        if row['oad_id'] in by_id:
            raise ValueError('duplicate OAD identity')
        by_id[row['oad_id']] = row
        if row['status'] == 'deterministic':
            source_id = row['osm_source_record_id']
            if source_id in used:
                raise ValueError('crosswalk must be reverse unique')
            used.add(source_id)
            if source_id not in sources or sources[source_id]['properties']['admin_type'] != row['admin_type']:
                raise ValueError('source snapshot identity or level mismatch')
    geometries = {key: shape(feature['geometry']) for key, feature in sources.items()}
    resolved = {row['oad_id'] for row in mapping if row['admin_type'] == 'province' and row['status'] == 'deterministic'}
    prepared, summary, failures = {}, {}, []
    for kind, parent_kind in [('district', 'province'), ('subdistrict', 'district')]:
        counts = {'checked': 0, 'covered': 0, 'outside_parent': 0, 'parent_unresolved': 0, 'parent_rejected': 0}
        for row in mapping:
            if row['admin_type'] != kind or row['status'] != 'deterministic':
                continue
            counts['checked'] += 1
            parent = by_id.get(row['parent_id'])
            status = None
            if not parent or parent['status'] != 'deterministic' or parent['admin_type'] != parent_kind:
                status = 'parent_unresolved'
            elif parent['oad_id'] not in resolved:
                status = 'parent_rejected'
            else:
                source_id, parent_source_id = row['osm_source_record_id'], parent['osm_source_record_id']
                if parent_source_id not in prepared:
                    prepared[parent_source_id] = prep(geometries[parent_source_id])
                if contains_child(prepared[parent_source_id], geometries[source_id]):
                    counts['covered'] += 1
                    resolved.add(row['oad_id'])
                else:
                    status = 'outside_parent'
            if status:
                counts[status] += 1
                failures.append({'oad_id': row['oad_id'], 'admin_type': kind, 'status': status,
                                 'source_id': row['osm_source_record_id'],
                                 'parent_source_id': parent.get('osm_source_record_id') if parent else None})
        summary[kind] = counts
    return {'summary': summary, 'failures': sorted(failures, key=lambda row: row['oad_id']),
            'parent_contained_child_oad_ids': sorted(identity for identity in resolved if by_id[identity]['admin_type'] != 'province'),
            'excluded_child_oad_ids': sorted(row['oad_id'] for row in failures),
            'matched_parent_polygon_gate': 'blocked' if failures else 'passed',
            'full_canonical_coverage': False, 'production_import_allowed': False}


def audit(mode, source, mapping_file=None):
    expected_sha, expected_bytes = PINS[mode]
    with source.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    if digest != expected_sha or (expected_bytes and source.stat().st_size != expected_bytes):
        raise ValueError('exact boundary source bytes/checksum mismatch')
    if shapely.__version__ != '2.1.2':
        raise ValueError('pinned Shapely version required')
    failures, summary, crosswalk = [], {}, None
    if mode == 'osm':
        features = [json.loads(raw) for raw in source.read_text().split('\x1e') if raw.strip()]
        if len(features) != 2044:
            raise ValueError('OSM source count mismatch')
        for feature in features:
            reason = validity_reason(shape(feature['geometry']))
            if reason:
                failures.append({'source_id': feature['properties']['source_record_id'], 'reason': reason})
        summary = {'records': len(features), 'invalid': len(failures)}
        if mapping_file is not None:
            raw = mapping_file.read_bytes()
            if hashlib.sha256(raw).hexdigest() != OSM_MAPPING_SHA256:
                raise ValueError('exact OAD/OSM crosswalk checksum mismatch')
            document = json.loads(raw)
            if document.get('execution') != 'review_only_no_database_write':
                raise ValueError('review-only crosswalk required')
            if failures:
                raise ValueError('valid OSM geometry required before crosswalk containment')
            crosswalk = audit_osm_crosswalk(features, document['matches'])
            crosswalk['mapping_sha256'] = OSM_MAPPING_SHA256

    else:
        parents = {}
        with zipfile.ZipFile(source) as archive:
            for level, expected_count in enumerate([1, 77, 928, 7425]):
                counts = {'records': 0, 'invalid': 0, 'outside_parent': 0, 'missing_parent': 0}
                next_parents = {}
                for line in archive.open(f'tha_admin{level}.geojson'):
                    if not line.lstrip().startswith(b'{ "type": "Feature",'):
                        continue
                    feature = json.loads(line.rstrip().rstrip(b','))
                    props = feature['properties']
                    code = props[f'adm{level}_pcode']
                    geometry = shape(feature['geometry'])
                    counts['records'] += 1
                    reason = validity_reason(geometry)
                    if reason:
                        counts['invalid'] += 1
                        failures.append({'code': code, 'status': 'invalid_geometry', 'reason': reason})
                        continue
                    if level:
                        parent = parents.get(props[f'adm{level-1}_pcode'])
                        if parent is None:
                            counts['missing_parent'] += 1
                            failures.append({'code': code, 'status': 'parent_missing_or_invalid'})
                        elif not contains_child(parent, geometry):
                            counts['outside_parent'] += 1
                            failures.append({'code': code, 'status': 'outside_parent_polygon'})
                    if level < 3:
                        if code in next_parents:
                            raise ValueError('duplicate source parent code')
                        next_parents[code] = prep(geometry)
                if counts['records'] != expected_count:
                    raise ValueError('HDX source member count mismatch')
                summary[f'ADM{level}'] = counts
                parents = next_parents
    return {
        'execution': 'review_only_no_database_write', 'source': mode,
        'source_sha256': digest,
        'engine': f'Shapely {shapely.__version__} / GEOS {shapely.geos_version_string}',
        'summary': summary, 'failures': failures,
        'geometry_validity_gate': 'passed' if (summary['invalid'] if mode == 'osm' else sum(level['invalid'] for level in summary.values())) == 0 else 'blocked',
        'source_parent_polygon_gate': ('passed' if not failures else 'blocked') if mode == 'hdx' else 'not_tested',
        'production_import_allowed': False,
        'osm_crosswalk_parent_review': crosswalk,
        'limits': 'Source geometry QA only; does not certify OAD identity/centroids, source freshness, cross-source containment, PostGIS release acceptance or village coverage.',
    }


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=PINS)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--mapping', type=Path, help='Exact pinned review-only OAD/OSM matching output')
    args = parser.parse_args()
    if args.mapping and args.mode != 'osm':
        parser.error('--mapping applies only to the OSM source')
    if args.output.exists():
        parser.error('output must be a new file')
    report = audit(args.mode, args.source, args.mapping)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report['summary']))
    if report.get('osm_crosswalk_parent_review'):
        print(json.dumps(report['osm_crosswalk_parent_review']['summary']))
