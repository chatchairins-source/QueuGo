#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest
from shapely.geometry import Polygon

spec=importlib.util.spec_from_file_location('cross',Path(__file__).resolve().parents[1]/'scripts'/'audit-osm-hdx-quarantine-geometry.py')
cross=importlib.util.module_from_spec(spec); spec.loader.exec_module(cross)

class CrossSourceGeometry(unittest.TestCase):
    def test_relation_classes(self):
        a=Polygon([(0,0),(4,0),(4,4),(0,4)])
        equal=Polygon([(0,0),(4,0),(4,4),(0,4)])
        inside=Polygon([(1,1),(3,1),(3,3),(1,3)])
        overlap=Polygon([(3,3),(5,3),(5,5),(3,5)])
        touch=Polygon([(4,4),(5,4),(5,5),(4,5)])
        disjoint=Polygon([(6,6),(7,6),(7,7),(6,7)])
        self.assertEqual(cross.relation(a,equal)['classification'],'topologically_equal')
        self.assertEqual(cross.relation(a,inside)['classification'],'osm_covers_hdx')
        self.assertEqual(cross.relation(inside,a)['classification'],'hdx_covers_osm')
        self.assertEqual(cross.relation(a,overlap)['classification'],'overlap_non_containing')
        self.assertEqual(cross.relation(a,touch)['classification'],'touch_only')
        self.assertEqual(cross.relation(a,disjoint)['classification'],'disjoint')

    def test_parent_sources_stay_independent(self):
        osm_parent=Polygon([(0,0),(3,0),(3,3),(0,3)])
        osm_child=Polygon([(2,2),(4,2),(4,4),(2,4)])
        hdx_parent=Polygon([(0,0),(5,0),(5,5),(0,5)])
        hdx_child=Polygon([(2,2),(4,2),(4,4),(2,4)])
        result=cross.source_parent_evidence(osm_child,osm_parent,hdx_child,hdx_parent)
        self.assertFalse(result['osm_parent_covers_osm_child'])
        self.assertTrue(result['hdx_parent_covers_hdx_child'])
        self.assertTrue(result['hdx_parent_covers_osm_child'])
        self.assertFalse(result['osm_parent_covers_hdx_child'])
        self.assertEqual(result['parent_cross_source_relation']['classification'],'hdx_covers_osm')

    def test_build_rows_never_promotes(self):
        p=Polygon([(0,0),(4,0),(4,4),(0,4)])
        d=Polygon([(1,1),(5,1),(5,3),(1,3)])
        hdp=Polygon([(0,0),(6,0),(6,6),(0,6)])
        hdd=Polygon([(1,1),(3,1),(3,3),(1,3)])
        old=cross.EXCLUDED
        try:
            cross.EXCLUDED={'1001'}
            rows={'10':{'oad_id':'10','admin_type':'province','parent_id':None,'status':'deterministic','osm_source_record_id':'p'},
              '1001':{'oad_id':'1001','admin_type':'district','parent_id':'10','status':'deterministic','osm_source_record_id':'d'}}
            osm={'p':{'geometry':p,'admin_type':'province'},'d':{'geometry':d,'admin_type':'district'}}
            hdx={'10':{'pcode':'TH10','geometry':hdp},'1001':{'pcode':'TH1001','geometry':hdd}}
            failures={'1001':{'oad_id':'1001','admin_type':'district','status':'outside_parent'}}
            result=cross.build_rows(rows,osm,hdx,failures)[0]
            self.assertEqual(result['status'],'quarantined')
            self.assertFalse(result['eligible'])
            self.assertFalse(result['geometry_override_allowed'])
            self.assertFalse(result['canonical_geometry_approved'])
            self.assertFalse(result['source_parent_evidence']['osm_parent_covers_osm_child'])
            self.assertTrue(result['source_parent_evidence']['hdx_parent_covers_hdx_child'])
        finally:
            cross.EXCLUDED=old

if __name__=='__main__': unittest.main()
