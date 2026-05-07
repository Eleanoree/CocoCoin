# CocoCoin

CocoCoin 是一款以 Android 原生 Kotlin 開發的個人記帳 App，主打「本機優先、雲端可同步、備份可匯出」，適合拿來做個人財務追蹤、課堂專題展示，或作為 Android 架構練習專案。

目前專案已包含本機資料庫、資產帳戶管理、月預算、分類分析、雲端同步與備份匯出等核心功能，並已配置 GitHub 版本控制所需的基本忽略規則。

## 功能特色

- 交易記錄：新增、編輯、刪除收入與支出交易
- 首頁總覽：顯示當月收支、預算使用比例與交易清單
- 分析頁：依分類與時間查看統計，搭配圖表呈現消費分布
- 資產管理：建立多個帳戶並追蹤各帳戶餘額
- 預算管理：設定每月預算並在首頁顯示剩餘比例
- 分類管理：維護收入與支出分類
- 本機資料庫：使用 Room 儲存核心資料
- 雲端同步：使用 Firebase Authentication 與 Firestore
- 備份機制：支援 JSON 備份匯入匯出、自動本機備份與 CSV 匯出

## 技術棧

- Kotlin
- Android SDK 34
- Min SDK 24
- Room
- Firebase Authentication
- Firebase Firestore
- Material Components
- MPAndroidChart
- Kotlin Coroutines

## 專案結構

```text
CocoCoin/
├── app/                       Android app 原始碼與資源
├── docs/                      專案分析與輸出文件
├── design/                    設計相關資料
├── tools/                     專案輔助腳本
├── topic-arch-v5-redesign/    簡報或展示素材來源
├── build.gradle.kts           Root Gradle 設定
└── RELEASE_CHECKLIST.md       上線前檢查清單
```

## 主要畫面

App 採用單一 `MainActivity` 搭配底部導覽，主要分成五個區塊：

- `HomeFragment`：首頁總覽與當月預算狀態
- `AnalysisFragment`：支出分析、分類明細、圖表視覺化
- `AddTransactionFragment`：新增交易與金額輸入
- `AssetsFragment`：帳戶餘額與每月預算設定
- `SettingsFragment`：帳本名稱、備份、同步、登入與分類管理

## 本機與雲端資料策略

- 本機資料以 Room 為主要來源
- App 啟動時會初始化 Firebase，並嘗試建立登入狀態
- 若未登入正式帳號，會先使用匿名登入以降低使用門檻
- 同步資料儲存在 Firestore 的 `users/{uid}/device_backups/{deviceId}` 路徑
- 專案支援手動匯出 JSON、匯出 CSV，以及自動本機備份

## 開發環境需求

- Android Studio
- JDK 17
- Android SDK 34
- Gradle Wrapper（已隨專案提供）

## 如何執行

1. Clone 專案：

```bash
git clone git@github.com:Eleanoree/CocoCoin.git
cd CocoCoin
```

2. 使用 Android Studio 開啟專案。
3. 等待 Gradle Sync 完成。
4. 若要使用 Firebase 雲端功能，請擇一完成設定：

- 放入自己的 `app/google-services.json`
- 或建立 `app/src/main/res/values/firebase_config.xml` 並填入 Firebase 設定值

5. 以模擬器或實機執行 app。

## Firebase 設定說明

為了避免把私人設定直接提交到 GitHub，以下檔案已加入 `.gitignore`：

- `app/google-services.json`
- `app/src/main/res/values/firebase_config.xml`

如果你要在自己的環境啟用雲端同步，請建立上述其中一種設定來源，並在 Firebase Console 至少啟用：

- Authentication 的 Anonymous Sign-In
- Firestore Database

若你打算支援 Google 或 Email 登入，也需要額外開啟對應的 Sign-in method。

## 建議驗證項目

- 首次安裝後能正常新增帳戶
- 新增、編輯、刪除交易流程正常
- 關閉 App 後本機資料仍可保留
- 雲端同步狀態能在設定頁顯示
- 備份匯出、匯入與 CSV 匯出可正常運作

## 版本控制說明

這個 repo 已排除大型簡報輸出、PDF/HTML 生成文件、`node_modules` 與 Firebase 私密設定，避免直接推送到 GitHub 時遭遇檔案過大或雜訊過多的問題。

如果要把此專案公開成作品集，建議再補上：

- App 截圖
- Demo GIF 或影片連結
- Firebase 架構圖
- 測試與發布流程說明

## License

目前尚未提供授權條款；若要公開發佈，建議補上 `LICENSE` 檔案。
