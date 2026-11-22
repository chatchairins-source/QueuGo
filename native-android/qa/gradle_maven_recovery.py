"""Run real Gradle tasks; recover once from known pinned Central metadata failures.

No mirror, version substitution, offline mode or test bypass. Cache policy in the
calling workflows reduces repeated downloads; this is a bounded last resort.
"""
import argparse
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import urlopen
import xml.etree.ElementTree as ET

PINNED = frozenset({
    "io.github.rallista:valhalla-mobile:0.6.4",
    "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2",
    "com.squareup:javawriter:2.1.1",
    "org.hamcrest:hamcrest-library:1.3",
    "org.hamcrest:hamcrest-integration:1.3",
    "com.google.code.findbugs:jsr305:2.0.2",
    "com.squareup.okhttp3:mockwebserver:4.12.0",
    "com.squareup.okhttp3:okhttp-tls:4.12.0",
    "org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:2.4.10",
})
COORD = r"([A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+-]+)"


class MetadataError(ValueError):
    pass


def retry_coordinates(log):
    # Mixed source/test failures must never be turned into a dependency retry.
    if re.search(r"^e:\s|Compilation (?:error|failed)|There were failing tests|AssertionError|FAILURES!!!",
                 log, re.MULTILINE | re.IGNORECASE):
        return []
    found = set(re.findall(r"Could not (?:find|resolve)\s+" + COORD, log))
    found.update(re.findall(r"Could not download[^\n]*\(" + COORD + r"\)", log))
    # Gradle terminates missing-module messages with sentence punctuation.
    found = {coordinate.rstrip(".") for coordinate in found}
    if not found or not found.issubset(PINNED):
        return []
    return sorted(found)


def metadata_url(coordinate):
    if coordinate not in PINNED:
        raise MetadataError("unapproved dependency")
    group, artifact, version = coordinate.split(":")
    return ("https://repo.maven.apache.org/maven2/" + group.replace(".", "/")
            + f"/{artifact}/{version}/{artifact}-{version}.pom")


def validate_pom(data, coordinate):
    if len(data) > 1024 * 1024 or b"<!DOCTYPE" in data or b"<!ENTITY" in data:
        raise MetadataError("invalid POM payload")
    try:
        root = ET.fromstring(data)
    except ET.ParseError as error:
        raise MetadataError("POM is not XML") from error
    namespace = root.tag.split("}")[0] + "}" if root.tag.startswith("{") else ""
    if root.tag != namespace + "project":
        raise MetadataError("POM is not a Maven project")
    group, artifact, version = coordinate.split(":")
    for name, expected in (("groupId", group), ("artifactId", artifact), ("version", version)):
        value = root.findtext(namespace + name)
        if value is None and name != "artifactId":
            value = root.findtext(namespace + "parent/" + namespace + name)
        if value is None or value.strip() != expected:
            raise MetadataError("POM identity mismatch: " + name)


def probe_metadata(coordinate, *, sleep=time.sleep, opener=urlopen, url=None):
    # `url` is an internal unit-test seam, never a CLI/env-controlled repository.
    target = metadata_url(coordinate) if url is None else url
    for attempt in range(3):
        try:
            with opener(target, timeout=20) as response:
                data = response.read(1024 * 1024 + 1)
            validate_pom(data, coordinate)
            return
        except HTTPError as error:
            # Explicit access denial is fatal; do not evade it through a mirror.
            if attempt == 2 or error.code not in (404, 408, 429, 500, 502, 503, 504):
                raise MetadataError(f"Central metadata unavailable: HTTP {error.code}") from error
            retry_after = error.headers.get("Retry-After", "") if error.headers else ""
            seconds = int(retry_after) if retry_after.isdigit() else 0
            if retry_after and not retry_after.isdigit():
                try:
                    seconds = (parsedate_to_datetime(retry_after) - datetime.now(timezone.utc)).total_seconds()
                except (TypeError, ValueError):
                    seconds = 0
            # A long server-directed cooldown is a blocker, not permission to
            # retry early or move to another hostname/egress path.
            if seconds > 30:
                raise MetadataError("Central requires a longer cooldown; stop recovery") from error
            delay = max(15, seconds)
            sleep(delay)
        except (URLError, TimeoutError) as error:
            if attempt == 2:
                raise MetadataError("Central metadata transport exhausted") from error
            sleep(15)


def run_build(command, log_path):
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("w", encoding="utf-8") as log:
        with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                              text=True, errors="replace") as process:
            for line in process.stdout:
                print(line, end="", flush=True)
                log.write(line)
            return process.wait()


def execute(command, log_path, *, build=run_build, probe=probe_metadata, sleep=time.sleep):
    first = build(command, log_path)
    if first == 0:
        return 0
    coordinates = retry_coordinates(log_path.read_text(encoding="utf-8", errors="replace"))
    if not coordinates:
        return first
    print("Known pinned Maven metadata failure; cooling down before one recovery attempt", flush=True)
    sleep(15)
    try:
        for coordinate in coordinates:
            probe(coordinate)
    except MetadataError as error:
        print("::error::" + str(error), file=sys.stderr)
        return first
    # This is the only build retry. Preserve both logs and the real exit status.
    return build(command + ["--refresh-dependencies"], log_path.with_suffix(".retry.log"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("a real Gradle command is required")
    return execute(command, args.log)


if __name__ == "__main__":
    sys.exit(main())
