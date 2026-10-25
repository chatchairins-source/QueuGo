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
