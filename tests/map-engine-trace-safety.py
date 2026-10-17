#!/usr/bin/env python3
"""Isolated unit fixtures: partial vendor traces must never certify a route."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'queuego-map-engine'))
from core import Engine,restriction_source_error

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
print('trace safety and source cardinality: 12 checks passed (isolated unit fixtures)')
