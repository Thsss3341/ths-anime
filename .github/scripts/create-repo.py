#!/usr/bin/env python3
"""Builds an Aniyomi extension repo (index.min.json + icons) from the APKs in repo/apk."""
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

BUILD_TOOLS = sorted((Path(os.environ["ANDROID_HOME"]) / "build-tools").iterdir())[-1]
REPO_DIR = Path("repo")
APK_DIR = REPO_DIR / "apk"
ICON_DIR = REPO_DIR / "icon"
ICON_DIR.mkdir(parents=True, exist_ok=True)


def source_id(name: str, lang: str, version_id: int = 1) -> int:
    """Same algorithm as AnimeHttpSource.generateId()."""
    digest = hashlib.md5(f"{name.lower()}/{lang}/{version_id}".encode()).digest()
    return int.from_bytes(digest[:8], "big") & 0x7FFFFFFFFFFFFFFF


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
for apk in sorted(APK_DIR.glob("*.apk")):
    badging = subprocess.check_output(
        [BUILD_TOOLS / "aapt", "dump", "--include-meta-data", "badging", apk]
    ).decode()
    pkg, code, version = PACKAGE_REGEX.search(badging).groups()

    with ZipFile(apk) as z, z.open(ICON_REGEX.search(badging).group(1)) as src:
        (ICON_DIR / f"{pkg}.png").write_bytes(src.read())

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
