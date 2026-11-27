"""Regression tests for exact-source Rider artifact transport retries."""
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from gh_api_retry import github_api


def completed(code=0, out=b"ok", err=b""):
    return subprocess.CompletedProcess(["gh", "api", "fixture"], code, out, err)


class GithubApiRetryTests(unittest.TestCase):
    def test_success_returns_original_api_bytes(self):
        with patch("gh_api_retry.subprocess.run", return_value=completed(out=b'{"ok":true}')) as run:
            self.assertEqual(github_api("repos/example/actions/runs"), b'{"ok":true}')
            self.assertEqual(run.call_count, 1)

    def test_network_timeout_retries_then_recovers(self):
        with patch("gh_api_retry.subprocess.run", side_effect=[
            completed(1, b"", b"dial tcp: i/o timeout"), completed(out=b'{"ok":true}')
        ]) as run, patch("gh_api_retry.time.sleep") as sleep:
            self.assertEqual(github_api("repos/example/actions/runs"), b'{"ok":true}')
            self.assertEqual(run.call_count, 2)
            sleep.assert_called_once_with(3)

    def test_process_timeout_retries_then_recovers(self):
        with patch("gh_api_retry.subprocess.run", side_effect=[
            subprocess.TimeoutExpired(["gh", "api"], 120), completed(out=b"recovered")
        ]) as run, patch("gh_api_retry.time.sleep"):
            self.assertEqual(github_api("repos/example/actions/runs"), b"recovered")
            self.assertEqual(run.call_count, 2)

    def test_auth_failure_never_retries(self):
        with patch("gh_api_retry.subprocess.run", return_value=completed(1, b"", b"HTTP 403 Forbidden")) as run, patch("gh_api_retry.time.sleep") as sleep:
            with self.assertRaisesRegex(RuntimeError, "refusing unsafe retry"):
                github_api("repos/example/actions/runs")
            self.assertEqual(run.call_count, 1)
            sleep.assert_not_called()

    def test_transient_exhaustion_fails_closed(self):
        with patch("gh_api_retry.subprocess.run", return_value=completed(1, b"", b"HTTP 503 Service Unavailable")) as run, patch("gh_api_retry.time.sleep") as sleep:
            with self.assertRaisesRegex(RuntimeError, "after 4 attempts"):
                github_api("repos/example/actions/runs")
            self.assertEqual(run.call_count, 4)
            self.assertEqual(sleep.call_count, 3)

    def test_partial_zip_is_truncated_on_retry(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "candidate.zip"
            def simulate(_cmd, **kwargs):
                kwargs["stdout"].write(b"broken" if simulate.calls == 0 else b"validated")
                simulate.calls += 1
                return completed(1, b"", b"i/o timeout") if simulate.calls == 1 else completed()
            simulate.calls = 0
            with patch("gh_api_retry.subprocess.run", side_effect=simulate), patch("gh_api_retry.time.sleep"):
                self.assertEqual(github_api("repos/example/actions/artifacts/123/zip", output_file=target), b"")
            self.assertEqual(target.read_bytes(), b"validated")
            self.assertEqual(simulate.calls, 2)


if __name__ == "__main__":
    unittest.main()
