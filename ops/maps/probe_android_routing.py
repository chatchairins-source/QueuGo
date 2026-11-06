"""Probe real packaged Thailand tiles; Linux evidence is separate from Android QA."""
import argparse
import json
import math
from pathlib import Path
import valhalla


PAIRS = [
    ('buriram', (14.9942, 103.1020), (15.0054, 103.1114)),
    ('bangkok', (13.7563, 100.5018), (13.7465, 100.5340)),
    ('chiangmai', (18.7883, 98.9853), (18.7901, 98.9987)),
    ('khonkaen', (16.4322, 102.8236), (16.4451, 102.8358)),
    ('hatyai', (7.0086, 100.4747), (7.0164, 100.4891)),
    ('bangkok-chiangmai', (13.7563, 100.5018), (18.7883, 98.9853)),
]


def decode_shape(encoded):
    values, index, lat, lon = [], 0, 0, 0
    while index < len(encoded):
        deltas = []
        for _ in range(2):
            result = shift = 0
            while True:
                if index >= len(encoded) or shift > 30:
                    raise ValueError('Malformed polyline6')
                digit = ord(encoded[index]) - 63
                index += 1
                if not 0 <= digit <= 63:
                    raise ValueError('Invalid polyline6 character')
                result |= (digit & 31) << shift
                if digit < 32:
                    break
                shift += 5
            deltas.append(~(result >> 1) if result & 1 else result >> 1)
        lat += deltas[0]
        lon += deltas[1]
        values.append((lat / 1e6, lon / 1e6))
    return values


def source_position(point, way):
    """Metres along the original OSM way, with closest-projection distance."""
    latitude_scale = 111_195.08
    longitude_scale = latitude_scale * math.cos(math.radians(point[0]))
    best, walked = None, 0.0
    for start, end in zip(way, way[1:]):
        dx = (end[1] - start[1]) * longitude_scale
        dy = (end[0] - start[0]) * latitude_scale
        length = math.hypot(dx, dy)
        if length == 0:
            continue
        px = (point[1] - start[1]) * longitude_scale
        py = (point[0] - start[0]) * latitude_scale
        fraction = max(0, min(1, (px * dx + py * dy) / length**2))
        separation = math.hypot(px - fraction * dx, py - fraction * dy)
        candidate = separation, walked + fraction * length
        if best is None or candidate < best:
            best = candidate
        walked += length
    if best is None:
        raise ValueError('Source way has no measurable geometry')
    return best


def route(actor, origin, destination, cutoff=250):
    return actor.route({
        'locations': [{'lat': p[0], 'lon': p[1], 'type': 'break',
                       'search_cutoff': cutoff} for p in (origin, destination)],
        'costing': 'motorcycle', 'units': 'kilometers', 'language': 'en-US',
        'shape_format': 'polyline6', 'directions_type': 'instructions',
        'costing_options': {'motorcycle': {'use_highways': 0, 'use_tolls': 0}},
    })


def probe_source_case(actor, case):
    response = route(actor, case['origin'], case['destination'])
    shape = response['trip']['legs'][0]['shape']
    trace = actor.trace_attributes({
        'encoded_polyline': shape, 'costing': 'motorcycle', 'shape_match': 'edge_walk',
        'filters': {'action': 'include', 'attributes': [
            'edge.way_id', 'edge.begin_shape_index', 'edge.end_shape_index', 'edge.drive_on_right']},
    })
    edges = trace['edges']
    ids = [edge['way_id'] for edge in edges]
    compact = [way for index, way in enumerate(ids) if index == 0 or ids[index - 1] != way]
    if not compact or any(edge['drive_on_right'] for edge in edges):
        raise ValueError('Missing graph edges or unexpected right-hand driving in Thailand')
    if case['kind'] == 'forbidden_turn':
        if case['restriction'] not in ('no_left_turn', 'no_right_turn', 'no_straight_on'):
            raise ValueError('Unsupported source restriction probe')
        # A detour alone is insufficient: wrong-road endpoint snapping can give a false PASS.
        if compact[0] != case['from_way'] or compact[-1] != case['to_way']:
            raise ValueError('Restriction endpoints did not anchor on the source member ways')
        if any(a == case['from_way'] and b == case['to_way'] for a, b in zip(compact, compact[1:])):
            raise ValueError('Route traverses the source-prohibited way transition')
    else:
        if case['kind'] != 'oneway' or case['oneway'] != 'yes':
            raise ValueError('Unsupported source one-way probe')
        if compact[0] != case['way_id'] or compact[-1] != case['way_id'] or len(compact) < 3:
            raise ValueError('One-way reverse endpoints did not produce an anchored detour')
        points = decode_shape(shape)
        for edge in edges:
            if edge['way_id'] != case['way_id']:
                continue
            before = source_position(points[edge['begin_shape_index']], case['way_points'])
            after = source_position(points[edge['end_shape_index']], case['way_points'])
            if before[0] > 5 or after[0] > 5 or after[1] < before[1] - 2:
                raise ValueError('Graph traverses backwards or outside the original one-way geometry')
    return {'kind': case['kind'], 'relation_id': case.get('relation_id'),
            'way_id': case.get('way_id'), 'route_way_ids': compact,
            'length_km': response['trip']['summary']['length'], 'status': 'PASS_LINUX_ENGINE_ONLY'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--assets', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    root = Path(args.assets).resolve() / 'routing'
    manifest = json.loads((root / 'manifest.json').read_text())
    config = json.loads((root / 'valhalla.json').read_text())
    config['mjolnir']['tile_dir'] = ''
    config['mjolnir']['tile_extract'] = str(root / 'tiles.tar')
    assert not config['mjolnir'].get('tile_url') and not config['mjolnir'].get('traffic_extract')
    actor = valhalla.Actor(config)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    results = []
    for name, origin, destination in PAIRS:
        try:
            response = route(actor, origin, destination)
            (output.parent / (name + '-route.json')).write_text(json.dumps(response, ensure_ascii=False))
            results.append({'name': name, 'from': origin, 'to': destination,
                            'summary': response['trip']['summary'],
                            'maneuvers': len(response['trip']['legs'][0]['maneuvers']),
                            'status': 'PASS_LINUX_ENGINE_ONLY'})
        except Exception as error:
            results.append({'name': name, 'status': 'FAIL', 'error': str(error)})
    source = json.loads((Path(__file__).parent / 'sources/thailand-261104-routing-probes.json').read_text())
    assert source['dataset_sha256'] == manifest['source']['extract_sha256']
    for case in source['cases']:
        try:
            results.append(probe_source_case(actor, case))
        except Exception as error:
            results.append({'kind': case['kind'], 'relation_id': case.get('relation_id'),
                            'way_id': case.get('way_id'), 'status': 'FAIL', 'error': str(error)})
    report = {'engine': 'pyvalhalla 3.9.1', 'dataset_sha256': source['dataset_sha256'],
              'graph_sha256': manifest['graph']['sha256'],
              'platform': 'Linux x86_64; not Android emulator or physical road QA', 'results': results}
    output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'passed': sum(x['status'] != 'FAIL' for x in results),
                      'failed': sum(x['status'] == 'FAIL' for x in results)}))
    if any(result['status'] == 'FAIL' for result in results):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
