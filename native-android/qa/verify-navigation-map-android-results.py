"""Fail closed on real Android instrumentation results, including installed speed-follow.

Android UTP can encode a JUnit assumption as <failure> rather than <skipped>.
Only the optional country-archive assumption may be treated as a skip when
this is NOT an offline-country run. Offline-required runs must execute it.
"""
from pathlib import Path
import sys
import tempfile
import xml.etree.ElementTree as ET

REQUIRED_CLASS = "com.queuego.ridernavigationmap.NavigationMapRuntimeTest"
REQUIRED_TEST = "installedMapLibreFollowCameraZoomRespondsToActualRiderSpeed"
COUNTRY_CLASS = "com.queuego.ridernavigationmap.InstalledThailandMapTest"
COUNTRY_TEST = "installedCountryArchivePaintsRoadsInMultipleRegions"
ASSUMPTION = "org.junit.AssumptionViolatedException"


def _detail(element: ET.Element) -> str:
    return " ".join(filter(None, (
        element.get("type"), element.get("message"), element.text
    ))).strip()


def verify_connected_results(directory: Path, offline_required: bool = False) -> int:
    reports = sorted(directory.rglob("TEST-*.xml")) if directory.is_dir() else []
    if not reports:
        raise AssertionError("No connected Android JUnit XML reports; green Gradle alone is insufficient")
    executed = 0
    required_found = False
    country_executed = False
    for report in reports:
        try:
            document = ET.parse(report).getroot()
        except ET.ParseError as error:
            raise AssertionError(f"Malformed connected Android report {report}: {error}") from error
        suites = [document] if document.tag == "testsuite" else list(document.iter("testsuite"))
        if not suites:
            raise AssertionError(f"Connected Android report contains no testsuite: {report}")
        for suite in suites:
            allowed_assumptions = 0
            errors = []
            for case in suite.findall("testcase"):
                classname = case.get("classname")
                name = case.get("name")
                is_country = classname == COUNTRY_CLASS and name == COUNTRY_TEST
                is_required = classname == REQUIRED_CLASS and name == REQUIRED_TEST
                failure = case.find("failure")
                error = case.find("error")
                if failure is not None or error is not None:
                    element = error if error is not None else failure
                    detail = _detail(element)
                    if (failure is not None and is_country and not offline_required
                            and ASSUMPTION in detail):
                        # JUnit's assumption is a non-execution, NOT a successful
                        # country-map rendering test. Never accept it offline.
                        allowed_assumptions += 1
                        continue
                    errors.append(f"{classname}.{name}: {detail[:1200]}")
                    continue
                if case.find("skipped") is not None:
                    if is_required or (is_country and offline_required):
                        errors.append(f"Required installed test skipped: {classname}.{name}")
                    continue
                executed += 1
                if is_required:
                    required_found = True
                if is_country:
                    country_executed = True
            if (errors or int(suite.get("errors", "0")) != 0
                    or int(suite.get("failures", "0")) != allowed_assumptions):
                raise AssertionError(
                    f"Connected Android suite failed: {report}; "
                    + ("; ".join(errors[:10]) if errors else
                       f"failure count mismatch; optional assumptions={allowed_assumptions}")
                )
    if executed < 5:
        raise AssertionError(f"Connected Android report has too few executed testcases: {executed}")
    if not required_found:
        raise AssertionError(f"Missing executed installed MapLibre camera test: {REQUIRED_TEST}")
    if offline_required and not country_executed:
        raise AssertionError("Offline-required country map test was not executed")
    return executed


def self_test() -> None:
    def xml(cases: str, failures: int = 0) -> str:
        return f'<testsuite tests="6" failures="{failures}" errors="0">{cases}</testsuite>'
    required = f'<testcase classname="{REQUIRED_CLASS}" name="{REQUIRED_TEST}"/>'
    other = f'<testcase classname="{REQUIRED_CLASS}" name="other"/>'
    country = f'<testcase classname="{COUNTRY_CLASS}" name="{COUNTRY_TEST}"/>'
    assumption = country.replace("/>", f'><failure type="{ASSUMPTION}" message="got false"/></testcase>')
    foreign_assumption = other.replace("/>", f'><failure type="{ASSUMPTION}"/></testcase>')
    failed = other.replace("/>", '><failure message="camera did not reach target"/></testcase>')
    cases = [
        ("valid", xml(required + other * 4), False, True),
        ("missing-speed", xml(other * 5), False, False),
        ("skipped-speed", xml(required.replace("/>", "><skipped/></testcase>") + other * 4), False, False),
        ("failed-suite", xml(required + other * 4, failures=1), False, False),
        ("failed-case", xml(required + failed + other * 3, failures=1), False, False),
        ("zero-tests", xml(""), False, False),
        ("malformed", "<testsuite", False, False),
        ("optional-country-assumption-online", xml(required + other * 4 + assumption, failures=1), False, True),
        ("country-assumption-offline-denied", xml(required + other * 4 + assumption, failures=1), True, False),
        ("country-success-offline", xml(required + other * 4 + country), True, True),
        ("country-missing-offline", xml(required + other * 4), True, False),
        ("foreign-assumption-denied", xml(required + other * 3 + foreign_assumption, failures=1), False, False),
        ("other-failure-not-hidden", xml(required + other * 3 + assumption + failed, failures=2), False, False),
    ]
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        for label, body, offline, should_pass in cases:
            report = root / "TEST-navigation.xml"
            report.write_text(body, encoding="utf-8")
            passed = True
            try:
                verify_connected_results(root, offline_required=offline)
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
    print(f"QUEUEGO_NAVIGATION_REPORT_VERIFIER_SELFTEST_PASS {len(cases) + 1}")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    elif len(sys.argv) in (3, 4) and sys.argv[1] == "--results":
        if len(sys.argv) == 4 and sys.argv[3] != "--offline-required":
            raise SystemExit("Unknown option")
        offline = len(sys.argv) == 4
        count = verify_connected_results(Path(sys.argv[2]), offline_required=offline)
        print(f"QUEUEGO_NAVIGATION_CONNECTED_XML_PASS tests={count} required={REQUIRED_TEST} offline={offline}")
    else:
        raise SystemExit("Usage: verify-navigation-map-android-results.py --self-test | --results DIRECTORY [--offline-required]")
