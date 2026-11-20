#!/usr/bin/env python3
"""Isolated unit fixtures: partial vendor traces must never certify a route."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'queuego-map-engine'))
from core import Engine,explicit_osm_maxspeed,restriction_source_error,route_maxspeed_intervals

assert restriction_source_error([1],[2],[3,4],[],'no_u_turn')
assert restriction_source_error([1,2],[3],[4],[],'no_u_turn')
assert restriction_source_error([1],[2,3],[4],[],'only_straight_on')
assert restriction_source_error([1],[2],[3],[4],'no_right_turn')
assert not restriction_source_error([1,2],[3],[4],[],'no_entry')
assert not restriction_source_error([1],[2,3],[4],[],'no_exit')
assert not restriction_source_error([1],[2],[],[3,4],'no_u_turn')
# Standard encoded three-point polyline; unit-only, never included in any package.
leg={'shape':'_p~iF~ps|U_ulLnnqC_mqNvxq`@','summary':{'length':1}}
engine=Engine.__new__(Engine)
partial={'edges':[{'begin_shape_index':0,'end_shape_index':1,'length':.017}]}
full={'edges':[{'begin_shape_index':0,'end_shape_index':2,'length':1}]}
calls=[]
def call(method,body):
    calls.append(body['shape_match'])
    return partial if body['shape_match']=='edge_walk' else full
engine._call=call
assert engine.route_trace_edges(leg,'motorcycle')==full['edges']
assert calls==['edge_walk','map_snap']
for invalid in (partial,{'edges':[{'begin_shape_index':0,'end_shape_index':2,'length':.017}]}, {'edges':[]}):
    engine._call=lambda *args:invalid
    try:engine.route_trace_edges(leg,'motorcycle')
    except ValueError:pass
    else:raise AssertionError('partial/length-mismatching trace accepted')

assert explicit_osm_maxspeed({'maxspeed':'50'})=={'raw':'50','kph':50.0,'source':'osm:maxspeed'}
mph=explicit_osm_maxspeed({'maxspeed':'30 mph'});assert mph and abs(mph['kph']-48.28032)<1e-6
assert explicit_osm_maxspeed({'maxspeed':'signals'}) is None
assert explicit_osm_maxspeed({'highway':'primary'}) is None
speed_line=[[103.0,15.0],[103.001,15.0],[103.002,15.0]]
speed_edges=[
    {'way_id':101,'_route_begin_shape_index':0,'_route_end_shape_index':1},
    {'way_id':202,'_route_begin_shape_index':1,'_route_end_shape_index':2}
]
intervals=route_maxspeed_intervals(speed_line,speed_edges,{
    101:{'maxspeed':'50','highway':'primary'},
    202:{'highway':'primary'}
})
assert len(intervals)==1
assert intervals[0]['source']=='osm:maxspeed' and intervals[0]['maxspeed_kph']==50.0 and intervals[0]['osm_way_id']==101
assert intervals[0]['start_geometry_m']==0 and intervals[0]['end_geometry_m']>100
print('trace safety, source cardinality and explicit maxspeed provenance: 19 checks passed (isolated unit fixtures)')
