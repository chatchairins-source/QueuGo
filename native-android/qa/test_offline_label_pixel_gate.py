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
        self.assertIn('PropertyFactory.textFont(arrayOf("Sans Regular"))', style)
        self.assertIn('PropertyFactory.textField(Expression.get("name"))', style)
        self.assertIn("visibleLabelPixelDelta >= kotlin.math.max(40, hiddenFrameJitter * 3 + 20)", self.source)
        self.assertNotIn("drawText(", style)

    def test_hidden_frame_and_diagnostic_diff_are_retained_from_actual_emulator(self):
        # The accepted 156 sampled changed pixels on run 41324372691 did
        # not make Bangkok road names visually legible. Retain A/B evidence.
        for token in (
            '"bangkok-labels-hidden.png"',
            '"bangkok-labels-diff-diagnostic.png"',
            "val a = hiddenB.getPixel(x, y)",
            "val b = visible.getPixel(x, y)",
            "diagnostic_only=true",
            "labelDiagnosticFiles.forEach { diagnostic ->",
            "Retained label diagnostic size differs",
        ):
            self.assertIn(token, self.source)
        self.assertNotIn("drawText(", self.source)
        self.assertNotIn("Canvas(", self.source)

    def test_installed_glyph_bytes_and_real_source_names_are_diagnosed(self):
        # A positive symbol index cannot tell whether glyph ranges or names
        # are actually available to the installed renderer.
        for token in (
            'val glyphRanges = listOf("0-255.pbf", "3584-3839.pbf")',
            'context.assets.open(assetPath)',
            'installed_glyph_ranges=',
            'map.queryRenderedFeatures(viewport, "queuego-label-$layer")',
            'feature.getStringProperty("name")',
            'bangkok_source_names=',
        ):
            self.assertIn(token, self.source)
        self.assertIn("visibleLabelPixelDelta >= kotlin.math.max", self.source)
        self.assertNotIn("drawText(", self.source)

    def test_embedded_fontstack_matches_runtime_style_and_assets(self):
        # Real pinned PBF bytes declare "Sans Regular" in Fontstack.name.
        # Test every shipped range, including Thai, and the exact MapLibre
        # fontstack/asset path. A directory alias alone is not a visual PASS.
        font_dir = ROOT / "native-android/rider-maplibre-osm/src/main/assets/fonts/Sans Regular"
        for name in ("0-255.pbf", "256-511.pbf", "512-767.pbf", "3584-3839.pbf", "8192-8447.pbf"):
            data = (font_dir / name).read_bytes()
            self.assertIn(b"\x0a\x0cSans Regular", data[:32], name)
        style = (ROOT / "native-android/rider-maplibre-osm/src/main/java/com/queuego/ridermaplibreosm/ShortbreadVillageStyle.kt").read_text(encoding="utf-8")
        self.assertIn('PropertyFactory.textFont(arrayOf("Sans Regular"))', style)
        self.assertIn('asset://fonts/{fontstack}/{range}.pbf', style)
        self.assertIn('fonts/Sans Regular/$range', self.source)

    def test_installed_maplibre_style_glyph_template_and_layout_are_audited(self):
        # The PBF file and a source-side glyph URL alone cannot prove the
        # installed renderer received that URL or symbol text layout.
        self.assertIn('org.json.JSONObject(runtimeStyle.json)', self.source)
        self.assertIn('runtime_glyph_template=', self.source)
        self.assertIn('runtime_json_layer_count=', self.source)
        self.assertIn('runtimeStyle.getLayer(layerId)', self.source)
        self.assertIn('Live MapLibre style lost $layerId', self.source)
        self.assertIn('runtime_live_symbol_$sourceLayer=', self.source)
        self.assertIn('runtime_json_symbol_$sourceLayer=', self.source)
        self.assertIn('not_serialized_use_live_layer', self.source)
        self.assertIn('Installed MapLibre style lost its offline glyph template', self.source)
        self.assertIn('Missing live text-field on $layerId', self.source)
        self.assertIn('Missing live text-font on $layerId', self.source)
        # Style.json is not the authority for dynamically installed SymbolLayers.
        self.assertNotIn('assertNotNull("Installed MapLibre style lost $layerId", layer)', self.source)
        self.assertIn('visibleLabelPixelDelta >= kotlin.math.max', self.source)

    def test_country_workflow_tests_exact_pr_head(self):
        self.assertIn("work-native-rider-nav-unified-offline-20261030", self.workflow)
        self.assertIn("github.event.pull_request.head.sha || github.sha", self.workflow)
        self.assertIn("QG_NAV_RUNTIME_OFFLINE: '1'", self.workflow)
        self.assertIn("queuego-installed-country-runtime", self.workflow)


if __name__ == "__main__":
    unittest.main()
