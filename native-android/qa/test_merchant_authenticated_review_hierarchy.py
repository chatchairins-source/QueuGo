"""Merchant authenticated QA must reject stale or malformed UIAutomator hierarchies."""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import unittest
from types import SimpleNamespace
from unittest.mock import patch

QA = Path(__file__).resolve().parent
sys.path.insert(0, str(QA))
os.environ.setdefault("ANDROID_HOME", "/tmp/android-sdk")
spec = importlib.util.spec_from_file_location(
    "queuego_merchant_auth_review", QA / "capture-merchant-authenticated-review.py"
)
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)

VALID = b'<hierarchy rotation="0"><node text="Merchant Home" bounds="[0,0][200,200]"/></hierarchy>'


class MerchantHierarchyFreshnessTest(unittest.TestCase):
    def exercise(self, dumps, cats):
        dump_iter = iter(dumps)
        cat_iter = iter(cats)
        operations = []

        def fake_adb(args, **kwargs):
            operations.append(tuple(args))
            if args[:3] == ["shell", "rm", "-f"]:
                return SimpleNamespace(stdout=b"")
            if args[:2] == ["exec-out", "cat"]:
                value = next(cat_iter)
                if isinstance(value, Exception):
                    raise value
                return SimpleNamespace(stdout=value)
            raise AssertionError("Unexpected ADB command: " + repr(args))

        def fake_dump(args, **kwargs):
            self.assertTrue(kwargs.get("capture_output"))
            self.assertTrue(kwargs.get("text"))
            return next(dump_iter)

        with patch.object(review, "run", side_effect=fake_adb), \
             patch.object(review.subprocess, "run", side_effect=fake_dump), \
             patch.object(review.time, "sleep"):
            result = review.nodes()
        return result, operations

    def test_null_root_discards_old_xml_and_retries(self):
        result, ops = self.exercise(
            [subprocess.CompletedProcess([], 0, "null root", ""),
             subprocess.CompletedProcess([], 0, "dumped", "")], [VALID])
        self.assertEqual(result[0].get("text"), "Merchant Home")
        self.assertEqual(sum(op[:3] == ("shell", "rm", "-f") for op in ops), 2)
        self.assertEqual(sum(op[:2] == ("exec-out", "cat") for op in ops), 1)

    def test_missing_xml_after_zero_exit_retries(self):
        result, ops = self.exercise(
            [subprocess.CompletedProcess([], 0, "", ""),
             subprocess.CompletedProcess([], 0, "", "")],
            [subprocess.CalledProcessError(1, ["adb", "cat"]), VALID])
        self.assertEqual(len(result), 1)
        self.assertEqual(sum(op[:3] == ("shell", "rm", "-f") for op in ops), 2)

    def test_wrong_root_never_accepted_as_screen(self):
        result, ops = self.exercise(
            [subprocess.CompletedProcess([], 0, "", ""),
             subprocess.CompletedProcess([], 0, "", "")],
            [b"<old><node text='Old Merchant Home'/></old>", VALID])
        self.assertEqual(result[0].get("text"), "Merchant Home")
        self.assertEqual(sum(op[:3] == ("shell", "rm", "-f") for op in ops), 2)

    def test_nonzero_dump_exit_retries_without_cat(self):
        result, ops = self.exercise(
            [subprocess.CompletedProcess([], 1, "", "dump failed"),
             subprocess.CompletedProcess([], 0, "", "")], [VALID])
        self.assertEqual(result[0].get("text"), "Merchant Home")
        self.assertEqual(sum(op[:2] == ("exec-out", "cat") for op in ops), 1)

    def test_exhausted_null_root_fails_closed(self):
        with patch.object(review, "run", return_value=SimpleNamespace(stdout=b"")), \
             patch.object(review.subprocess, "run",
                          return_value=subprocess.CompletedProcess([], 0, "null root", "")), \
             patch.object(review.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "hierarchy unavailable after retries"):
                review.nodes()


if __name__ == "__main__":
    unittest.main()
