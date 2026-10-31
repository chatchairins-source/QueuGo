#!/usr/bin/env python3
"""Offline tests for the unattended QueueGo CI recovery policy."""
import contextlib
import io
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from ops.ci.queuego_autorecovery import classify, excerpt, process, trusted


class FakeGitHub:
    repo = "chatchairins-source/QueuGo"

    def __init__(self, log="AssertionError: breakage", attempt=1, result="failure",
                 head_current=True, fork=False, existing_issue=None):
        self.log = log
        self.writes = []
        self.existing_issue = existing_issue
        self.run = {
            "id": 42, "run_attempt": attempt, "name": "QueueGo Native Android Pilot",
            "status": "completed", "conclusion": result, "head_sha": "a" * 40,
            "head_branch": "work-native-sample",
            "head_repository": {"full_name": "other/fork" if fork else self.repo},
            "event": "pull_request", "html_url": "https://github.com/example/actions/runs/42",
        }
        self.head_current = head_current

    def get(self, path):
        if path == "/actions/runs/42":
            return self.run
        if path.startswith("/branches/"):
            return {"commit": {"sha": "a" * 40 if self.head_current else "b" * 40}}
        if "/jobs?" in path:
            return {"jobs": [{
                "id": 99, "name": "native-pilot", "conclusion": "failure",
            }]}
        if path.startswith("/issues?"):
            return [self.existing_issue] if self.existing_issue else []
        if path.endswith("/comments?per_page=100"):
            return []
        raise AssertionError("Unexpected GET: " + path)

    def call(self, method, path, body=None, text=False):
        if method == "GET" and path == "/actions/jobs/99/logs":
            return self.log
        raise AssertionError("Unexpected call: " + path)

    def issue(self, title):
        if self.existing_issue and self.existing_issue.get("title") == title:
            return self.existing_issue
        return None

    def post(self, path, body=None):
        self.writes.append((path, body))
        if path == "/issues":
            return {"number": 12, "body": body["body"]}
        return {}

    def patch(self, path, body):
        self.writes.append((path, body))
        return {}


class RecoveryTest(unittest.TestCase):
    def run_case(self, gh, dry=False):
        with contextlib.redirect_stdout(io.StringIO()):
            process(gh, 42, dry)
        return [path for path, _ in gh.writes]

    def test_transient_classifier(self):
        self.assertEqual(classify("HTTP 503 Service Unavailable"), "transient")
        self.assertEqual(classify("connection reset by peer"), "transient")

    def test_deterministic_failure_must_override_transient(self):
        self.assertEqual(classify("HTTP 503\nAssertionError: broken invariant"), "code-or-policy")
        self.assertEqual(classify("unresolved reference: Foo"), "code-or-policy")

    def test_unknown_failure_fails_closed(self):
        self.assertEqual(classify("Something unexpected"), "unknown")

    def test_sanitized_excerpt(self):
        sample = "error: Authorization: Bearer github_pat_longlongabc\nerror: https://host/?token=secret"
        result = excerpt(sample)
        self.assertNotIn("github_pat_longlongabc", result)
        self.assertNotIn("token=secret", result)
        self.assertIn("[REDACTED]", result)

    def test_rejects_fork(self):
        gh = FakeGitHub(fork=True, log="HTTP 503")
        self.assertFalse(trusted(gh.run, gh.repo))
        self.assertEqual(self.run_case(gh), [])

    def test_stale_branch_is_not_retried(self):
        gh = FakeGitHub(log="HTTP 503", head_current=False)
        self.assertEqual(self.run_case(gh), [])

    def test_only_transient_attempt_one_retries(self):
        gh = FakeGitHub(log="HTTP 503")
        paths = self.run_case(gh)
        self.assertIn("/issues", paths)
        self.assertIn("/actions/runs/42/rerun-failed-jobs", paths)

    def test_second_attempt_only_triages(self):
        gh = FakeGitHub(log="HTTP 503", attempt=2)
        self.assertIn("/issues", self.run_case(gh))
        self.assertNotIn("/actions/runs/42/rerun-failed-jobs",
                         [p for p, _ in gh.writes])

    def test_code_regression_never_retries(self):
        gh = FakeGitHub()
        self.assertIn("/issues", self.run_case(gh))
        self.assertNotIn("/actions/runs/42/rerun-failed-jobs",
                         [p for p, _ in gh.writes])

    def test_dry_run_has_no_side_effects(self):
        gh = FakeGitHub(log="HTTP 503")
        self.assertEqual(self.run_case(gh, dry=True), [])

    def test_success_closes_matching_issue(self):
        issue = {"number": 11, "title": "[QueueGo CI Recovery] QueueGo Native Android Pilot @ aaaaaaaaaa", "body": ""}
        gh = FakeGitHub(result="success", existing_issue=issue)
        paths = self.run_case(gh)
        self.assertIn("/issues/11", paths)
        self.assertNotIn("/actions/runs/42/rerun-failed-jobs", paths)


if __name__ == "__main__":
    unittest.main()
