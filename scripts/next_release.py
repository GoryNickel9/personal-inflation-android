"""Choose a monotonically increasing release version without committing generated files."""
import argparse
import json
from pathlib import Path
import re

from release_artifacts import read_version


def next_version(baseline: Path, previous: Path | None = None) -> tuple[int, str]:
    code, name = read_version(baseline)
    if previous:
        published = json.loads(previous.read_text(encoding="utf-8"))
        old_code, old_name = published["versionCode"], published["versionName"]
        if (type(old_code) is not int or not 1 <= old_code <= 2_100_000_000
                or not isinstance(old_name, str) or not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", old_name)):
            raise ValueError("Invalid published release version")
        code = max(code, old_code + 1)
        old_parts = tuple(map(int, old_name.split(".")))
        if tuple(map(int, name.split("."))) <= old_parts:
            name = f"{old_parts[0]}.{old_parts[1]}.{old_parts[2] + 1}"
    if code > 2_100_000_000:
        raise ValueError("Android versionCode limit reached")
    return code, name


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, default=Path("version.properties"))
    parser.add_argument("--previous", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--github-env", type=Path, required=True)
    args = parser.parse_args()
    code, name = next_version(args.baseline, args.previous)
    args.output.write_text(f"versionCode={code}\nversionName={name}\n", encoding="utf-8")
    with args.github_env.open("a", encoding="utf-8") as env:
        env.write(f"RELEASE_VERSION_CODE={code}\nRELEASE_VERSION_NAME={name}\nRELEASE_TAG=v{name}\n")
    print(f"Next release: v{name}, versionCode={code}")
