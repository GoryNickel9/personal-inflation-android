"""Validate the version and prepare the three public release assets."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil


def read_version(path: Path) -> tuple[int, str]:
    entries = dict(line.strip().split("=", 1) for line in path.read_text(encoding="utf-8").splitlines()
                   if line.strip() and not line.startswith("#"))
    code, name = int(entries["versionCode"]), entries["versionName"]
    if not 1 <= code <= 2_100_000_000 or not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", name):
        raise ValueError("Invalid release version")
    return code, name


def prepare(version_file: Path, tag: str, apk: Path, metadata: Path, output: Path,
            previous: Path | None = None) -> dict:
    code, name = read_version(version_file)
    if tag != f"v{name}":
        raise ValueError("Git tag does not match version.properties")
    if previous and code <= json.loads(previous.read_text(encoding="utf-8"))["versionCode"]:
        raise ValueError("versionCode must increase relative to the published release")
    info = json.loads(metadata.read_text(encoding="utf-8"))
    elements = info["elements"]
    if (info["applicationId"] != "cc.baldeddu.prezzi" or len(elements) != 1
            or elements[0]["versionCode"] != code or elements[0]["versionName"] != name
            or elements[0]["outputFile"] != apk.name):
        raise ValueError("Built APK metadata does not match the release version")
    size = apk.stat().st_size
    if not 0 < size <= 100 * 1024 * 1024:
        raise ValueError("APK exceeds the updater download limit")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    output.mkdir(parents=True, exist_ok=True)
    asset = f"PrezziNelTempo-{name}.apk"
    shutil.copy2(apk, output / asset)
    manifest = {"schemaVersion": 1, "applicationId": "cc.baldeddu.prezzi", "versionCode": code,
                "versionName": name, "minSdk": 26, "apkAsset": asset, "size": size, "sha256": digest}
    manifest_file = output / "update.json"
    manifest_file.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    manifest_hash = hashlib.sha256(manifest_file.read_bytes()).hexdigest()
    (output / "SHA256SUMS.txt").write_text(f"{digest}  {asset}\n{manifest_hash}  update.json\n", encoding="utf-8")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version-file", type=Path, default=Path("version.properties"))
    parser.add_argument("--tag", required=True)
    parser.add_argument("--apk", type=Path, default=Path("app/build/outputs/apk/release/app-release.apk"))
    parser.add_argument("--metadata", type=Path, default=Path("app/build/outputs/apk/release/output-metadata.json"))
    parser.add_argument("--output", type=Path, default=Path("artifacts/release"))
    parser.add_argument("--previous", type=Path)
    args = parser.parse_args()
    result = prepare(args.version_file, args.tag, args.apk, args.metadata, args.output, args.previous)
    print(f"Prepared {result['apkAsset']}: versionCode={result['versionCode']}, sha256={result['sha256']}")
