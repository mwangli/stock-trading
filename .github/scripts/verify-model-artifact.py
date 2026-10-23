#!/usr/bin/env python3
import argparse
import hashlib
import json
from pathlib import Path


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as artifact:
        for chunk in iter(lambda: artifact.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description="Verify a versioned model artifact manifest.")
    parser.add_argument("manifest", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    if not args.manifest.is_file():
        raise SystemExit(f"Missing model manifest: {args.manifest}")
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    version_directory = args.manifest.parent
    checksum_lines: list[str] = []
    for entry in manifest.get("files", []):
        relative_path = entry.get("path")
        artifact_path = version_directory / relative_path
        if not artifact_path.is_file():
            raise SystemExit(f"Missing model artifact: {artifact_path}")
        actual_size = artifact_path.stat().st_size
        if actual_size != entry.get("size"):
            raise SystemExit(
                f"Model artifact size mismatch: {artifact_path}, "
                f"expected={entry.get('size')}, actual={actual_size}"
            )
        digest = sha256_file(artifact_path)
        if digest.lower() != str(entry.get("sha256", "")).lower():
            raise SystemExit(f"Model artifact SHA-256 mismatch: {artifact_path}")
        checksum_lines.append(f"{digest}  {artifact_path.as_posix()}")

    manifest_digest = sha256_file(args.manifest)
    checksum_lines.append(f"{manifest_digest}  {args.manifest.as_posix()}")
    args.output.write_text("\n".join(checksum_lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
