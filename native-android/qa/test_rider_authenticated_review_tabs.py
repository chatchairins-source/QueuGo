"""Rider authenticated screenshots must show the selected page, not stale nav."""
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
    "queuego_rider_tab_review", QA / "capture-rider-authenticated-review.py"
)
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)


def node(text, bounds="[0,0][300,300]"):
    return {"text": text, "bounds": bounds}


class RiderTabVisualGateTest(unittest.TestCase):
    def test_bottom_navigation_alone_cannot_pass(self):
        nav = [node(x) for x in ("หน้าแรก", "ข้อความ", "รายได้", "โปรไฟล์")]
        for label in ("ข้อความ", "รายได้", "โปรไฟล์"):
            with self.subTest(label=label):
                self.assertFalse(review.rider_tab_ready(nav, label))

    def test_messages_accepts_empty_inbox_or_real_conversation(self):
        self.assertTrue(review.rider_tab_ready([node("ยังไม่มีการสนทนา")], "ข้อความ"))
        self.assertTrue(review.rider_tab_ready([node("ออเดอร์ QG123")], "ข้อความ"))
        self.assertFalse(review.rider_tab_ready([node("โหลดข้อความไม่สำเร็จ")], "ข้อความ"))

    def test_earnings_requires_loaded_summary_not_just_period_controls(self):
        periods = [node(x) for x in ("วันนี้", "7 วัน", "30 วัน")]
        self.assertFalse(review.rider_tab_ready(periods, "รายได้"))
        self.assertTrue(review.rider_tab_ready(
            periods + [node("รายได้จากค่าส่ง")], "รายได้"
        ))

    def test_profile_requires_real_work_settings(self):
        self.assertTrue(review.rider_tab_ready([node("รูปแบบและประเภทงาน")], "โปรไฟล์"))
        self.assertFalse(review.rider_tab_ready(
            [node("รูปแบบและประเภทงาน", "[0,0][0,0]")], "โปรไฟล์"
        ))

    def test_capture_waits_for_real_page_before_screenshot(self):
        review.screens = []
        operations = []
        def fake_wait(predicate, seconds):
            operations.append("wait")
            self.assertEqual(seconds, 60)
            self.assertFalse(predicate([node("ข้อความ")]))
            self.assertTrue(predicate([node("ยังไม่มีการสนทนา")]))
            return [node("ยังไม่มีการสนทนา")]
        with (
            patch.object(review, "nodes", return_value=[node("ข้อความ")]),
            patch.object(review, "tap_label", side_effect=lambda *args: operations.append("tap")),
            patch.object(review, "wait", side_effect=fake_wait),
            patch.object(review.time, "sleep"),
            patch.object(review, "shot", side_effect=lambda name: (
                operations.append("shot") or {"file": name + ".png"}
            )),
        ):
            review.capture_rider_tab("ข้อความ", "02-messages-chat", "messages_chat")
        self.assertEqual(operations, ["tap", "wait", "shot"])
        self.assertEqual(review.screens[0]["file"], "02-messages-chat.png")

    def test_failed_tab_never_captures_screenshot(self):
        review.screens = []
        with (
            patch.object(review, "nodes", return_value=[node("รายได้")]),
            patch.object(review, "tap_label"),
            patch.object(review, "wait", return_value=[]),
            patch.object(review, "shot") as screenshot,
        ):
            with self.assertRaisesRegex(AssertionError, "tab content did not render"):
                review.capture_rider_tab("รายได้", "04-earnings-today", "earnings_today")
        screenshot.assert_not_called()
        self.assertEqual(review.screens, [])

    def test_unknown_tab_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "Unknown Rider tab"):
            review.rider_tab_ready([node("home")], "home")


if __name__ == "__main__":
    unittest.main()
