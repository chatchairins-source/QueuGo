"""Fail-closed source contracts for real, secret-backed Android visual review CI.

No credentials are read here; this checks workflow eligibility and role isolation.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/rider-android-authenticated-review.yml"
CUSTOMER = ROOT / "native-android/qa/capture-customer-authenticated-review.py"
BRANCH = "work-native-ride-visual-capture-20261101"


class VisualReviewWorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.customer = CUSTOMER.read_text(encoding="utf-8")

    def assert_branch_in_python_gate(self, branch, start, end):
        # Match membership inside the intended gate, not the position of an
        # element in a growing tuple (new successors must not break CI).
        self.assertIn(start, self.workflow)
        section = self.workflow.split(start, 1)[1].split(end, 1)[0]
        self.assertIn('"' + branch + '"', section)

    def test_successor_branch_triggers_capture(self):
        self.assertIn("      - " + BRANCH, self.workflow)
        self.assertIn('GITHUB_REF_NAME") in (', self.workflow)
        self.assert_branch_in_python_gate(BRANCH, 'all_roles = (', 'ready = (')
        self.assert_branch_in_python_gate(BRANCH, 'capture_requested = (', 'report =')
        self.assertIn("github.ref_name == '" + BRANCH + "'", self.workflow)

    def test_rider_nav_clearance_branch_runs_real_capture(self):
        rider_branch = "work-rider-profile-nav-clearance-20261101"
        self.assertIn("      - " + rider_branch, self.workflow)
        self.assertIn('"' + rider_branch + '",', self.workflow)
        self.assertIn("github.ref_name == '" + rider_branch + "'", self.workflow)

    def test_visual_evidence_fix_branch_captures_real_installed_apks(self):
        branch = "work-native-visual-evidence-integrity-20261101"
        self.assertIn("      - " + branch, self.workflow)
        self.assertIn('"' + branch + '",', self.workflow)
        self.assertIn("github.ref_name == '" + branch + "'", self.workflow)

    def test_png_signature_fix_branch_runs_installed_screenshot_review(self):
        branch = "work-qa-true-png-signature-20261101"
        self.assertIn("      - " + branch, self.workflow)
        self.assert_branch_in_python_gate(branch, 'capture_requested = (', 'report =')
        self.assertIn("github.ref_name == '" + branch + "'", self.workflow)

    def test_customer_review_successor_is_eligible_without_rider_credentials(self):
        # Owner review must not be silently skipped when only the Customer
        # test secrets exist. Existing account-level concurrency stays intact.
        branch = "work-native-customer-review-eligibility-20261101"
        self.assertIn("      - " + branch, self.workflow)
        self.assert_branch_in_python_gate(branch, 'all_roles = (', 'ready = (')
        self.assert_branch_in_python_gate(branch, 'capture_requested = (', 'report =')
        self.assertIn("github.ref_name == '" + branch + "'", self.workflow)
        self.assertIn('any(role["ready"] for role in roles.values()) if all_roles',
                      self.workflow)
        customer = re.search(
            r"- name: Capture authenticated Customer views on Android Emulator\n\s+if: ([^\n]+)",
            self.workflow)
        self.assertIsNotNone(customer, "Customer capture step absent")
        self.assertIn("needs.credential-check.outputs.customer_ready == 'true'",
                      customer.group(1))
        self.assertIn("github.ref_name == '" + branch + "'", customer.group(1))
        self.assertIn("cancel-in-progress: false", self.workflow)

    def test_qa_membership_successor_preserves_customer_capture(self):
        branch = 'work-qa-review-branch-membership-20261101'
        self.assertIn('      - ' + branch, self.workflow)
        self.assert_branch_in_python_gate(branch, 'all_roles = (', 'ready = (')
        self.assert_branch_in_python_gate(branch, 'capture_requested = (', 'report =')
        self.assertIn("github.ref_name == '" + branch + "'", self.workflow)
        self.assertIn("needs.credential-check.outputs.customer_ready == 'true'", self.workflow)

    def test_country_visual_successor_uses_real_pinned_apk_not_regional_fixture(self):
        branch = "work-native-country-visual-proof-20261101"
        self.assertIn("      - " + branch, self.workflow)
        self.assert_branch_in_python_gate(branch, 'all_roles = (', 'ready = (')
        self.assert_branch_in_python_gate(branch, 'capture_requested = (', 'report =')
        self.assertIn("github.ref_name == '" + branch + "'", self.workflow)
        self.assertIn("Stage pinned nationwide Rider archive for visual evidence", self.workflow)
        self.assertIn("verify-bundled-thailand-apks.py --source-sha", self.workflow)
        self.assertIn("15b1861a1336d35119a5cc1564796b48dfe33cfd5ad35046f3bdfdeb7e8ef4ac", self.workflow)
        self.assertIn("QG_REQUIRE_BUNDLED_COUNTRY:", self.workflow)
        self.assertIn("QG_DEBUG_MAP_ARCHIVE:", self.workflow)
        self.assertIn("github.ref_name == '" + branch + "'", self.workflow)
        self.assertIn("QG_REQUIRE_BUNDLED_COUNTRY:", self.workflow)
        self.assertIn("QG_OFFLINE_MAP_ASSETS=$RUNNER_TEMP/queuego-offline-assets", self.workflow)


    def test_country_apk_export_successor_is_exact_source_and_role_isolated(self):
        branch = "work-native-country-apk-export-20261101"
        self.assertIn("      - " + branch, self.workflow)
        self.assert_branch_in_python_gate(branch, 'all_roles = (', 'ready = (')
        self.assert_branch_in_python_gate(branch, 'capture_requested = (', 'report =')
        self.assertIn("github.ref_name == '" + branch + "'", self.workflow)
        self.assertIn("Stage pinned nationwide Rider archive for visual evidence", self.workflow)
        self.assertIn("verify-bundled-thailand-apks.py --source-sha", self.workflow)
        # Never export unverified APKs: all three upload steps follow the
        # source/map isolation validator, and the export manifest is fail-closed.
        verify = self.workflow.index("- name: Verify actual three-role APK country isolation")
        manifest = self.workflow.index("- name: Record verified Android test APK identities")
        export = self.workflow.index("- name: Export exact-source Customer online-map debug APK")
        self.assertLess(verify, manifest)
        self.assertLess(manifest, export)
        for role in ("customer", "merchant", "rider"):
            self.assertIn("native-android/" + role +
                          "/build/outputs/apk/debug/" + role + "-debug.apk", self.workflow)
            self.assertIn("queuego-" + role + "-", self.workflow)
        self.assertIn("hashlib.file_digest(stream, \"sha256\")", self.workflow)
        self.assertIn('"source_sha": source', self.workflow)
        self.assertIn('"production_release": False', self.workflow)
        self.assertIn('"physical_gps_certified": False', self.workflow)
        self.assertIn('"visual_parity_certified": False', self.workflow)
        self.assertIn("if-no-files-found: error", self.workflow)
        self.assertIn("compression-level: 0", self.workflow)
        # New branch may use shared review accounts only under the existing
        # serial credential-gated workflow; no secrets go in exported manifests.
        self.assertIn("cancel-in-progress: false", self.workflow)
        self.assertIn("needs.credential-check.outputs.customer_ready == 'true'", self.workflow)
        manifest_section = self.workflow[manifest:export]
        self.assertNotIn("REVIEW_PASSWORD", manifest_section)
        self.assertNotIn("REVIEW_PHONE", manifest_section)

    def test_country_review_reuses_verified_archive_instead_of_rebuilding(self):
        branch = "work-qa-country-archive-reuse-20261102"
        self.assertIn("      - " + branch, self.workflow)
        self.assert_branch_in_python_gate(branch, 'all_roles = (', 'ready = (')
        self.assert_branch_in_python_gate(branch, 'capture_requested = (', 'report =')
        self.assertIn("github.ref_name == '" + branch + "'", self.workflow)
        stage = self.workflow.split(
            "- name: Stage pinned nationwide Rider archive for visual evidence", 1
        )[1].split("- name: Build actual Rider, Merchant and Customer debug APKs", 1)[0]
        self.assertIn("gh run download 41029740374", stage)
        self.assertIn("--name queuego-native-pilot-apks", stage)
        self.assertIn('source.read("assets/maps/thailand-shortbread.json")', stage)
        self.assertIn("hashlib.file_digest(stream, \"sha256\")", stage)
        self.assertIn("404204685", stage)
        self.assertIn("15b1861a1336d35119a5cc1564796b48dfe33cfd5ad35046f3bdfdeb7e8ef4ac", stage)
        self.assertNotIn("build-thailand-shortbread-pmtiles.sh", stage)
        self.assertNotIn("curl --fail", stage)
        self.assertIn("  actions: read", self.workflow)
        self.assertIn("QG_REQUIRE_BUNDLED_COUNTRY:", self.workflow)
        self.assertIn("verify-bundled-thailand-apks.py --source-sha", self.workflow)

    def test_predecessor_country_provenance_matches_actual_source_run(self):
        # Run 41029740374's native-pilot log recorded a map digest and size
        # before later unrelated job failure. Do not pin another Planetiler
        # build's bytes or trust an APK manifest without an independent pin.
        actual_sha = "15b1861a1336d35119a5cc1564796b48dfe33cfd5ad35046f3bdfdeb7e8ef4ac"
        stage = self.workflow.split(
            "- name: Stage pinned nationwide Rider archive for visual evidence", 1
        )[1].split("- name: Build actual Rider, Merchant and Customer debug APKs", 1)[0]
        self.assertIn('manifest["sha256"] == "' + actual_sha + '"', stage)
        self.assertIn('manifest["bytes"] == 404204685', stage)
        self.assertIn('digest == expected', stage)
        self.assertIn('archive_path.stat().st_size == 404204685', stage)
        self.assertIn('--expected-sha256 ' + actual_sha, stage)
        self.assertNotIn("abfedfc200c1ff81045f9f946a6f281b0a8664d4b440e269b68f0e628408ba3b", stage)

    def test_verified_provenance_successor_runs_all_role_capture(self):
        branch = "work-qa-country-provenance-verified-20261102"
        self.assertIn("      - " + branch, self.workflow)
        self.assert_branch_in_python_gate(branch, 'all_roles = (', 'ready = (')
        self.assert_branch_in_python_gate(branch, 'capture_requested = (', 'report =')
        self.assertIn("github.ref_name == '" + branch + "'", self.workflow)
        self.assertIn("QG_REQUIRE_BUNDLED_COUNTRY:", self.workflow)
        self.assertIn("verify-bundled-thailand-apks.py --source-sha", self.workflow)


    def test_country_pin_is_passed_to_actual_rider_capture_not_credential_check(self):
        # Run 41357466942: the SHA pin was scoped to credential-check only,
        # so capture failed before emulator screenshot collection.
        branch = "work-qa-country-pin-capture-env-20261102"
        expected = "15b1861a1336d35119a5cc1564796b48dfe33cfd5ad35046f3bdfdeb7e8ef4ac"
        self.assertIn("      - " + branch, self.workflow)
        self.assert_branch_in_python_gate(branch, 'all_roles = (', 'ready = (')
        self.assert_branch_in_python_gate(branch, 'capture_requested = (', 'report =')
        rider = self.workflow.split(
            "- name: Capture authenticated Rider views on Android Emulator", 1
        )[1].split("- name: Capture authenticated Merchant views on Android Emulator", 1)[0]
        credentials = self.workflow.split(
            "- name: Check review secrets without printing their values", 1
        )[1].split("- name: Save review readiness", 1)[0]
        self.assertIn("QG_EXPECTED_COUNTRY_SHA256: " + expected, rider)
        self.assertNotIn("QG_EXPECTED_COUNTRY_SHA256:", credentials)
        self.assertIn("QG_REQUIRE_BUNDLED_COUNTRY:", rider)
        self.assertIn("QG_DEBUG_MAP_ARCHIVE:", rider)
        self.assertIn("github.ref_name == '" + branch + "'", rider)
        self.assertIn("run: python3 native-android/qa/capture-rider-authenticated-review.py", rider)

    def test_all_role_dispatch_uses_any_available_test_account(self):
        self.assertIn('os.environ.get("GITHUB_EVENT_NAME") == "workflow_dispatch"', self.workflow)
        self.assertIn('any(role["ready"] for role in roles.values())', self.workflow)
        self.assertIn("cancel-in-progress: false", self.workflow)

    def test_each_capture_is_independently_credential_gated(self):
        blocks = {}
        for role in ("Rider", "Merchant", "Customer"):
            pattern = (r"- name: Capture authenticated " + role +
                       r" views on Android Emulator\n\s+if: ([^\n]+)")
            match = re.search(pattern, self.workflow)
            self.assertIsNotNone(match, role + " capture step absent")
            blocks[role] = match.group(1)
        for role in ("Rider", "Merchant", "Customer"):
            self.assertIn("needs.credential-check.outputs." + role.lower() + "_ready == 'true'",
                          blocks[role])
        self.assertIn("always()", blocks["Merchant"])
        self.assertIn("always()", blocks["Customer"])

    def test_customer_ride_is_read_only_and_explicitly_incomplete(self):
        for screen in ("ride", "ride-pickup-picker", "ride-destination-picker"):
            self.assertIn('"' + screen + '"', self.customer)
        self.assertIn("capture_ride_read_only(screens)", self.customer)
        self.assertIn("active trip may contain private addresses", self.customer)
        self.assertIn('"visual_parity_certified": False', self.customer)
        self.assertIn('"production_order_or_dispatch_mutations": False', self.customer)
        self.assertNotIn("api.request(", self.customer)

    def test_role_artifacts_remain_separate(self):
        for role in ("rider", "merchant", "customer"):
            self.assertIn("queuego-" + role + "-authenticated-screens", self.workflow)
            self.assertIn("artifacts/" + role + "-authenticated-review/*.png", self.workflow)


if __name__ == "__main__":
    unittest.main()
