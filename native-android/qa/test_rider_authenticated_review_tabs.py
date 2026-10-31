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

    def test_earnings_period_controls_alone_cannot_pass(self):
        controls = [node(x) for x in ("วันนี้", "7 วัน", "30 วัน")]
        for label in ("7 วัน", "30 วัน"):
            with self.subTest(label=label):
                self.assertFalse(review.rider_earnings_period_ready(controls, label))

    def test_earnings_period_rejects_stale_previous_ledger(self):
        loaded = [node(x) for x in (
            "รายได้จากค่าส่ง", "งานสำเร็จ", "บัญชีเงินสด",
            "จ่ายร้าน", "เก็บลูกค้า", "วันนี้", "7 วัน", "30 วัน"
        )]
        # The selected button exists, but the cash-ledger header is still
        # from the old period; the new period appears only once.
        self.assertFalse(review.rider_earnings_period_ready(loaded, "7 วัน"))
        self.assertFalse(review.rider_earnings_period_ready(loaded, "30 วัน"))

    def test_earnings_period_requires_both_loaded_finance_sections(self):
        required = ["รายได้จากค่าส่ง", "งานสำเร็จ", "บัญชีเงินสด",
                    "จ่ายร้าน", "เก็บลูกค้า"]
        for label in ("7 วัน", "30 วัน"):
            ready = [node(x) for x in required + [label, label]]
            with self.subTest(label=label):
                self.assertTrue(review.rider_earnings_period_ready(ready, label))
                self.assertFalse(review.rider_earnings_period_ready(ready[:-3], label))
                self.assertFalse(review.rider_earnings_period_ready(
                    ready + [node("โหลดบัญชีเงินสดไม่สำเร็จ: เชื่อมต่อไม่ได้")], label
                ))

    def test_earnings_period_rejects_invisible_header(self):
        ready = [node(x) for x in (
            "รายได้จากค่าส่ง", "งานสำเร็จ", "บัญชีเงินสด",
            "จ่ายร้าน", "เก็บลูกค้า", "7 วัน"
        )]
        ready.append(node("7 วัน", "[0,0][0,0]"))
        self.assertFalse(review.rider_earnings_period_ready(ready, "7 วัน"))

    def test_earnings_capture_waits_before_screenshot(self):
        review.screens = []
        operations = []
        loaded = [node(x) for x in (
            "รายได้จากค่าส่ง", "งานสำเร็จ", "บัญชีเงินสด",
            "จ่ายร้าน", "เก็บลูกค้า", "7 วัน", "7 วัน"
        )]
        def fake_wait(predicate, seconds):
            operations.append("wait")
            self.assertEqual(seconds, 60)
            self.assertFalse(predicate([node("7 วัน")]))
            self.assertTrue(predicate(loaded))
            return loaded
        with (
            patch.object(review, "nodes", return_value=[node("7 วัน")]),
            patch.object(review, "tap_label", side_effect=lambda *args: operations.append("tap")),
            patch.object(review, "wait", side_effect=fake_wait),
            patch.object(review.time, "sleep"),
            patch.object(review, "shot", side_effect=lambda name: (
                operations.append("shot") or {"file": name + ".png"}
            )),
        ):
            review.capture_rider_earnings_period("7 วัน", "05-earnings-7-days")
        self.assertEqual(operations, ["tap", "wait", "shot"])
        self.assertEqual(review.screens[0]["file"], "05-earnings-7-days.png")

    def test_earnings_capture_timeout_never_saves_image(self):
        review.screens = []
        with (
            patch.object(review, "nodes", return_value=[node("30 วัน")]),
            patch.object(review, "tap_label"),
            patch.object(review, "wait", return_value=[]),
            patch.object(review.time, "sleep"),
            patch.object(review, "shot") as screenshot,
        ):
            with self.assertRaisesRegex(AssertionError, "did not finish loading"):
                review.capture_rider_earnings_period("30 วัน", "06-earnings-30-days")
        screenshot.assert_not_called()
        self.assertEqual(review.screens, [])

    def test_unknown_earnings_period_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "Unknown Rider earnings period"):
            review.rider_earnings_period_ready([node("วันนี้")], "วันนี้")

    def test_unknown_tab_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "Unknown Rider tab"):
            review.rider_tab_ready([node("home")], "home")


if __name__ == "__main__":
    unittest.main()
