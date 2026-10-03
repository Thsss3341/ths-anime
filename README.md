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
- Shows each anime's real cover from [Bangumi](https://bgm.tv) instead of
  anime1's single placeholder image. The lookup only runs when a cover is
  displayed. To turn it off, uncheck **使用Bangumi封面** in the extension
  settings.
- Optionally adds each anime's MyAnimeList title and ID to its description, or
  renames it to the MAL title, so MAL tracking finds it without typing a
  Japanese or English name. See
  [MAL tracking](#mal-tracking).
- Search matches anime1's anime list (Traditional or Simplified Chinese), so
  results are whole anime instead of single episodes. If nothing matches, it
  falls back to the site's own search.

The source name and language are unchanged, so the source ID
(`8121834351495460193`) is the same as the yuzono version. Your library
entries carry over.

## MAL tracking

Tracker search uses the anime's title, and anime1's titles are Chinese, which
MyAnimeList can't find. In the extension settings, set **標題語言（方便MAL追蹤）**:

| Option | Effect |
|--------|--------|
| 中文（anime1原標題） | Default, nothing is looked up. |
| 中文，簡介裡加上MAL ID | Keeps the Chinese title and adds `MAL: <title> (id:12345)` to the top of the description. Copy the title, or paste `id:12345` into the MAL tracker search for an exact match. |
| 羅馬拼音（MAL標題） / 英文 | Also renames the anime to MAL's romaji or English title, so the tracker search finds it as is. |

When an anime's details load, the extension:

1. Looks it up on Bangumi using anime1's Chinese title and its year/season, so
   the right season of a series is picked.
2. Takes Bangumi's original Japanese title to AniList, which returns the MAL
   ID and the romaji/English title MAL uses.
3. Adds the MAL line to the description and, for 羅馬拼音 / 英文, renames the
   anime.

If no match aired close to anime1's year/season, the Chinese title is kept
rather than guessing. Switching back to 中文 restores anime1's titles.

For anime already in your library, pull down to refresh the anime to add the
MAL line. They are only renamed if the app's **Update library anime titles to
match source** setting (Anikku: Settings → Advanced) is on.

## Install in Aniyomi / Animetail

1. Uninstall the yuzono **Anime1.me** extension. It has the same package name
   but a different signature, so Android won't install this one over it.
2. Go to **More → Settings → Browse → Extension repos → Add** (same place in
   Aniyomi and Animetail) and enter:

   ```
   https://raw.githubusercontent.com/thsss3341/ths-anime/repo/index.min.json
   ```

3. Install **Anime1.me** from **Browse → Extensions**, then trust the extension
   when prompted.

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
# -> src/zh/anime1/build/outputs/apk/release/aniyomi-zh.anime1-v14.5-release.apk
```

Without `signingkey.jks`, local builds are signed with your debug key.

## Adding another extension

Create `src/<lang>/<name>/` with a `build.gradle.kts`, `AndroidManifest.xml`,
`res/` and `src/`. Copy `src/zh/anime1` as a template. It is picked up
automatically. Bump `extVersionCode` whenever you change an extension.
