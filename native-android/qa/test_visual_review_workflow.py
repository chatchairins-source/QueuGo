"""Fail-closed source contracts for real, secret-backed Android visual review CI.

No credentials are read here; this checks workflow eligibility and role isolation.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/rider-android-authenticated-review.yml"
CUSTOMER = ROOT / "native-android/qa/capture-customer-authenticated-review.py"
PILOT = ROOT / ".github/workflows/build-native-rider-pilot.yml"
BRANCH = "work-native-ride-visual-capture-20261101"


class VisualReviewWorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.customer = CUSTOMER.read_text(encoding="utf-8")
        cls.pilot = PILOT.read_text(encoding="utf-8")

    def test_successor_branch_triggers_capture(self):
        self.assertIn("      - " + BRANCH, self.workflow)
        self.assertIn('GITHUB_REF_NAME") in (', self.workflow)
        self.assertIn('"' + BRANCH + '")', self.workflow)
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
        self.assertIn('"' + branch + '") or merchant_only)', self.workflow)
        self.assertIn("github.ref_name == '" + branch + "'", self.workflow)

    def test_qa_pilot_runs_on_qa_push_and_pr(self):
        # CI must validate exact QA source HEADs; do not expand the
        # secret-backed authenticated-review workflow to every QA branch.
        triggers = self.pilot.split("\npermissions:", 1)[0]
        self.assertIn("  pull_request:\n    branches:\n      - 'work-native-**'\n      - 'work-qa-**'", triggers)
        self.assertIn("  push:\n    branches:\n      - queuego-native-android-v1\n      - 'work-native-**'\n      - 'work-qa-**'", triggers)
        self.assertNotIn("      - 'work-qa-**'", self.workflow)

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
