# ths-anime

Personal [Aniyomi](https://github.com/aniyomiorg/aniyomi) extension repo.

| Extension | Notes |
|-----------|-------|
| Anime1.me (`zh-hant`) | Fork of the yuzono / Kohi-den extension with a fix for the anime list |

## Anime1.me fix

The original extension downloads the anime list from
`https://d1zquzjgwo9yb.cloudfront.net`. That CloudFront host no longer exists,
so browsing fails with:

```
Unable to resolve host "d1zquzjgwo9yb.cloudfront.net": No address associated with hostname
```

anime1.me now serves the same list from `https://anime1.me/animelist.json`.
This fork uses that URL. It also:

- Reads the signed video cookies (`h`, `p`, `e`) straight from the
  `v.anime1.me/api` response instead of relying on the WebView cookie store.
  Without them the video host returns 403.
- Parses episode dates in the `+08:00` timezone format the site uses.
- Refreshes the list when you pull to refresh instead of caching it until the
  app restarts.
- Handles entries that link to sister sites (for example `anime1.pw`).

The source name and language are unchanged, so the source ID
(`8121834351495460193`) is the same as the yuzono version. Your library
entries carry over.

## Install in Aniyomi

1. Uninstall the yuzono **Anime1.me** extension. It has the same package name
   but a different signature, so Android won't install this one over it.
2. In Aniyomi, go to **More → Settings → Browse → Extension repos → Add** and enter:

   ```
   https://raw.githubusercontent.com/thsss3341/ths-anime/repo/index.min.json
   ```

3. Install **Anime1.me** from **Browse → Extensions**, then trust the extension
   when prompted.

## Signing (one-time setup for the GitHub Action)

The workflow in `.github/workflows/build.yml` builds on every push to `main`.
It publishes the APK and `index.min.json` to the `repo` branch, and it needs a
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
# -> src/zh/anime1/build/outputs/apk/release/aniyomi-zh.anime1-v14.5-release.apk
```

Without `signingkey.jks`, local builds are signed with your debug key.

## Adding another extension

Create `src/<lang>/<name>/` with a `build.gradle.kts`, `AndroidManifest.xml`,
`res/` and `src/`. Copy `src/zh/anime1` as a template. It is picked up
automatically. Bump `extVersionCode` whenever you change an extension.
