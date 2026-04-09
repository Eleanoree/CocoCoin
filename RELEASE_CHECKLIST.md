# CocoCoin 上線前清單

## 已完成
- Firebase Anonymous Auth 已接入
- Firestore 雲端路徑已改為 `users/{uid}/device_backups/{deviceId}`
- SQLite 本機資料庫已作為主要資料來源
- App 啟動時會自動初始化 Firebase 並進行同步
- 設定頁可查看雲端同步狀態、匿名帳號與最後同步時間
- `assembleDebug` 已完成編譯驗證

## 發布前必做
- 在 Firebase Console 的 `Authentication -> Sign-in method` 啟用 `Anonymous`
- 在 Firestore `Rules` 發布正式規則，確認路徑使用 `users/{uid}/device_backups/{deviceId}`
- 在實機測試以下流程：
  - 首次安裝後新增帳戶
  - 新增交易
  - 編輯交易
  - 刪除交易
  - 關閉並重新開啟 App 後資料仍存在
  - Firestore 有出現對應的 `users/{uid}/device_backups/{deviceId}` 文件
- 確認 `google-services.json` 與目前 Firebase 專案一致
- 準備隱私政策，說明 Firebase Authentication 與 Firestore 的資料用途

## 建議補強
- 新增 Google 或 Email 登入，讓使用者可跨裝置找回資料
- 加入 Crashlytics 監控正式版崩潰
- 加入基本自動化測試，至少覆蓋 repository 的 CRUD 與同步初始化
- 將版本號與 app 名稱、圖示、商店描述整理成發布版本
- 在 Play Console 上架前做封閉測試
