"""CLI-control and real loopback HTTP regressions, not Android acceptance."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import tempfile
import threading
import unittest
import subprocess
import sys

from gradle_maven_recovery import (
    MetadataError, execute, metadata_url, probe_metadata, retry_coordinates, validate_pom,
)

COORD = "com.google.code.findbugs:jsr305:2.0.2"
MISSING = "Could not find " + COORD + ".\n"
POM = (b"<project><groupId>com.google.code.findbugs</groupId>"
       b"<artifactId>jsr305</artifactId><version>2.0.2</version></project>")


class RecoveryTest(unittest.TestCase):
    def exercise(self, output, codes, probe_error=None, command=None):
        calls, probes, waits = [], [], []
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "first.log"

            def build(command, target):
                calls.append(command)
                target.write_text(output)
                return codes[len(calls) - 1]

            def probe(coordinate):
                probes.append(coordinate)
                if probe_error:
                    raise probe_error

            command = command or ["gradle", ":customer:assembleDebugAndroidTest", "--stacktrace"]
            code = execute(command, log, build=build, probe=probe, sleep=waits.append)
            self.assertEqual(log.read_text(), output, "original failure evidence retained")
        return code, calls, probes, waits

    def test_first_success_never_probes_or_retries(self):
        code, calls, probes, waits = self.exercise("BUILD SUCCESSFUL", [0])
        self.assertEqual((code, len(calls), probes, waits), (0, 1, [], []))

    def test_known_failure_retries_identical_tasks_once(self):
        code, calls, probes, waits = self.exercise(MISSING, [1, 0])
        self.assertEqual(code, 0)
        self.assertEqual(calls[1], calls[0] + ["--refresh-dependencies"])
        self.assertEqual(probes, [COORD])
        self.assertEqual(waits, [15])

    def test_second_failure_is_final(self):
        code, calls, _, _ = self.exercise(MISSING, [1, 7])
        self.assertEqual((code, len(calls)), (7, 2))

    def test_observed_offline_test_dependencies_keep_all_tasks(self):
        coordinates = ["com.squareup.okhttp3:mockwebserver:4.12.0",
                       "com.squareup.okhttp3:okhttp-tls:4.12.0"]
        command = ["gradle", "-p", "native-android", ":rider-navigation-map:testDebugUnitTest",
                   ":rider-maplibre-osm:testDebugUnitTest", ":rider:testDebugUnitTest",
                   ":rider:assembleDebug", ":rider:assembleDebugAndroidTest", "--no-daemon", "--stacktrace"]
        output = "\n".join("Could not find " + coordinate + "." for coordinate in coordinates)
        code, calls, probes, waits = self.exercise(output, [1, 0], command=command)
        self.assertEqual((code, probes, waits), (0, coordinates, [15]))
        self.assertEqual(calls, [command, command + ["--refresh-dependencies"]])
        self.assertEqual(retry_coordinates(output + "\nCould not find com.squareup.okhttp3:mockwebserver:4.13.0."), [])

    def test_offline_dependency_poms_require_exact_coordinates(self):
        for artifact in ("mockwebserver", "okhttp-tls"):
            coordinate = "com.squareup.okhttp3:" + artifact + ":4.12.0"
            pom = ("<project><groupId>com.squareup.okhttp3</groupId><artifactId>" + artifact
                   + "</artifactId><version>4.12.0</version></project>").encode()
            validate_pom(pom, coordinate)
            self.assertTrue(metadata_url(coordinate).endswith(artifact + "/4.12.0/" + artifact + "-4.12.0.pom"))
            with self.assertRaises(MetadataError):
                validate_pom(pom.replace(b"4.12.0", b"4.13.0"), coordinate)

    def test_observed_customer_transitive_coroutine_keeps_exact_tasks(self):
        coordinates = ["org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2",
                       "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0"]
        command = ["gradle", "-p", "native-android", ":customer:testDebugUnitTest",
                   ":customer:assembleDebug", ":customer:assembleDebugAndroidTest", "--no-daemon", "--stacktrace"]
        output = "\n".join("Could not resolve " + coordinate + "." for coordinate in coordinates)
        code, calls, probes, waits = self.exercise(output, [1, 0], command=command)
        self.assertEqual((code, probes, waits), (0, sorted(coordinates), [15]))
        self.assertEqual(calls, [command, command + ["--refresh-dependencies"]])
        self.assertEqual(retry_coordinates(output + "\nCould not resolve org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.1."), [])

    def test_observed_transitive_coroutine_pom_requires_exact_version(self):
        coordinate = "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0"
        pom = (b"<project><groupId>org.jetbrains.kotlinx</groupId>"
               b"<artifactId>kotlinx-coroutines-test</artifactId><version>1.9.0</version></project>")
        self.assertTrue(metadata_url(coordinate).endswith("kotlinx-coroutines-test/1.9.0/kotlinx-coroutines-test-1.9.0.pom"))
        validate_pom(pom, coordinate)
        with self.assertRaises(MetadataError):
            validate_pom(pom.replace(b"1.9.0", b"1.10.2"), coordinate)

    def test_metadata_failure_does_not_run_gradle_again(self):
        code, calls, _, _ = self.exercise(MISSING, [9], MetadataError("no metadata"))
        self.assertEqual((code, len(calls)), (9, 1))

    def test_compile_or_test_failure_is_never_retried(self):
        for reason in ("e: file:///Source.kt: broken", "There were failing tests", "AssertionError"):
            code, calls, probes, waits = self.exercise(MISSING + reason, [1])
            self.assertEqual((code, len(calls), probes, waits), (1, 1, [], []))

    def test_unknown_or_mixed_dependency_is_not_retried(self):
        for output in ("Could not find evil:module:9.0", MISSING + "Could not resolve evil:module:9.0"):
            code, calls, probes, _ = self.exercise(output, [1])
            self.assertEqual((code, len(calls), probes), (1, 1, []))

    def test_resolve_and_download_429_coordinates(self):
        text = ("Could not resolve io.github.rallista:valhalla-mobile:0.6.4.\n"
                "Could not download plugin.jar (org.jetbrains.kotlin.plugin.compose:"
                "org.jetbrains.kotlin.plugin.compose.gradle.plugin:2.4.10)\nReceived status code 429")
        self.assertEqual(len(retry_coordinates(text)), 2)

    def test_url_is_canonical_and_version_pinned(self):
        self.assertEqual(metadata_url(COORD), "https://repo.maven.apache.org/maven2/"
                         "com/google/code/findbugs/jsr305/2.0.2/jsr305-2.0.2.pom")
        with self.assertRaises(MetadataError):
            metadata_url("com.google.code.findbugs:jsr305:latest")

    def test_pom_identity_and_invalid_payload(self):
        validate_pom(POM, COORD)
        for data in (b"", b"<html>jsr305</html>", POM.replace(b"2.0.2", b"2.0.3"),
                     POM.replace(b"com.google.code.findbugs", b"other.group"),
                     POM.replace(b"jsr305", b"other"), b"<!DOCTYPE project>" + POM):
            with self.assertRaises(MetadataError):
                validate_pom(data, COORD)

    def test_namespaced_parent_identity(self):
        data = (b'<project xmlns="http://maven.apache.org/POM/4.0.0"><parent>'
                b'<groupId>com.google.code.findbugs</groupId><version>2.0.2</version>'
                b'</parent><artifactId>jsr305</artifactId></project>')
        validate_pom(data, COORD)

    def test_actual_cli_preserves_process_status_and_log(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "first.log"
            script = Path(__file__).with_name("gradle_maven_recovery.py")
            result = subprocess.run([sys.executable, str(script), "--log", str(target), "--",
                                     sys.executable, "-c", "print('There were failing tests');raise SystemExit(7)"],
                                    capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 7)
            self.assertEqual(target.read_text().strip(), "There were failing tests")
            self.assertFalse(target.with_suffix(".retry.log").exists())

    def test_actual_http_recovery_exhaustion_and_denial(self):
        counts, waits = {}, []

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                counts[self.path] = counts.get(self.path, 0) + 1
                code = (404 if self.path == "/recover" and counts[self.path] == 1
                        else 429 if self.path in ("/limited", "/long-limit")
                        else 403 if self.path == "/denied" else 200)
                self.send_response(code)
                self.send_header("Retry-After", "120" if self.path == "/long-limit" else "17")
                self.end_headers()
                self.wfile.write(POM)

            def log_message(self, *args):
                pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            origin = "http://127.0.0.1:" + str(server.server_port)
            probe_metadata(COORD, url=origin + "/recover", sleep=waits.append)
            self.assertEqual((counts["/recover"], waits), (2, [17]))
            waits.clear()
            with self.assertRaises(MetadataError):
                probe_metadata(COORD, url=origin + "/limited", sleep=waits.append)
            self.assertEqual((counts["/limited"], waits), (3, [17, 17]))
            waits.clear()
            with self.assertRaises(MetadataError):
                probe_metadata(COORD, url=origin + "/long-limit", sleep=waits.append)
            self.assertEqual((counts["/long-limit"], waits), (1, []))
            with self.assertRaises(MetadataError):
                probe_metadata(COORD, url=origin + "/denied", sleep=waits.append)
            self.assertEqual((counts["/denied"], waits), (1, []))
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == "__main__":
    unittest.main()
