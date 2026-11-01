"""Rider authenticated screenshots must show the selected page, not stale nav."""
import hashlib
import importlib.util
import os
from pathlib import Path
import sys
import unittest
from types import SimpleNamespace
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

    def test_earnings_summary_requires_loaded_cards_not_controls(self):
        controls = [node(x) for x in ("วันนี้", "7 วัน", "30 วัน")]
        self.assertFalse(review.rider_earnings_summary_ready(controls))
        loaded = controls + [node("รายได้จากค่าส่ง"), node("งานสำเร็จ")]
        self.assertTrue(review.rider_earnings_summary_ready(loaded))
        self.assertFalse(review.rider_earnings_summary_ready(
            loaded + [node("โหลดสรุปงานไม่สำเร็จ: network")]
        ))

    def ledger_nodes(self, label):
        return [
            node("บัญชีเงินสด", "[50,500][300,550]"),
            node(label, "[700,500][900,550]"),
            node("จ่ายร้าน", "[50,650][200,700]"),
            node("เก็บลูกค้า", "[500,650][650,700]"),
        ]

    def test_earnings_ledger_requires_period_on_heading_row(self):
        for label in ("7 วัน", "30 วัน"):
            with self.subTest(label=label):
                loaded = self.ledger_nodes(label)
                self.assertTrue(review.rider_earnings_ledger_ready(loaded, label))
                self.assertFalse(review.rider_earnings_ledger_ready(
                    loaded[:-1], label
                ))
                self.assertFalse(review.rider_earnings_ledger_ready(
                    loaded + [node("โหลดบัญชีเงินสดไม่สำเร็จ: network")], label
                ))

    def test_earnings_ledger_rejects_stale_period_button(self):
        stale = self.ledger_nodes("วันนี้") + [
            node("7 วัน", "[300,100][500,150]"),
            node("30 วัน", "[550,100][750,150]"),
        ]
        self.assertFalse(review.rider_earnings_ledger_ready(stale, "7 วัน"))
        self.assertFalse(review.rider_earnings_ledger_ready(stale, "30 วัน"))
        invisible = self.ledger_nodes("7 วัน")
        invisible[1] = node("7 วัน", "[0,0][0,0]")
        self.assertFalse(review.rider_earnings_ledger_ready(invisible, "7 วัน"))

    def test_scroll_earnings_until_finds_second_viewport(self):
        ledger = self.ledger_nodes("7 วัน")
        with (
            patch.object(review, "nodes", side_effect=[[], ledger]),
            patch.object(review, "swipe_vertical") as swipe,
            patch.object(review.time, "sleep"),
        ):
            found = review.scroll_earnings_until(
                lambda xs: review.rider_earnings_ledger_ready(xs, "7 วัน"), "up"
            )
        self.assertEqual(found, ledger)
        swipe.assert_called_once_with("up")

    def test_scroll_earnings_until_fails_closed(self):
        with (
            patch.object(review, "nodes", return_value=[]),
            patch.object(review, "swipe_vertical") as swipe,
            patch.object(review.time, "sleep"),
        ):
            self.assertEqual(review.scroll_earnings_until(lambda _: False, "down"), [])
        self.assertEqual(swipe.call_count, 8)
        with self.assertRaisesRegex(ValueError, "scroll direction"):
            review.scroll_earnings_until(lambda _: True, "sideways")

    def test_vertical_swipe_uses_actual_override_not_hardcoded_coordinates(self):
        for direction, expected in (
            ("up", ["shell", "input", "swipe", "360", "1024", "360", "384", "450"]),
            ("down", ["shell", "input", "swipe", "360", "384", "360", "1024", "450"]),
        ):
            with self.subTest(direction=direction):
                def fake_run(args, **kwargs):
                    if args == ["shell", "wm", "size"]:
                        return SimpleNamespace(
                            stdout="Physical size: 1080x2280\\nOverride size: 720x1280\\n"
                        )
                    self.assertEqual(args, expected)
                    return SimpleNamespace(stdout="")
                with patch.object(review, "run", side_effect=fake_run) as adb:
                    review.swipe_vertical(direction)
                self.assertEqual(adb.call_count, 2)

    def test_vertical_swipe_uses_physical_size_when_no_override(self):
        def fake_run(args, **kwargs):
            if args == ["shell", "wm", "size"]:
                return SimpleNamespace(stdout="Physical size: 1080x2280\\n")
            self.assertEqual(
                args, ["shell", "input", "swipe", "540", "1824", "540", "684", "450"]
            )
            return SimpleNamespace(stdout="")
        with patch.object(review, "run", side_effect=fake_run):
            review.swipe_vertical("up")

    def test_vertical_swipe_fails_closed_on_bad_viewport_or_direction(self):
        for output in ("", "Physical size: 0x0", "Override size: 50x100"):
            with self.subTest(output=output):
                with patch.object(review, "run", return_value=SimpleNamespace(stdout=output)) as adb:
                    with self.assertRaises(RuntimeError):
                        review.swipe_vertical("up")
                adb.assert_called_once()
        with patch.object(review, "run") as adb:
            with self.assertRaisesRegex(ValueError, "scroll direction"):
                review.swipe_vertical("sideways")
        adb.assert_not_called()

    def test_earnings_capture_validates_two_viewports_before_metadata(self):
        review.screens = []
        operations = []
        summary = [node(x) for x in (
            "วันนี้", "7 วัน", "30 วัน", "รายได้จากค่าส่ง", "งานสำเร็จ"
        )]
        ledger = self.ledger_nodes("7 วัน")
        def fake_wait(predicate, seconds):
            operations.append("summary-wait")
            self.assertEqual(seconds, 60)
            self.assertTrue(predicate(summary))
            return summary
        def fake_scroll(predicate, direction):
            operations.append("scroll-" + direction)
            selected = ledger if direction == "up" else summary
            self.assertTrue(predicate(selected))
            return selected
        with (
            patch.object(review, "nodes", return_value=[node("7 วัน")]),
            patch.object(review, "tap_label", side_effect=lambda *args: operations.append("tap")),
            patch.object(review, "wait", side_effect=fake_wait),
            patch.object(review, "scroll_earnings_until", side_effect=fake_scroll),
            patch.object(review.time, "sleep"),
            patch.object(review, "shot", side_effect=lambda name: (
                operations.append("shot-" + name) or {
                    "file": name + ".png",
                    "sha256": hashlib.sha256(name.encode("utf-8")).hexdigest(),
                }
            )),
        ):
            review.capture_rider_earnings_period("7 วัน", "05-earnings-7-days")
        self.assertEqual(operations, [
            "tap", "summary-wait", "scroll-up", "shot-05-earnings-7-days-ledger",
            "scroll-down", "shot-05-earnings-7-days"
        ])
        self.assertEqual([x["screen"] for x in review.screens], [
            "05-earnings-7-days", "05-earnings-7-days-ledger"
        ])

    def test_earnings_evidence_deduplicates_identical_pixels(self):
        # Screenshot names are not evidence of distinct visual content.
        summary = {"file": "summary.png", "sha256": "a" * 64}
        ledger = {"file": "ledger.png", "sha256": "a" * 64}
        entries, duplicate = review.earnings_evidence(
            "05-earnings-7-days", summary, ledger
        )
        self.assertTrue(duplicate)
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0]["screen"], "05-earnings-7-days")
        self.assertEqual(entries[0]["file"], "summary.png")
        self.assertTrue(entries[0]["cash_ledger_in_same_viewport"])

    def test_earnings_evidence_preserves_distinct_viewports(self):
        summary = {"file": "summary.png", "sha256": "a" * 64}
        ledger = {"file": "ledger.png", "sha256": "b" * 64}
        entries, duplicate = review.earnings_evidence(
            "06-earnings-30-days", summary, ledger
        )
        self.assertFalse(duplicate)
        self.assertEqual(
            [entry["screen"] for entry in entries],
            ["06-earnings-30-days", "06-earnings-30-days-ledger"],
        )
        self.assertEqual([entry["file"] for entry in entries],
                         ["summary.png", "ledger.png"])

    def test_earnings_capture_missing_ledger_never_accepts_image(self):
        review.screens = []
        with (
            patch.object(review, "nodes", return_value=[node("30 วัน")]),
            patch.object(review, "tap_label"),
            patch.object(review, "wait", return_value=[node("รายได้จากค่าส่ง")]),
            patch.object(review, "scroll_earnings_until", return_value=[]),
            patch.object(review.time, "sleep"),
            patch.object(review, "shot") as screenshot,
        ):
            with self.assertRaisesRegex(AssertionError, "cash ledger"):
                review.capture_rider_earnings_period("30 วัน", "06-earnings-30-days")
        screenshot.assert_not_called()
        self.assertEqual(review.screens, [])

    def test_earnings_capture_missing_return_summary_never_accepts(self):
        review.screens = []
        with (
            patch.object(review, "nodes", return_value=[node("7 วัน")]),
            patch.object(review, "tap_label"),
            patch.object(review, "wait", return_value=[node("รายได้จากค่าส่ง")]),
            patch.object(review, "scroll_earnings_until",
                         side_effect=[self.ledger_nodes("7 วัน"), []]),
            patch.object(review.time, "sleep"),
            patch.object(review, "shot", return_value={"file": "ledger.png"}) as screenshot,
        ):
            with self.assertRaisesRegex(AssertionError, "return to viewport"):
                review.capture_rider_earnings_period("7 วัน", "05-earnings-7-days")
        screenshot.assert_called_once()
        self.assertEqual(review.screens, [])

    def test_unknown_earnings_period_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "Unknown Rider earnings period"):
            review.rider_earnings_ledger_ready([node("วันนี้")], "วันนี้")
        with self.assertRaisesRegex(ValueError, "Unknown Rider earnings period"):
            review.capture_rider_earnings_period("วันนี้", "wrong")

    def test_unknown_tab_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "Unknown Rider tab"):
            review.rider_tab_ready([node("home")], "home")


if __name__ == "__main__":
    unittest.main()
