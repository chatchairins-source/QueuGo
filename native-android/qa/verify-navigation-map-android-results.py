"""Fail closed unless real connected Android XML proves Rider speed-follow camera test.

This is a report verifier, not a substitute for installed MapLibre instrumentation.
"""
from pathlib import Path
import sys
import tempfile
import xml.etree.ElementTree as ET

REQUIRED_CLASS = "com.queuego.ridernavigationmap.NavigationMapRuntimeTest"
REQUIRED_TEST = "installedMapLibreFollowCameraZoomRespondsToActualRiderSpeed"


def verify_connected_results(directory: Path) -> int:
    reports = sorted(directory.rglob("TEST-*.xml")) if directory.is_dir() else []
    if not reports:
        raise AssertionError("No connected Android JUnit XML reports; green Gradle alone is insufficient")
    total = 0
    required_found = False
    for report in reports:
        try:
            document = ET.parse(report).getroot()
        except ET.ParseError as error:
            raise AssertionError(f"Malformed connected Android report {report}: {error}") from error
        suites = [document] if document.tag == "testsuite" else list(document.iter("testsuite"))
        if not suites:
            raise AssertionError(f"Connected Android report contains no testsuite: {report}")
        for suite in suites:
            if int(suite.get("failures", "0")) or int(suite.get("errors", "0")):
                raise AssertionError(f"Connected Android suite failed: {report}")
            for case in suite.findall("testcase"):
                total += 1
                if case.find("failure") is not None or case.find("error") is not None:
                    raise AssertionError(f"Connected Android test failed: {case.get('name')} in {report}")
                if (case.get("classname") == REQUIRED_CLASS
                        and case.get("name") == REQUIRED_TEST):
                    if case.find("skipped") is not None:
                        raise AssertionError("Required installed MapLibre speed-follow camera test was skipped")
                    required_found = True
    if total < 5:
        raise AssertionError(f"Connected Android report has too few testcases: {total}")
    if not required_found:
        raise AssertionError(f"Missing required installed MapLibre camera test: {REQUIRED_TEST}")
    return total


def self_test() -> None:
    def xml(cases: str, failures: int = 0) -> str:
        return f'<testsuite tests="5" failures="{failures}" errors="0">{cases}</testsuite>'
    required = f'<testcase classname="{REQUIRED_CLASS}" name="{REQUIRED_TEST}"/>'
    other = '<testcase classname="com.queuego.ridernavigationmap.NavigationMapRuntimeTest" name="other"/>'
    valid = xml(required + other * 4)
    cases = [
        ("valid", valid, True),
        ("missing-speed", xml(other * 5), False),
        ("skipped-speed", xml(required.replace("/>", "><skipped/></testcase>") + other * 4), False),
        ("failed-suite", xml(required + other * 4, failures=1), False),
        ("zero-tests", xml(""), False),
        ("malformed", "<testsuite", False),
    ]
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        for label, body, should_pass in cases:
            report = root / "TEST-navigation.xml"
            report.write_text(body, encoding="utf-8")
            passed = True
            try:
                verify_connected_results(root)
            except AssertionError:
                passed = False
            if passed != should_pass:
                raise AssertionError(f"Report verifier self-test failed: {label}")
            report.unlink()
        try:
            verify_connected_results(root)
        except AssertionError:
            pass
        else:
            raise AssertionError("Missing XML must fail closed")
    print("QUEUEGO_NAVIGATION_REPORT_VERIFIER_SELFTEST_PASS 7")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    elif len(sys.argv) == 3 and sys.argv[1] == "--results":
        count = verify_connected_results(Path(sys.argv[2]))
        print(f"QUEUEGO_NAVIGATION_CONNECTED_XML_PASS tests={count} required={REQUIRED_TEST}")
    else:
        raise SystemExit("Usage: verify-navigation-map-android-results.py --self-test | --results DIRECTORY")
