# ths-anime

[English](README.md) | **繁體中文**

適用於 [Anikku](https://github.com/komikku-app/anikku)、[Aniyomi](https://github.com/aniyomiorg/aniyomi)
及其他 Aniyomi 系 App 的改良版 **Anime1.me** 擴充套件。在 App 裡觀看 [anime1.me](https://anime1.me)
的動畫，並提供真實封面、更好用的搜尋，以及方便的 MyAnimeList（MAL）追蹤。

## 目錄

- [功能](#功能)
- [安裝](#安裝)
- [設定](#設定)
- [用 MyAnimeList 追蹤](#用-myanimelist-追蹤)
- [從 Bangumi 取得動畫資料](#從-bangumi-取得動畫資料)
- [更新](#更新)
- [疑難排解](#疑難排解)
- [致謝](#致謝)

## 功能

- **恢復正常使用。** 原本的擴充套件無法載入動畫列表，會顯示
  `Unable to resolve host "d1zquzjgwo9yb.cloudfront.net"`。此版本可以正常載入列表和播放影片。
- **真實封面。** 每部動畫都顯示自己的封面，不再全部是 anime1 的同一張預設圖片。
- **更好用的搜尋。** 用繁體或簡體中文輸入標題都能搜到，結果是整部動畫，而不是一集一集。
- **方便 MAL 追蹤。** 在簡介中顯示動畫的 MAL 標題和 ID，可直接複製到追蹤搜尋；也可以把動畫改名為
  MAL 標題，讓追蹤自動搜到。
- **劇情簡介**（可選），資料來自 [Bangumi](https://bgm.tv)。
- **保留收藏。** 如果之前用的是 yuzono 的 Anime1.me 擴充套件，收藏的動畫和觀看紀錄都會保留。

## 安裝

1. **如果已安裝 yuzono 的 Anime1.me 擴充套件，請先解除安裝。** 兩者名稱相同，Android 無法直接覆蓋安裝。
   收藏不會消失。
2. 用以下任一方式把本儲存庫加入 App：
   - **一鍵加入：** 在手機上開啟此連結：
     [將 ths-anime 加入 Anikku / Aniyomi](https://intradeus.github.io/http-protocol-redirector/?r=aniyomi://add-repo?url=https://raw.githubusercontent.com/thsss3341/ths-anime/repo/index.min.json)
   - **手動加入：** 前往 **其他 → 設定 → 探索 → 擴充套件儲存庫**，點 **新增**，貼上：

     ```
     https://raw.githubusercontent.com/thsss3341/ths-anime/repo/index.min.json
     ```

3. 前往 **探索 → 擴充套件**，找到 **Anime1.me** 並安裝。如果 App 詢問，點 **信任**。

## 設定

從 **探索 → 擴充套件 → Anime1.me**（設定圖示）開啟。

| 設定 | 作用 | 預設 |
|------|------|------|
| **使用Bangumi封面** | 顯示每部動畫的真實封面。關閉後改回 anime1 的預設圖片。 | 開啟 |
| **標題語言（方便MAL追蹤）** | 在簡介加上 MAL 標題和 ID，或把動畫改名為 MAL 標題。詳見[用 MyAnimeList 追蹤](#用-myanimelist-追蹤)。 | 中文 |
| **啟用Bangumi刮削** | 加上劇情簡介和更多資料。詳見[從 Bangumi 取得動畫資料](#從-bangumi-取得動畫資料)。 | 關閉 |
| **詳情拉取設置** | 開啟上一項時要加上多少資料。 | 拉取部分數據 |

## 用 MyAnimeList 追蹤

anime1 的標題是中文，MyAnimeList 的搜尋找不到。把 **標題語言（方便MAL追蹤）** 設為你想要的選項：

| 選項 | App 中顯示的標題 | 簡介 |
|------|------------------|------|
| **中文（anime1原標題）** | 中文 | 不變 |
| **中文，簡介裡加上MAL ID** | 中文 | 最上面加上 MAL 標題和 ID |
| **羅馬拼音（MAL標題）** | MAL 的羅馬拼音標題，例如 *Tensei Shitara Slime Datta Ken 3rd Season* | 最上面加上 MAL 標題和 ID |
| **英文（沒有英文名時用羅馬拼音）** | MAL 的英文標題，例如 *That Time I Got Reincarnated as a Slime Season 3* | 最上面加上 MAL 標題和 ID |

選第一項以外的選項時，動畫簡介的開頭會像這樣：

```
MAL: Tensei Shitara Slime Datta Ken 3rd Season
id:53580
```

**追蹤動畫的步驟：**

1. 打開動畫頁面，點追蹤按鈕，再選 MyAnimeList。
2. 如果選了羅馬拼音或英文，搜尋結果已經是正確的動畫。如果選了「中文，簡介裡加上MAL ID」，
   長按簡介中的 `id:53580` 那一行，複製後貼到搜尋框。搜尋 `id:` 加數字會直接找到那部動畫。

**已收藏的動畫：** 在動畫頁面往下拉重新整理，就會出現 MAL 資料。如果還想改名（羅馬拼音／英文），
先在 Anikku 開啟 **設定 → 進階 → Update library anime titles to match source**，再重新整理。

**小提示：**

- 會自動選對季數，例如「第三季」會對應到 MAL 上的第三季。
- 如果找不到有把握的對應，動畫會保留中文標題、不加 MAL 資料，而不會加上錯誤的資料。這種情況很少見，
  仍可在 MAL 手動搜尋。
- 改回「中文（anime1原標題）」並重新整理，就會恢復中文標題。

## 從 Bangumi 取得動畫資料

anime1 本身只提供標題和集數。開啟 **啟用Bangumi刮削** 後，動畫頁面會加上來自
[Bangumi](https://bgm.tv)（大型動畫資料庫）的資料。再用 **詳情拉取設置** 選擇：

- **拉取部分數據**：劇情簡介。
- **拉取完整數據**：劇情簡介、類型標籤、動畫公司、首播日期、原作、導演和播出狀態。

Bangumi 的資料是簡體中文。選「拉取完整數據」時，播出狀態也會來自 Bangumi，有時會在最後一集播出前就顯示為已完結。
如果主要只想看簡介，建議選 **拉取部分數據**。

## 更新

有新版本時，App 會在 **探索 → 擴充套件** 顯示更新。在該頁面往下拉即可檢查更新。

## 疑難排解

**顯示「Unable to resolve host d1zquzjgwo9yb.cloudfront.net」**<br>
你仍在使用舊的 yuzono 擴充套件。請解除安裝，並從本儲存庫安裝 Anime1.me（見[安裝](#安裝)）。

**加入儲存庫時顯示「HTTP 404」**<br>
確認網址完整貼上，或改用一鍵加入連結。如果連續試了好幾次，請等五分鐘後再試。

**App 無法安裝擴充套件**<br>
請先解除安裝其他 Anime1.me 擴充套件，同一時間只能安裝一個。

**封面顯示預設圖片或其他動畫的封面**<br>
這部動畫在 Bangumi 上找不到有把握的對應。如果介意錯誤的封面，可以關閉 **使用Bangumi封面**。

**簡介裡沒有 MAL 資料**<br>
在動畫頁面往下拉重新整理。如果仍然沒有，代表找不到有把握的對應，請在 MAL 手動搜尋。

**其他問題**<br>
請到 [Issues](https://github.com/Thsss3341/ths-anime/issues) 回報，附上動畫名稱和發生的情況。

## 致謝

- 以 [yuzono](https://github.com/yuzono/anime-extensions) 和
  [Kohi-den](https://github.com/Kohi-den/extensions-source) 的 Anime1.me 擴充套件為基礎。
- 封面和資料來自 [Bangumi](https://bgm.tv)；MAL 標題和 ID 透過 [AniList](https://anilist.co) 取得。
- 與 anime1.me 無關。以 [Apache License 2.0](LICENSE) 授權。
