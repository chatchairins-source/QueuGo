"""Authenticated Merchant visual QA must prove tab content, not just bottom nav."""
import importlib.util
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
from types import SimpleNamespace

QA = Path(__file__).resolve().parent
sys.path.insert(0, str(QA))
os.environ.setdefault("ANDROID_HOME", "/tmp/android-sdk")
spec = importlib.util.spec_from_file_location(
    "queuego_merchant_tab_review", QA / "capture-merchant-authenticated-review.py"
)
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)

BOUNDS = "[0,0][300,300]"


def node(text="", desc="", bounds=BOUNDS):
    return {"text": text, "content-desc": desc, "bounds": bounds}


class MerchantPrimaryTabVisualGateTest(unittest.TestCase):
    def test_bottom_navigation_alone_never_passes(self):
        navigation = [node(x) for x in review.PRIMARY_TAB_MARKERS]
        for label in review.PRIMARY_TAB_MARKERS:
            with self.subTest(label=label):
                self.assertFalse(review.primary_tab_ready(navigation, label))

    def test_real_page_markers_accept_each_tab(self):
        for label, markers in review.PRIMARY_TAB_MARKERS.items():
            for marker in markers:
                with self.subTest(label=label, marker=marker):
                    self.assertTrue(review.primary_tab_ready([node(marker)], label))
                    self.assertFalse(review.primary_tab_ready(
                        [node(marker, bounds="[0,0][0,0]")], label
                    ))

    def test_merged_accessibility_description_is_supported(self):
        self.assertTrue(review.primary_tab_ready(
            [node(desc="Merchant page\nดูสถานะและจัดการออเดอร์ของร้าน")], "คำสั่งซื้อ"
        ))

    def test_wrong_tab_content_does_not_pass(self):
        self.assertFalse(review.primary_tab_ready(
            [node("สินค้าและเมนู"), node("โปรโมชั่น")], "คำสั่งซื้อ"
        ))

    def test_capture_waits_for_real_content_before_screenshot(self):
        screens = []
        order = []
        def fake_wait(predicate, seconds):
            order.append("wait")
            self.assertEqual(seconds, 45)
            self.assertFalse(predicate([node("คำสั่งซื้อ")]))
            self.assertTrue(predicate([node("ดูสถานะและจัดการออเดอร์ของร้าน")]))
            return [node("ดูสถานะและจัดการออเดอร์ของร้าน")]
        with patch.object(review, "nodes", return_value=[node("คำสั่งซื้อ")]), \
             patch.object(review, "tap_label", side_effect=lambda *args: order.append("tap")), \
             patch.object(review, "wait", side_effect=fake_wait), \
             patch.object(review.time, "sleep", return_value=None), \
             patch.object(review, "shot", side_effect=lambda name: (
                 order.append("shot") or {"file": name + ".png", "sha256": "test"}
             )):
            review.capture_primary_tab(screens, "คำสั่งซื้อ", "02-orders", "คำสั่งซื้อ")
        self.assertEqual(order, ["tap", "wait", "shot"])
        self.assertEqual(screens[0]["screen"], "คำสั่งซื้อ")
        self.assertEqual(screens[0]["file"], "02-orders.png")

    def test_merchant_swipes_follow_phone_override_and_tablet_physical_size(self):
        cases = (
            ("Physical size: 1080x2280\\nOverride size: 720x1280\\n", "up",
             ["shell", "input", "swipe", "360", "1024", "360", "384", "450"]),
            ("Physical size: 1080x2280\\nOverride size: 720x1280\\n", "down",
             ["shell", "input", "swipe", "360", "384", "360", "1024", "450"]),
            ("Physical size: 1600x2560\\n", "up",
             ["shell", "input", "swipe", "800", "2048", "800", "768", "450"]),
            ("Physical size: 1600x2560\\n", "down",
             ["shell", "input", "swipe", "800", "768", "800", "2048", "450"]),
        )
        for viewport, direction, expected in cases:
            with self.subTest(viewport=viewport, direction=direction):
                def fake_run(args, **kwargs):
                    if args == ["shell", "wm", "size"]:
                        return SimpleNamespace(stdout=viewport)
                    self.assertEqual(args, expected)
                    return SimpleNamespace(stdout="")
                with patch.object(review, "run", side_effect=fake_run) as adb:
                    review.swipe_vertical(direction)
                self.assertEqual(adb.call_count, 2)

    def test_merchant_swipe_fails_closed_on_invalid_size_and_direction(self):
        for value in ("", "Physical size: 0x0", "Override size: 50x100"):
            with self.subTest(viewport=value):
                with patch.object(review, "run", return_value=SimpleNamespace(stdout=value)) as adb:
                    with self.assertRaises(RuntimeError):
                        review.swipe_vertical("up")
                adb.assert_called_once()
        with patch.object(review, "run") as adb:
            with self.assertRaisesRegex(ValueError, "scroll direction"):
                review.swipe_vertical("sideways")
        adb.assert_not_called()

    def test_merchant_scroll_to_label_uses_viewport_helper(self):
        with (patch.object(review, "nodes", side_effect=[[], [node("หน้าร้าน POS")]]),
              patch.object(review, "swipe_vertical") as swipe):
            self.assertEqual(review.scroll_to_label("หน้าร้าน POS"), [node("หน้าร้าน POS")])
        swipe.assert_called_once_with("up")

    def test_merchant_scroll_to_label_stops_when_unavailable(self):
        with (patch.object(review, "nodes", return_value=[]),
              patch.object(review, "swipe_vertical") as swipe):
            with self.assertRaisesRegex(AssertionError, "control not visible"):
                review.scroll_to_label("หน้าร้าน POS")
        self.assertEqual(swipe.call_count, 7)

    def test_failed_navigation_never_writes_a_screenshot(self):
        screens = []
        with patch.object(review, "nodes", return_value=[node("โปรโมชั่น")]), \
             patch.object(review, "tap_label"), \
             patch.object(review, "wait", return_value=[]), \
             patch.object(review, "shot") as screenshot:
            with self.assertRaisesRegex(AssertionError, "tab content did not render"):
                review.capture_primary_tab(screens, "โปรโมชั่น", "tablet-promotions", "tablet-promotions")
        screenshot.assert_not_called()
        self.assertEqual(screens, [])


if __name__ == "__main__":
    unittest.main()
