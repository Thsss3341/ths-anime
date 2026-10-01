#!/usr/bin/env python3
"""Builds an Aniyomi extension repo (index.min.json, repo.json, icons) from the APKs in repo/apk."""
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path
from zipfile import ZipFile

PACKAGE_REGEX = re.compile(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'")
NSFW_REGEX = re.compile(r"'tachiyomi.animeextension.nsfw' value='([^']+)'")
LABEL_REGEX = re.compile(r"^application-label:'([^']+)'", re.MULTILINE)
ICON_REGEX = re.compile(r"^application-icon-320:'([^']+)'", re.MULTILINE)
APK_REGEX = re.compile(r"^aniyomi-([^.]+)\.([^-]+)-v")
SOURCE_FIELD_REGEX = r'override val {}\s*(?::\s*String)?\s*=\s*"([^"]+)"'
# "Signer #1 certificate SHA-256 digest: ..." on older apksigner, "V2 Signer: certificate ..." on newer.
FINGERPRINT_REGEX = re.compile(r"certificate SHA-256 digest: ([0-9a-f]{64})")

BUILD_TOOLS = sorted((Path(os.environ["ANDROID_HOME"]) / "build-tools").iterdir())[-1]
REPO_DIR = Path("repo")
APK_DIR = REPO_DIR / "apk"
ICON_DIR = REPO_DIR / "icon"
ICON_DIR.mkdir(parents=True, exist_ok=True)


def source_id(name: str, lang: str, version_id: int = 1) -> int:
    """Same algorithm as AnimeHttpSource.generateId()."""
    digest = hashlib.md5(f"{name.lower()}/{lang}/{version_id}".encode()).digest()
    return int.from_bytes(digest[:8], "big") & 0x7FFFFFFFFFFFFFFF


def signing_fingerprint(apk: Path) -> str:
    output = subprocess.check_output(
        [BUILD_TOOLS / "apksigner", "verify", "--print-certs", apk]
    ).decode()
    match = FINGERPRINT_REGEX.search(output)
    if not match:
        raise SystemExit(f"Could not read the signing certificate of {apk.name}:\n{output}")
    return match.group(1)


def read_source(lang_dir: str, ext_dir: str) -> dict:
    """Reads name/lang/baseUrl straight from the extension's Kotlin source."""
    text = "\n".join(p.read_text(encoding="utf-8") for p in Path("src", lang_dir, ext_dir).rglob("*.kt"))
    fields = {}
    for field in ("name", "lang", "baseUrl"):
        match = re.search(SOURCE_FIELD_REGEX.format(field), text)
        if not match:
            raise SystemExit(f"Could not find `override val {field} = \"...\"` in src/{lang_dir}/{ext_dir}")
        fields[field] = match.group(1)
    fields["id"] = str(source_id(fields["name"], fields["lang"]))
    return fields


index = []
fingerprints = set()
for apk in sorted(APK_DIR.glob("*.apk")):
    badging = subprocess.check_output(
        [BUILD_TOOLS / "aapt", "dump", "--include-meta-data", "badging", apk]
    ).decode()
    pkg, code, version = PACKAGE_REGEX.search(badging).groups()

    with ZipFile(apk) as z, z.open(ICON_REGEX.search(badging).group(1)) as src:
        (ICON_DIR / f"{pkg}.png").write_bytes(src.read())

    fingerprints.add(signing_fingerprint(apk))

    lang_dir, ext_dir = APK_REGEX.search(apk.name).groups()
    source = read_source(lang_dir, ext_dir)
    index.append(
        {
            "name": LABEL_REGEX.search(badging).group(1),
            "pkg": pkg,
            "apk": apk.name,
            "lang": source["lang"],
            "code": int(code),
            "version": version,
            "nsfw": int(NSFW_REGEX.search(badging).group(1)),
            "sources": [source],
        }
    )

index.sort(key=lambda x: x["pkg"])
print(json.dumps(index, ensure_ascii=False, indent=2))
with (REPO_DIR / "index.min.json").open("w", encoding="utf-8") as f:
    json.dump(index, f, ensure_ascii=False, separators=(",", ":"))

# Mihon-based apps (Animetail, newer Aniyomi) read repo.json to add a repo and
# only trust extensions signed with this key.
if len(fingerprints) != 1:
    raise SystemExit(f"Expected all APKs to share one signing key, found: {fingerprints}")
repository = os.environ.get("GITHUB_REPOSITORY", "thsss3341/ths-anime")
repo_meta = {
    "meta": {
        "name": "ths-anime",
        "shortName": "ths-anime",
        "website": f"https://github.com/{repository}",
        "signingKeyFingerprint": fingerprints.pop(),
    }
}
with (REPO_DIR / "repo.json").open("w", encoding="utf-8") as f:
    json.dump(repo_meta, f, indent=2)
