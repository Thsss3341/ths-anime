# ths-anime

**English** | [繁體中文](README.zh-TW.md)

Improved anime and video extensions for [Anikku](https://github.com/komikku-app/anikku),
[Aniyomi](https://github.com/aniyomiorg/aniyomi) and other Aniyomi-based apps:

| Extension | Site | Highlights |
|-----------|------|------------|
| **Anime1.me** | [anime1.me](https://anime1.me) | Works again, real covers, better search, easy MyAnimeList (MAL) tracking |
| **Xfani** (稀饭动漫) | [next.xifanacg.com](https://next.xifanacg.com) | Works again on the site's new website: search, browse and playback; easy MAL tracking |
| **Cycity** (次元城动漫) | [cycani.org](https://www.cycani.org) | Works again on the site's rebuilt website (playback needs a free 次元城 account); easy MAL tracking |
| **爱迪影视** | [aidi.tv](https://aidi.tv) | Films, US/UK/Korean/Japanese series, variety and documentaries in 1080p, with several lines per episode |
| **小宝影院** | [xiaoheimi.cc](https://xiaoheimi.cc) | A large catalog of films, series, variety, anime and documentaries in 1080p |

> [!NOTE]
> This is a personal project, maintained for my own watching. Extensions may break when a site
> changes, and fixes depend on my free time. Most of the code was written with the help of AI
> (Claude Code) and tested by me on my own device.

## Contents

- [Features](#features)
  - [Anime1.me](#anime1me)
  - [Xfani (稀饭动漫)](#xfani-稀饭动漫)
  - [Cycity (次元城动漫)](#cycity-次元城动漫)
  - [爱迪影视](#爱迪影视)
  - [小宝影院](#小宝影院)
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
- **Search in Traditional or Simplified Chinese.** Taiwanese titles work too, e.g. 藥師少女的獨語 finds
  药屋少女的呢喃.
- **Every playback line.** Each episode lists a video for every line the site has (主线-1,
  主线-2, 备用-1), with your preferred line first, so you can switch if one doesn't play.
- **Easy MAL tracking**, the same as for Anime1.me. See [Tracking with MyAnimeList](#tracking-with-myanimelist).
- **Keeps your library.** Anime saved with the yuzono Xfani extension still open. Their episode
  list is rebuilt from the new site once, so episodes you had marked as watched show as unwatched
  again.

### Cycity (次元城动漫)

- **Works again.** 次元城 rebuilt its website in 2026, so the yuzono extension finds nothing
  (and gets "请使用 Android APP" on Android). This version reads the same data the official app
  uses.
- **Search, Popular and Latest**, plus filters for channel (频道), sort order (排序), genre (类型)
  and year (年份).
- **Search in Traditional or Simplified Chinese.** Taiwanese titles work too, e.g. 間諜家家酒 finds
  间谍过家家.
- **Playback needs a 次元城 account.** The site only gives video links to logged-in users, in its
  official app too. Register for free on the site or in the app, then enter the account in the
  extension's settings ([Settings](#cycity-次元城动漫-1)). Browsing and searching work without one.
- **Easy MAL tracking**, the same as for Anime1.me. See [Tracking with MyAnimeList](#tracking-with-myanimelist).
- **Keeps your library.** Anime saved with the yuzono Cycity extension still open: they're found on
  the new site by title. Their episode list is rebuilt once, so episodes you had marked as watched
  show as unwatched again.

### 爱迪影视

- **Not just anime.** Films, series (美剧, 英剧, 韩剧, 日剧, 国产剧…), variety shows, documentaries and
  anime. For example Breaking Bad, Better Call Saul, Stranger Things, Squid Game and
  苦尽柑来遇见你.
- **High quality.** Almost everything is 1080p, at a higher bitrate than most similar sites (about
  3–6 Mbps in tests, e.g. 6.3 Mbps for 苦尽柑来遇见你).
- **Several lines per episode** (APP专用线路, VIP线路, 蓝光四, …), with your preferred line first, so
  you can switch if one is slow.
- **Popular, Latest and search**, plus a category filter (分类). Search works in Traditional or
  Simplified Chinese.
- No account needed.

### 小宝影院

- **A large catalog.** Films, series (欧美剧, 韩国剧, 日本剧, 国产剧…), variety, anime and documentaries,
  including every season of Breaking Bad and Better Call Saul, Squid Game, 苦尽柑来遇见你 and
  Stranger Things.
- **1080p** (about 1.6–2.6 Mbps in tests).
- **Popular, Latest and search**, plus filters for category (分类), sort order (排序), region (地区),
  language (语言) and year (年份). Search works in Traditional or Simplified Chinese.
- The site lists one category at a time, so Popular and Latest show the category chosen in the
  settings (连续剧 by default).
- No account needed. Formerly known as 小新TV.

## Install

1. **If you have the yuzono Anime1.me, Xfani or Cycity extension installed, uninstall it first.** They
   have the same names, so Android won't install these over them. Your library is kept.
2. Add this repository to the app, using either option:
   - **One tap:** open this link on your phone:
     [Add ths-anime to Anikku / Aniyomi](https://intradeus.github.io/http-protocol-redirector/?r=aniyomi://add-repo?url=https://raw.githubusercontent.com/thsss3341/ths-anime/repo/index.min.json)
   - **Manually:** go to **More → Settings → Browse → Extension repos**, tap **Add** and paste:

     ```
     https://raw.githubusercontent.com/thsss3341/ths-anime/repo/index.min.json
     ```

3. Go to **Browse → Extensions**, find **Anime1.me**, **Xfani**, **Cycity**, **爱迪影视** and/or
   **小宝影院** and install them. Tap **Trust** if the app asks.

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
| **标题语言（方便MAL追踪）** | Adds the MAL title and ID to descriptions, or renames anime to their MAL title. See [Tracking with MyAnimeList](#tracking-with-myanimelist). | 中文 |

### Cycity (次元城动漫)

| Setting | What it does | Default |
|---------|--------------|---------|
| **热门动画显示番剧周表** | Shows today's airing schedule under Popular instead of the most-watched anime. | Off |
| **标题语言（方便MAL追踪）** | Adds the MAL title and ID to descriptions, or renames anime to their MAL title. See [Tracking with MyAnimeList](#tracking-with-myanimelist). | 中文 |
| **次元城账号** | Your 次元城 username. Needed to play videos. | Empty |
| **次元城密码** | Your 次元城 password. Stored only in the app on your phone. | Empty |
| **最近一次错误** | Read-only. The full reason the last video failed to load, for reporting problems. | 无 |

Instead of entering your account, you can also log in to 次元城 in the extension's WebView; the
extension then uses that login.

### 爱迪影视

| Setting | What it does | Default |
|---------|--------------|---------|
| **首选播放线路** | Which line's video is listed first. The other lines are still listed below it. | APP专用线路 (highest bitrate in tests) |

### 小宝影院

| Setting | What it does | Default |
|---------|--------------|---------|
| **热门/最新显示的分类** | Which category Popular and Latest show (连续剧, 电影, 综艺, 动漫 or 纪录片). Use the filters to browse any other category. | 连续剧 |

## Tracking with MyAnimeList

This works in **Anime1.me**, **Xfani** and **Cycity**. Their titles are in Chinese, which MyAnimeList's
search can't find. Set **標題語言（方便MAL追蹤）** (in Xfani and Cycity: **标题语言（方便MAL追踪）**) to
the option you prefer. Xfani and Cycity name the options in Simplified Chinese, and their first
option is called 中文（网站原标题）.

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

**Cycity: "HTTP error 405", "请使用 Android APP" or "无法解析页面"**<br>
You're using the old yuzono Cycity extension or an older version from this repository. Update or
install Cycity from this repository ([Install](#install)).

**Cycity: "次元城要登录才能播放" or "次元城登录失败"**<br>
Enter your 次元城 username and password in the extension's settings. If you don't have an account,
register on [cycani.org](https://www.cycani.org) or in the 次元城 app. If logging in from the
extension still fails, open the extension's WebView (the globe icon), log in to 次元城 there, and
play the episode again: the extension uses that login too. If it still fails, open the extension's
settings and tap **最近一次错误**: it shows why each attempt failed (you can copy the text). Send
that along when you report it.

**Cycity: "播放太频繁"**<br>
次元城 limits how many videos an account can open in a short time. Wait a while and try again.

**Cycity: "这一集来自旧版网站"**<br>
The episode was saved before the site's rebuild. Pull down on the anime's page to refresh its
episode list.

**Cycity: "新版次元城找不到…"**<br>
An anime saved with the old extension couldn't be found on the new site by its title. Search for it
again and add it to your library.

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

**爱迪影视: a video doesn't play or keeps buffering**<br>
Open the episode's video list and pick another line.

**爱迪影视: nothing loads**<br>
The site itself is sometimes down (all of its addresses at once). Try again later.

**小宝影院: "网站要求输入验证码"**<br>
The site wants a captcha after many searches. Open the extension's WebView (the globe icon), search
there once, then search again in the app.

**Something else doesn't work**<br>
Open an [issue](https://github.com/Thsss3341/ths-anime/issues) with the anime's name and what
happened.

## Credits

- Based on the Anime1.me, Xfani and Cycity extensions from [yuzono](https://github.com/yuzono/anime-extensions) and
  [Kohi-den](https://github.com/Kohi-den/extensions-source).
- Covers and details from [Bangumi](https://bgm.tv); MAL titles and IDs via
  [AniList](https://anilist.co).
- Not affiliated with anime1.me, 稀饭动漫, 次元城动漫, 爱迪影视 or 小宝影院. Licensed under the [Apache License 2.0](LICENSE).
