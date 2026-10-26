#!/usr/bin/env python3
"""Export Thailand OSM administrative areas for QueueGo hierarchy review only."""
import argparse
import json
import os
from pathlib import Path

import osmium

ADMIN_LEVELS = {'4': 'province', '6': 'district', '8': 'subdistrict'}
ALLOWED_TAGS = {
    'boundary', 'admin_level', 'name', 'name:th', 'name:en',
    'official_name', 'ref', 'ISO3166-2'
}


class AdminBoundaryExporter(osmium.SimpleHandler):
    def __init__(self, handle):
        super().__init__()
        self.handle = handle
        self.geometry_factory = osmium.geom.GeoJSONFactory()
        self.counts = {
            'province': 0,
            'district': 0,
            'subdistrict': 0,
            'skipped_without_name': 0,
            'skipped_invalid_geometry': 0
        }

    def area(self, area):
        raw = dict(area.tags)
        level = str(raw.get('admin_level', '')).strip()
        if str(raw.get('boundary', '')).strip() != 'administrative' or level not in ADMIN_LEVELS:
            return
        tags = {key: str(raw[key]).strip() for key in ALLOWED_TAGS if key in raw and str(raw[key]).strip()}
        name = tags.get('name:th') or tags.get('name') or tags.get('name:en')
        if not name:
            self.counts['skipped_without_name'] += 1
            return
        try:
            geometry = json.loads(self.geometry_factory.create_multipolygon(area))
        except (RuntimeError, ValueError):
            self.counts['skipped_invalid_geometry'] += 1
            return
        osm_type = 'way' if area.from_way() else 'relation'
        props = {
            '@type': osm_type,
            '@id': int(area.orig_id()),
            '@timestamp': int(area.timestamp.timestamp())
        }
        props.update(tags)
        feature = {'type': 'Feature', 'properties': props, 'geometry': geometry}
        self.handle.write('\x1e' + json.dumps(feature, ensure_ascii=False, separators=(',', ':')) + '\n')
        self.counts[ADMIN_LEVELS[level]] += 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('pbf', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    if not args.pbf.is_file():
        parser.error('PBF input file does not exist')
    if args.output.exists():
        parser.error('output path already exists')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name(args.output.name + '.partial')
    if temporary.exists():
        parser.error('partial output path already exists')
    try:
        with temporary.open('x', encoding='utf-8', newline='\n') as handle:
            exporter = AdminBoundaryExporter(handle)
            exporter.apply_file(str(args.pbf), locations=True)
        os.replace(temporary, args.output)
        print(json.dumps(exporter.counts, sort_keys=True))
    except Exception:
        if temporary.exists():
            temporary.unlink()
        raise


if __name__ == '__main__':
    main()
