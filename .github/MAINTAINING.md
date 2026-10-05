# Maintaining ths-anime

Notes for building and publishing the extensions. The user guide is in [README.md](../README.md).

## Signing (one-time setup for the GitHub Action)

The workflow in `.github/workflows/build.yml` builds on every push to `main`.
It publishes the APK, `index.min.json` and `repo.json` to the `repo` branch, and it needs a
signing key stored in repository secrets. Keep the same key forever: Aniyomi
refuses updates signed by a different key.

```sh
keytool -genkeypair -v -keystore signingkey.jks -alias ths-anime \
  -keyalg RSA -keysize 2048 -validity 36500
base64 -w0 signingkey.jks   # macOS: base64 -i signingkey.jks
```

Under **Settings → Secrets and variables → Actions**, add these secrets:

| Secret | Value |
|--------|-------|
| `SIGNING_KEY` | the base64 output above |
| `ALIAS` | `ths-anime` (the `-alias` you used) |
| `KEY_STORE_PASSWORD` | the keystore password |
| `KEY_PASSWORD` | the key password (same as the keystore password unless you set a different one) |

Then re-run the workflow from the **Actions** tab, or push to `main`.

## Building locally

Requires JDK 17+ and the Android SDK (set `sdk.dir` in `local.properties` or
`ANDROID_HOME`).

```sh
./gradlew assembleRelease
# -> src/zh/anime1/build/outputs/apk/release/aniyomi-zh.anime1-v<version>-release.apk
```

Without `signingkey.jks`, local builds are signed with your debug key.

## Adding another extension

Create `src/<lang>/<name>/` with a `build.gradle.kts`, `AndroidManifest.xml`,
`res/` and `src/`. Copy `src/zh/anime1` as a template. It is picked up
automatically. Bump `extVersionCode` whenever you change an extension.
