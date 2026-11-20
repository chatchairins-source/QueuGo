"""Fail-closed contract for the real Rider Fused provider registration Task.

This checks source wiring only; it cannot replace a Google APIs emulator GPS fix.
Run: python3 native-android/qa/test-fused-subscription-failclosed.py
"""
from pathlib import Path
import unittest

SERVICE = Path(__file__).resolve().parents[1] / "rider/src/main/java/com/queuego/rider/RiderNavigationLocationService.kt"

class FusedSubscriptionContract(unittest.TestCase):
    def test_registration_task_handles_async_failure(self):
        source = SERVICE.read_text(encoding="utf-8")
        start = source.index("fusedClient.requestLocationUpdates(request, nextCallback, Looper.getMainLooper())")
        end = source.index("fusedClient.lastLocation.addOnSuccessListener", start)
        registration = source[start:end]
        self.assertIn(".addOnFailureListener { error ->", registration)
        self.assertIn('Log.e("QueueGoNavLocation", "fused_subscription_rejected", error)', registration)
        self.assertIn("if (callback === nextCallback)", registration)
        self.assertIn("RiderNavigationLocationStore.clear()", registration)
        self.assertIn("stopSelf()", registration)
        self.assertIn(".addOnSuccessListener", registration)

if __name__ == "__main__":
    unittest.main()
