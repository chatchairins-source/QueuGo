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


def audit(mode, source):
    expected_sha, expected_bytes = PINS[mode]
    with source.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    if digest != expected_sha or (expected_bytes and source.stat().st_size != expected_bytes):
        raise ValueError('exact boundary source bytes/checksum mismatch')
    if shapely.__version__ != '2.1.2':
        raise ValueError('pinned Shapely version required')
    failures, summary = [], {}
    if mode == 'osm':
        features = [json.loads(raw) for raw in source.read_text().split('\x1e') if raw.strip()]
        if len(features) != 2044:
            raise ValueError('OSM source count mismatch')
        for feature in features:
            reason = validity_reason(shape(feature['geometry']))
            if reason:
                failures.append({'source_id': feature['properties']['source_record_id'], 'reason': reason})
        summary = {'records': len(features), 'invalid': len(failures)}
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
        'limits': 'Source geometry QA only; does not certify OAD identity/centroids, source freshness, cross-source containment, PostGIS release acceptance or village coverage.',
    }


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=PINS)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    if args.output.exists():
        parser.error('output must be a new file')
    report = audit(args.mode, args.source)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report['summary']))
