"""Authenticated Merchant visual QA must prove tab content, not just bottom nav."""
import importlib.util
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

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
