# 影音轉換大師 Android v1.5.4

桌面版 [YoutubeBilibiliMP4MP3Converter](https://github.com/huang1988pioneer/YoutubeBilibiliMP4MP3Converter) 的原生 Android 版（Kotlin、Jetpack Compose）。轉換在手機上完成，使用內建的 `yt-dlp` 與 `ffmpeg`，不經過自建伺服器。

## 功能

- 貼上 YouTube / Bilibili 網址，支援多行批次
- 從其他 App 分享連結，或直接開啟支援的影片網址
- 解析標題、時長、觀看次數、上傳日期
- 輸出 MP4（480P / 720P / 1080P / 4K）或 MP3
- 下載清單：進度、速度、完成 / 失敗，可在背景繼續
- 搜尋 YouTube / Bilibili，只保留標題或介紹含完整關鍵字的結果
- 最近 12 筆搜尋、我的最愛
- 字幕（預設關閉）：中文優先的外掛 `.srt`；MP4 內嵌字幕軌；MP3 另存 `.lrc`
- 可選下載整份播放清單
- 會員或需登入的影片可匯入 `cookies.txt`
- 完成的檔案存到「下載 / 影音轉換大師」

## 安裝

到 [Releases](https://github.com/huang1988pioneer/YoutubeBilibiliMP4MP3ConverterMobile/releases) 下載 `YoutubeBilibiliMP4MP3Converter-v1.5.4-android.apk`。

此 APK 包含 `arm64-v8a` 與 `armeabi-v7a`。第一次開啟會解壓 Python 與 ffmpeg，並在有網路時更新 yt-dlp，請稍候。

YouTube 若出現 HTTP 403，請按「更新 yt-dlp」，或匯入已登入帳號匯出的 `cookies.txt`。

## 建置

需要 JDK 17 與 Android SDK（compileSdk 35）。

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew :app:assembleRelease
```

簽章讀取專案根目錄的 `keystore.properties`（不進版控）：

```properties
storeFile=keystore/release.jks
storePassword=...
keyAlias=converter
keyPassword=...
```

未提供簽章檔時，release 組態無法簽名。單元測試：

```bash
./gradlew :app:testDebugUnitTest
```
