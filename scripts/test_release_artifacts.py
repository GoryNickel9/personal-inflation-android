import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from release_artifacts import prepare, read_version


class ReleaseArtifactsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.version = self.root / "version.properties"
        self.version.write_text("versionCode=2\nversionName=1.1.0\n", encoding="utf-8")
        self.apk = self.root / "app-release.apk"
        self.apk.write_bytes(b"test artifact, APK signature verified separately by apksigner")
        self.metadata = self.root / "output-metadata.json"
        self.metadata.write_text(json.dumps({"applicationId": "cc.baldeddu.prezzi", "minSdkVersionForDexing": 26, "elements": [
            {"versionCode": 2, "versionName": "1.1.0", "outputFile": self.apk.name}]}), encoding="utf-8")

    def run_prepare(self, tag="v1.1.0", previous=None):
        return prepare(self.version, tag, self.apk, self.metadata, self.root / "out", previous)

    def test_generates_matching_assets_and_checksums(self):
        result = self.run_prepare()
        self.assertEqual(result["sha256"], hashlib.sha256(self.apk.read_bytes()).hexdigest())
        self.assertEqual(result["size"], self.apk.stat().st_size)
        self.assertEqual(json.loads((self.root / "out/update.json").read_text()), result)
        self.assertEqual((self.root / "out/PrezziNelTempo-1.1.0.apk").read_bytes(), self.apk.read_bytes())
        self.assertEqual(len((self.root / "out/SHA256SUMS.txt").read_text().splitlines()), 2)

    def test_rejects_mismatched_tag_and_built_metadata(self):
        with self.assertRaises(ValueError):
            self.run_prepare("v1.0.0")
        self.metadata.write_text('{"applicationId":"other", "elements":[]}', encoding="utf-8")
        with self.assertRaises(ValueError):
            self.run_prepare()

    def test_rejects_non_increasing_code(self):
        previous = self.root / "previous.json"
        previous.write_text('{"versionCode":2}', encoding="utf-8")
        with self.assertRaises(ValueError):
            self.run_prepare(previous=previous)

    def test_rejects_invalid_version(self):
        self.version.write_text("versionCode=0\nversionName=../../other\n", encoding="utf-8")
        with self.assertRaises(ValueError):
            read_version(self.version)

    def test_uses_minimum_sdk_from_built_metadata(self):
        data = json.loads(self.metadata.read_text())
        data["minSdkVersionForDexing"] = 29
        self.metadata.write_text(json.dumps(data), encoding="utf-8")
        self.assertEqual(self.run_prepare()["minSdk"], 29)


if __name__ == "__main__":
    unittest.main()
