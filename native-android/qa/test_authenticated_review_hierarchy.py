"""Regression coverage: authenticated Rider screenshots must never reuse stale UI XML."""
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
    "queuego_rider_auth_review", QA / "capture-rider-authenticated-review.py"
)
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)

VALID = b'<hierarchy rotation="0"><node text="Rider Home" bounds="[0,0][200,200]"/></hierarchy>'


class AuthenticatedRiderHierarchyTest(unittest.TestCase):
    def exercise(self, dump_results, cat_results):
        dumps = iter(dump_results)
        cats = iter(cat_results)
        operations = []

        def fake_adb(args, **kwargs):
            operations.append(tuple(args))
            if args[:3] == ["shell", "rm", "-f"]:
                return SimpleNamespace(stdout=b"")
            if args[:2] == ["exec-out", "cat"]:
                value = next(cats)
                if isinstance(value, Exception):
                    raise value
                return SimpleNamespace(stdout=value)
            raise AssertionError("Unexpected ADB command: " + repr(args))

        def fake_dump(args, **kwargs):
            self.assertIn("capture_output", kwargs)
            self.assertTrue(kwargs["capture_output"])
            self.assertTrue(kwargs["text"])
            return next(dumps)

        with patch.object(review, "run", side_effect=fake_adb), \
             patch.object(review.subprocess, "run", side_effect=fake_dump), \
             patch.object(review.time, "sleep"):
            result = review.nodes()
        return result, operations

    def test_null_root_retries_without_reading_old_xml(self):
        result, operations = self.exercise(
            [subprocess.CompletedProcess([], 0, "null root", ""),
             subprocess.CompletedProcess([], 0, "dumped", "")],
            [VALID],
        )
        self.assertEqual(result[0].get("text"), "Rider Home")
        self.assertEqual(sum(op[:3] == ("shell", "rm", "-f") for op in operations), 2)
        self.assertEqual(sum(op[:2] == ("exec-out", "cat") for op in operations), 1)

    def test_missing_dump_file_retries_after_successful_dump_exit(self):
        result, operations = self.exercise(
            [subprocess.CompletedProcess([], 0, "", ""),
             subprocess.CompletedProcess([], 0, "", "")],
            [subprocess.CalledProcessError(1, ["adb", "cat"]), VALID],
        )
        self.assertEqual(len(result), 1)
        self.assertEqual(sum(op[:3] == ("shell", "rm", "-f") for op in operations), 2)

    def test_wrong_root_is_not_accepted_as_current_screen(self):
        result, operations = self.exercise(
            [subprocess.CompletedProcess([], 0, "", ""),
             subprocess.CompletedProcess([], 0, "", "")],
            [b"<stale><node text='Old Rider Home'/></stale>", VALID],
        )
        self.assertEqual(result[0].get("text"), "Rider Home")
        self.assertEqual(sum(op[:3] == ("shell", "rm", "-f") for op in operations), 2)

    def test_all_null_roots_raise_runtime_error_not_type_error(self):
        with patch.object(review, "run", return_value=SimpleNamespace(stdout=b"")), \
             patch.object(review.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, "null root", "")), \
             patch.object(review.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "hierarchy unavailable"):
                review.nodes()


if __name__ == "__main__":
    unittest.main()
