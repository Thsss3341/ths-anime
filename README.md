# ths-anime

**English** | [繁體中文](README.zh-TW.md)

Improved anime extensions for [Anikku](https://github.com/komikku-app/anikku),
[Aniyomi](https://github.com/aniyomiorg/aniyomi) and other Aniyomi-based apps:

| Extension | Site | Highlights |
|-----------|------|------------|
| **Anime1.me** | [anime1.me](https://anime1.me) | Works again, real covers, better search, easy MyAnimeList (MAL) tracking |
| **Xfani** (稀饭动漫) | [next.xifanacg.com](https://next.xifanacg.com) | Works again on the site's new website: search, browse and playback |

> [!NOTE]
> This is a personal project, maintained for my own watching. Extensions may break when a site
> changes, and fixes depend on my free time. Most of the code was written with the help of AI
> (Claude Code) and tested by me on my own device.

## Contents

- [Features](#features)
  - [Anime1.me](#anime1me)
  - [Xfani (稀饭动漫)](#xfani-稀饭动漫)
- [Install](#install)
- [Settings](#settings)
- [Tracking with MyAnimeList](#tracking-with-myanimelist)
- [Anime details from Bangumi](#anime-details-from-bangumi)
- [Updating](#updating)
- [Troubleshooting](#troubleshooting)
- [Credits](#credits)

## Features

### Anime1.me

- **Works again.** The original extension stopped loading the anime list with
  `Unable to resolve host "d1zquzjgwo9yb.cloudfront.net"`. This version loads it and plays
  videos normally.
- **Real covers.** Every anime shows its own cover instead of anime1's single placeholder image.
- **Better search.** Type a title in Traditional or Simplified Chinese and get whole anime back,
  not individual episodes.
- **Easy MAL tracking.** Shows each anime's MAL title and ID so you can copy them into the tracker
  search, or renames anime to their MAL title so the tracker finds them by itself.
- **Plot summaries** (optional), taken from [Bangumi](https://bgm.tv).
- **Keeps your library.** If you used the yuzono Anime1.me extension before, your saved anime
  and watch history carry over.

### Xfani (稀饭动漫)

- **Works again.** 稀饭动漫 moved to a new website in 2026, so search, browsing and playback all
  stopped working in the yuzono extension. This version uses the new site.
- **Search, Popular and Latest**, plus filters for channel (频道), sort order (排序) and year (年份).
- **Every playback line.** Each episode lists a video for every line the site has (主线-1,
  主线-2, 备用-1), with your preferred line first, so you can switch if one doesn't play.
- **Keeps your library.** Anime saved with the yuzono Xfani extension still open. Their episode
  list is rebuilt from the new site once, so episodes you had marked as watched show as unwatched
  again.

## Install

1. **If you have the yuzono Anime1.me or Xfani extension installed, uninstall it first.** They
   have the same names, so Android won't install these over them. Your library is kept.
2. Add this repository to the app, using either option:
   - **One tap:** open this link on your phone:
     [Add ths-anime to Anikku / Aniyomi](https://intradeus.github.io/http-protocol-redirector/?r=aniyomi://add-repo?url=https://raw.githubusercontent.com/thsss3341/ths-anime/repo/index.min.json)
   - **Manually:** go to **More → Settings → Browse → Extension repos**, tap **Add** and paste:

     ```
     https://raw.githubusercontent.com/thsss3341/ths-anime/repo/index.min.json
     ```

3. Go to **Browse → Extensions**, find **Anime1.me** and/or **Xfani** and install them. Tap
   **Trust** if the app asks.

## Settings

Open them from **Browse → Extensions**, then the extension's settings icon.

### Anime1.me

| Setting | What it does | Default |
|---------|--------------|---------|
| **使用Bangumi封面** | Shows each anime's real cover. Turn off to go back to anime1's placeholder image. | On |
| **標題語言（方便MAL追蹤）** | Adds the MAL title and ID to descriptions, or renames anime to their MAL title. See [Tracking with MyAnimeList](#tracking-with-myanimelist). | 中文 |
| **啟用Bangumi刮削** | Adds a plot summary and more details. See [Anime details from Bangumi](#anime-details-from-bangumi). | Off |
| **詳情拉取設置** | How much detail to add when the option above is on. | 拉取部分數據 |

### Xfani (稀饭动漫)

| Setting | What it does | Default |
|---------|--------------|---------|
| **首选播放线路** | Which line's video is listed first. The other lines are still listed below it. | 主线-1 |

## Tracking with MyAnimeList

This section is about **Anime1.me**. anime1's titles are in Chinese, which MyAnimeList's search can't find. Set
**標題語言（方便MAL追蹤）** to the option you prefer:

| Option | Title shown in the app | Description |
|--------|------------------------|-------------|
| **中文（anime1原標題）** | Chinese | Unchanged |
| **中文，簡介裡加上MAL ID** | Chinese | Starts with the MAL title and ID |
| **羅馬拼音（MAL標題）** | MAL's romaji title, e.g. *Tensei Shitara Slime Datta Ken 3rd Season* | Starts with the MAL title and ID |
| **英文（沒有英文名時用羅馬拼音）** | MAL's English title, e.g. *That Time I Got Reincarnated as a Slime Season 3* | Starts with the MAL title and ID |

With any option except the first, the description of an anime starts like this:

```
MAL: Tensei Shitara Slime Datta Ken 3rd Season
id:53580
```

**To track an anime:**

1. Open the anime and tap the tracking button, then MyAnimeList.
2. With 羅馬拼音 or 英文, the search already shows the right anime. With 中文，簡介裡加上MAL ID,
   long-press the `id:53580` line in the description, copy it, and paste it into the search box.
   Searching for `id:` plus a number shows exactly that anime.

**Anime already in your library:** pull down on the anime's page to refresh it, and the MAL line
appears. To also rename them (羅馬拼音 / 英文), first turn on **Settings → Advanced → Update
library anime titles to match source** in Anikku, then refresh.

**Good to know:**

- The right season is picked automatically. For example, 第三季 links to the season 3 entry on MAL.
- If no confident match is found, the anime keeps its Chinese title and gets no MAL line rather
  than a wrong one. This is rare; you can still search MAL by hand.
- Switch back to 中文（anime1原標題） and refresh to restore the Chinese titles.

## Anime details from Bangumi

anime1 itself only provides titles and episodes. Turn on **啟用Bangumi刮削** to fill an anime's
page with information from [Bangumi](https://bgm.tv), a large anime database. Then choose with
**詳情拉取設置**:

- **拉取部分數據** (partial): plot summary.
- **拉取完整數據** (full): plot summary, genre tags, studio, air date, original author, director
  and airing status.

Bangumi's text is in Simplified Chinese. With the full option, the airing status also comes from
Bangumi, which sometimes marks a show as finished before its last episode airs. If you mainly
want summaries, use **拉取部分數據**.

## Updating

When a new version is released, the app shows an update under **Browse → Extensions**. Pull down
on that page to check for one.

## Troubleshooting

**"Unable to resolve host d1zquzjgwo9yb.cloudfront.net"**<br>
You're still using the old yuzono extension. Uninstall it and install Anime1.me from this
repository ([Install](#install)).

**Xfani: search shows nothing, or browsing fails**<br>
You're still using the old yuzono Xfani extension, which uses the site's old address. Uninstall it
and install Xfani from this repository ([Install](#install)).

**Xfani: a video doesn't play**<br>
Open the episode's video list and pick another line. Lines sometimes miss a file, especially
备用-1 for brand-new episodes.

**Adding the repository fails with "HTTP 404"**<br>
Check that the URL was pasted exactly, or use the one-tap link. If you tried several times in a
row, wait five minutes and try again.

**The app won't install the extension**<br>
Uninstall any other copy of the same extension (for example yuzono's) first; only one can be
installed at a time.

**A cover shows the placeholder image or a different anime**<br>
The anime couldn't be matched on Bangumi with confidence. Turn off **使用Bangumi封面** if wrong
covers bother you.

**No MAL line in the description**<br>
Pull down on the anime's page to refresh it. If it still doesn't appear, no confident match was
found; search MAL by hand.

**Something else doesn't work**<br>
Open an [issue](https://github.com/Thsss3341/ths-anime/issues) with the anime's name and what
happened.

## Credits

- Based on the Anime1.me and Xfani extensions from [yuzono](https://github.com/yuzono/anime-extensions) and
  [Kohi-den](https://github.com/Kohi-den/extensions-source).
- Covers and details from [Bangumi](https://bgm.tv); MAL titles and IDs via
  [AniList](https://anilist.co).
- Not affiliated with anime1.me or 稀饭动漫. Licensed under the [Apache License 2.0](LICENSE).
