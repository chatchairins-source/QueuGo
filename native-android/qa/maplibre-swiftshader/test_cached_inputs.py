"""A corrupt immutable dependency cache must never produce an audited repository."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

class CachedInputTests(unittest.TestCase):
    def test_corrupt_cached_bytes_are_rejected_before_repository_creation(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            paths = {name: root / name for name in ('baseline.aar', 'baseline.pom', 'x86.zip', 'arm.zip')}
            for path in paths.values():
                path.write_bytes(b'corrupted-cache-fixture')
            repository = root / 'repository'
            result = subprocess.run([sys.executable, 'native-android/qa/maplibre-swiftshader/prepare-repository.py', 'prepare',
                '--baseline-aar', str(paths['baseline.aar']), '--baseline-pom', str(paths['baseline.pom']),
                '--x86-zip', str(paths['x86.zip']), '--arm64-zip', str(paths['arm.zip']), '--repository', str(repository)],
                capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('AssertionError', result.stderr)
            self.assertFalse(repository.exists())

if __name__ == '__main__':
    unittest.main()
