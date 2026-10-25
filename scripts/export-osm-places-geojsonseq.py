#!/usr/bin/env python3
"""Export minimal OSM Thailand point and area records as RFC 8142 GeoJSONSeq."""
import argparse
import json
import os
from pathlib import Path
import sys

import osmium

ALLOWED_TAGS = {
    'shop', 'amenity', 'tourism', 'leisure', 'historic', 'craft', 'office',
    'public_transport', 'railway', 'highway', 'aeroway', 'name', 'name:th',
    'name:en', 'brand', 'addr:housenumber', 'addr:street', 'addr:suburb',
    'addr:city_district', 'addr:subdistrict', 'addr:subdistrict:th',
    'addr:district', 'addr:district:th', 'addr:county', 'addr:state',
    'addr:province', 'addr:province:th', 'addr:city', 'addr:village',
    'addr:hamlet', 'addr:place', 'addr:moo', 'addr:postcode'
}
CATEGORY_TAGS = ALLOWED_TAGS - {
    key for key in ALLOWED_TAGS
    if key.startswith('addr:') or key in {'name', 'name:th', 'name:en', 'brand'}
}
NAME_TAGS = {'name', 'name:th', 'name:en', 'brand'}


class PlaceExporter(osmium.SimpleHandler):
    def __init__(self, handle):
        super().__init__()
        self.handle = handle
        self.geometry_factory = osmium.geom.GeoJSONFactory()
        self.counts = {
            'nodes_written': 0,
            'areas_written': 0,
            'skipped_without_name_or_category': 0,
            'skipped_invalid_geometry': 0
        }

    def emit(self, obj, osm_type, osm_id, geometry_factory):
        tags = {key: str(value) for key, value in dict(obj.tags).items()
                if key in ALLOWED_TAGS and str(value).strip()}
        if not tags.keys() & NAME_TAGS or not tags.keys() & CATEGORY_TAGS:
            self.counts['skipped_without_name_or_category'] += 1
            return
        try:
            geometry = json.loads(geometry_factory())
        except (RuntimeError, ValueError):
            self.counts['skipped_invalid_geometry'] += 1
            return
        properties = {
            '@type': osm_type,
            '@id': int(osm_id),
            '@timestamp': int(obj.timestamp.timestamp())
        }
        properties.update(tags)
        feature = {'type': 'Feature', 'properties': properties, 'geometry': geometry}
        self.handle.write('\x1e' + json.dumps(feature, ensure_ascii=False, separators=(',', ':')) + '\n')
        self.counts['nodes_written' if osm_type == 'node' else 'areas_written'] += 1

    def node(self, node):
        if not node.location.valid():
            self.counts['skipped_invalid_geometry'] += 1
            return
        self.emit(node, 'node', node.id, lambda: self.geometry_factory.create_point(node))

    def area(self, area):
        osm_type = 'way' if area.from_way() else 'relation'
        self.emit(area, osm_type, area.orig_id(),
                  lambda: self.geometry_factory.create_multipolygon(area))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('pbf', type=Path, help='exact Geofabrik Thailand PBF snapshot')
    parser.add_argument('output', type=Path, help='new GeoJSONSeq output path')
    args = parser.parse_args()
    if not args.pbf.is_file():
        parser.error('PBF input file does not exist')
    if args.output.exists():
        parser.error('output path already exists; choose a fresh path')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name(args.output.name + '.partial')
    if temporary.exists():
        parser.error('partial output path already exists; inspect it before retrying')
    try:
        with temporary.open('x', encoding='utf-8', newline='\n') as handle:
            exporter = PlaceExporter(handle)
            # An area callback enables PyOsmium's node-location and multipolygon
            # processing. Invalid geometries are counted and skipped by emit().
            exporter.apply_file(str(args.pbf))
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, args.output)
        print(json.dumps(exporter.counts, sort_keys=True), file=sys.stderr)
    except Exception:
        temporary.unlink(missing_ok=True)
        raise


if __name__ == '__main__':
    main()
