import importlib.util
from pathlib import Path
import unittest
import tempfile
from shapely.geometry import Polygon, Point
from shapely.prepared import prep
spec=importlib.util.spec_from_file_location('audit',Path(__file__).resolve().parents[1]/'scripts/audit-admin-boundary-topology.py')
audit=importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)

class GeometryGate(unittest.TestCase):
    def crosswalk_fixture(self, outside=False):
        def feature(identity, kind, geometry):
            return {'properties': {'source_record_id': identity, 'admin_type': kind}, 'geometry': geometry.__geo_interface__}
        province=Polygon([(0,0),(4,0),(4,4),(0,4)])
        district=Polygon([(1,1),(5 if outside else 3,1),(5 if outside else 3,3),(1,3)])
        child=Polygon([(1.1,1.1),(2,1.1),(2,2),(1.1,2)])
        features=[feature('p','province',province),feature('d','district',district),feature('s','subdistrict',child)]
        mapping=[{'oad_id':'10','admin_type':'province','parent_id':None,'status':'deterministic','osm_source_record_id':'p'},
                 {'oad_id':'1001','admin_type':'district','parent_id':'10','status':'deterministic','osm_source_record_id':'d'},
                 {'oad_id':'100101','admin_type':'subdistrict','parent_id':'1001','status':'deterministic','osm_source_record_id':'s'}]
        return features,mapping

    def test_full_child_polygon_and_parent_failure_cascade(self):
        features,mapping=self.crosswalk_fixture(outside=True)
        result=audit.audit_osm_crosswalk(features,mapping)
        self.assertEqual(result['summary']['district']['outside_parent'],1)
        self.assertEqual(result['summary']['subdistrict']['parent_rejected'],1)
        self.assertEqual(result['matched_parent_polygon_gate'],'blocked')
        self.assertNotIn('100101',result['parent_contained_child_oad_ids'])
        self.assertEqual(result['excluded_child_oad_ids'],['1001','100101'])
        self.assertFalse(result['production_import_allowed'])

    def test_crosswalk_full_containment(self):
        features,mapping=self.crosswalk_fixture()
        result=audit.audit_osm_crosswalk(features,list(reversed(mapping)))
        self.assertEqual(result['summary']['district']['covered'],1)
        self.assertEqual(result['summary']['subdistrict']['covered'],1)
        self.assertEqual(result['matched_parent_polygon_gate'],'passed')
        self.assertFalse(result['full_canonical_coverage'])

    def test_reverse_identity_collision_rejected(self):
        features,mapping=self.crosswalk_fixture()
        mapping.append(dict(mapping[1],oad_id='1002'))
        with self.assertRaisesRegex(ValueError,'reverse unique'):
            audit.audit_osm_crosswalk(features,mapping)

    def test_unpinned_source_rejected(self):
        with tempfile.NamedTemporaryFile() as stream:
            with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                audit.audit('osm',Path(stream.name))

    def test_valid_polygon(self):
        self.assertIsNone(audit.validity_reason(Polygon([(0,0),(2,0),(2,2),(0,2)])))
    def test_self_intersection_rejected(self):
        self.assertIn('Self-intersection',audit.validity_reason(Polygon([(0,0),(2,2),(2,0),(0,2)])))
    def test_empty_rejected(self):
        self.assertEqual(audit.validity_reason(Polygon()),'empty geometry')
    def test_point_rejected(self):
        self.assertEqual(audit.validity_reason(Point(0,0)),'polygon geometry required')
    def test_hole_and_parent_boundary(self):
        parent=prep(Polygon([(0,0),(4,0),(4,4),(0,4)],holes=[[(1,1),(2,1),(2,2),(1,2)]]))
        self.assertFalse(audit.contains_child(parent,Polygon([(1.1,1.1),(1.9,1.1),(1.9,1.9),(1.1,1.9)])))
        self.assertTrue(audit.contains_child(parent,Polygon([(0,0),(1,0),(1,1),(0,1)])))
        self.assertFalse(audit.contains_child(parent,Polygon([(-0.001,0),(1,0),(1,1),(0,1)])))

if __name__=='__main__': unittest.main()
