"""Regression tests for authenticated Rider diagnostic screenshot acceptance."""
from pathlib import Path
import sys
import unittest
import xml.etree.ElementTree as ET

QA = Path(__file__).resolve().parent
ROOT = QA.parent.parent
sys.path.insert(0, str(QA))
from rider_diagnostics_visual_gate import REQUIRED, OPTIONAL, diagnostics_visible, visible_texts


def node(text, bounds="[0,0][100,40]"):
    return ET.Element("node", {"text": text, "bounds": bounds})


def accepted_nodes(count="3"):
    return [node(label) for label in sorted(REQUIRED)] + [
        node(OPTIONAL), node("รายการแนะนำ"),
        node(f"ผ่าน {count}/4 รายการหลัก"),
    ]


class RiderDiagnosticsVisualGateTest(unittest.TestCase):
    def test_real_visible_four_core_optional_maps_and_partial_status(self):
        self.assertTrue(diagnostics_visible(accepted_nodes("3")))
        self.assertTrue(diagnostics_visible(accepted_nodes("4")))
        self.assertTrue(diagnostics_visible(accepted_nodes("0")))

    def test_missing_any_core_check_fails(self):
        for required in REQUIRED:
            with self.subTest(required=required):
                nodes = [n for n in accepted_nodes() if n.get("text") != required]
                self.assertFalse(diagnostics_visible(nodes))

    def test_google_maps_must_be_visibly_marked_optional(self):
        nodes = [n for n in accepted_nodes() if n.get("text") != OPTIONAL]
        nodes.append(node("Google Maps"))
        self.assertFalse(diagnostics_visible(nodes))
        nodes = [n for n in accepted_nodes() if n.get("text") != "รายการแนะนำ"]
        self.assertFalse(diagnostics_visible(nodes))

    def test_old_five_item_summary_is_rejected(self):
        nodes = [n for n in accepted_nodes() if not n.get("text").startswith("ผ่าน ")]
        nodes.append(node("ผ่าน 5/5 รายการหลัก"))
        self.assertFalse(diagnostics_visible(nodes))

    def test_stale_or_non_visible_nodes_do_not_satisfy_gate(self):
        nodes = accepted_nodes()
        for n in nodes:
            if n.get("text") == OPTIONAL:
                n.set("bounds", "[0,0][0,0]")
        self.assertNotIn(OPTIONAL, visible_texts(nodes))
        self.assertFalse(diagnostics_visible(nodes))

    def test_workflow_and_capture_use_actual_authenticated_gate(self):
        workflow = (ROOT / ".github/workflows/rider-android-authenticated-review.yml").read_text()
        capture = (QA / "capture-rider-authenticated-review.py").read_text()
        branch = "work-rider-diagnostics-visual-gate-20261102"
        self.assertIn("      - " + branch, workflow)
        self.assertIn("github.ref_name == '" + branch + "'", workflow)
        self.assertIn('os.environ.get("GITHUB_REF_NAME") in (', workflow)
        self.assertIn("python3 native-android/qa/test_rider_diagnostics_visual_gate.py", workflow)
        self.assertIn("diagnostics_visible", capture)
        self.assertIn("Rider diagnostics summary/optional Maps not visible", capture)
        self.assertIn('screens.append({"screen":name,**shot(name)})', capture)


if __name__ == "__main__":
    unittest.main()
