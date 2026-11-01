"""Guard real offline map label visibility acceptance against index-only passes.

This source test is a fast preflight. The Android instrumentation test remains
the authority for actual MapLibre frame pixels; no screenshot is fabricated.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
TEST = ROOT / "native-android/rider-navigation-map/src/androidTest/java/com/queuego/ridernavigationmap/InstalledThailandMapTest.kt"
WORKFLOW = ROOT / ".github/workflows/native-country-offline-runtime.yml"


class OfflineLabelPixelGateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = TEST.read_text(encoding="utf-8")
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_real_framebuffer_comparison_not_rendered_feature_index_alone(self):
        for token in (
            "changedMapPixels(first: Bitmap, second: Bitmap)",
            "instrumentation.uiAutomation.takeScreenshot()",
            "PropertyFactory.visibility(",
            "Property.NONE",
            "Property.VISIBLE",
            "hiddenFrameJitter = changedMapPixels(hiddenA, hiddenB)",
            "visibleLabelPixelDelta = changedMapPixels(hiddenB, visible)",
            "visibleLabelPixelDelta >= kotlin.math.max(40, hiddenFrameJitter * 3 + 20)",
        ):
            self.assertIn(token, self.source)

    def test_retained_screenshot_is_written_before_failing_visual_gate(self):
        persisted = self.source.index("resolver.openFileDescriptor(uri")
        assert_visible = self.source.index("visibleLabelPixelDelta >= kotlin.math.max")
        self.assertLess(persisted, assert_visible)
        self.assertIn("bangkok_visible_label_pixel_delta=", self.source)
        self.assertIn("Offline Bangkok glyphs are not visibly painted", self.source)
        self.assertNotIn("drawText(", self.source)
        self.assertNotIn("Canvas(", self.source)

    def test_street_collision_probe_is_narrow_and_keeps_real_pixel_gate(self):
        style = (ROOT / "native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/ShortbreadVillageStyle.kt").read_text(encoding="utf-8")
        self.assertIn('PropertyFactory.textAllowOverlap(layer == "street_labels")', style)
        self.assertIn('PropertyFactory.textFont(arrayOf("Noto Sans Regular"))', style)
        self.assertIn('PropertyFactory.textField(Expression.get("name"))', style)
        self.assertIn("visibleLabelPixelDelta >= kotlin.math.max(40, hiddenFrameJitter * 3 + 20)", self.source)
        self.assertNotIn("drawText(", style)

    def test_country_workflow_tests_exact_pr_head(self):
        self.assertIn("work-native-rider-nav-unified-offline-20261030", self.workflow)
        self.assertIn("github.event.pull_request.head.sha || github.sha", self.workflow)
        self.assertIn("QG_NAV_RUNTIME_OFFLINE: '1'", self.workflow)
        self.assertIn("queuego-installed-country-runtime", self.workflow)


if __name__ == "__main__":
    unittest.main()
