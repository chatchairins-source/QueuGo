#!/usr/bin/env python3
"""Reference-complete OSM regional extract; preserve restriction relations and provenance."""
import argparse
import hashlib
import json
from pathlib import Path
from datetime import datetime, timezone
import osmium


def sha256(path):
    digest = hashlib.sha256()
    with open(path, 'rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def extract(source, output, bounds):
    west, south, east, north = bounds
    assert -180 <= west < east <= 180 and -90 <= south < north <= 90
    source, output = Path(source).resolve(), Path(output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    assert not output.exists(), 'Refuse to replace an existing immutable extract'
    selected_ways = set()
    counts = {'nodes': 0, 'ways': 0, 'restriction_relations': 0}
    with osmium.BackReferenceWriter(str(output), str(source), remove_tags=False, relation_depth=2) as writer:
        class Region(osmium.SimpleHandler):
            def node(self, node):
                if node.location.valid() and west <= node.location.lon <= east and south <= node.location.lat <= north:
                    writer.add_node(node)
                    counts['nodes'] += 1
            def way(self, way):
                if not way.tags.get('highway') and not any(way.tags.get(k) for k in ('name', 'amenity', 'shop', 'tourism', 'place', 'railway', 'barrier')):
                    return
                if any(n.location.valid() and west <= n.lon <= east and south <= n.lat <= north for n in way.nodes):
                    selected_ways.add(way.id)
                    writer.add_way(way)
                    counts['ways'] += 1
            def relation(self, relation):
                if relation.tags.get('type') == 'restriction' and any(m.type == 'w' and m.ref in selected_ways for m in relation.members):
                    writer.add_relation(relation)
                    counts['restriction_relations'] += 1
        Region().apply_file(str(source), locations=True, idx='flex_mem')
    with osmium.io.Reader(str(source)) as reader:
        timestamp = reader.header().get('osmosis_replication_timestamp')
    manifest = {
        'schema_version': 1, 'region': 'buriram-padded', 'bounds': bounds,
        'coverage_note': 'Padded Buriram operating envelope; includes adjacent border roads, not an administrative-boundary claim.',
        'source': 'OpenStreetMap / Geofabrik Thailand', 'licence': 'ODbL-1.0',
        'attribution': '© OpenStreetMap contributors',
        'download_url': 'https://download.geofabrik.de/asia/thailand-latest.osm.pbf',
        'source_replication_timestamp': timestamp or None,
        'source_sha256': sha256(source), 'extract_sha256': sha256(output),
        'extract_bytes': output.stat().st_size, 'selected_counts': counts,
        'built_at': datetime.now(timezone.utc).isoformat(),
        'restriction_handling': 'Reference-complete selected restriction relations, with original tags and members; no fabricated topology.'
    }
    output.with_suffix('.manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(manifest, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source'); parser.add_argument('output')
    parser.add_argument('--bounds', default='102.369,14.109,103.592,15.819')
    args = parser.parse_args()
    extract(args.source, args.output, [float(v) for v in args.bounds.split(',')])
