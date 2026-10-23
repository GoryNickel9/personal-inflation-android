import json
from pathlib import Path
import tempfile
import unittest

from next_release import next_version


class NextReleaseTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.baseline = self.root / "version.properties"
        self.baseline.write_text("versionCode=3\nversionName=1.2.0\n", encoding="utf-8")
        self.previous = self.root / "previous.json"

    def published(self, code, name):
        self.previous.write_text(json.dumps({"versionCode": code, "versionName": name}), encoding="utf-8")
        return next_version(self.baseline, self.previous)

    def test_first_release_and_upgrade_from_manual_release(self):
        self.assertEqual(next_version(self.baseline), (3, "1.2.0"))
        self.assertEqual(self.published(2, "1.1.0"), (3, "1.2.0"))

    def test_repeated_pushes_increment_both_versions_without_editing_baseline(self):
        code, name = 3, "1.2.0"
        for expected in range(1, 4):
            code, name = self.published(code, name)
            self.assertEqual((code, name), (3 + expected, f"1.2.{expected}"))

    def test_numeric_comparison_and_higher_baseline(self):
        self.assertEqual(self.published(20, "1.2.9"), (21, "1.2.10"))
        self.assertEqual(self.published(21, "1.10.0"), (22, "1.10.1"))
        self.baseline.write_text("versionCode=30\nversionName=2.0.0\n", encoding="utf-8")
        self.assertEqual(self.published(21, "1.10.0"), (30, "2.0.0"))

    def test_rejects_invalid_previous_version_and_code_overflow(self):
        for code, name in [(True, "1.2.0"), (0, "1.2.0"), (3, "bad\nversion"), (2_100_000_000, "1.2.0")]:
            with self.subTest(code=code, name=name), self.assertRaises(ValueError):
                self.published(code, name)
