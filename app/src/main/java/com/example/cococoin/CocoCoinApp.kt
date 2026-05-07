package com.example.cococoin

import android.app.Application

/**
 * 🚀【CocoCoin 記帳 App 的入口】
 *
 * 想像你要開一間名叫「CocoCoin」的咖啡廳：
 * - Application 類別就像咖啡廳的「總開關」
 * - 當客人打開門（點擊 App 圖示）的那一刻，總開關就會啟動
 * - 這裡適合做「開店前的準備工作」：
 *   1️⃣ 確認電源（Firebase 登入）
 *   2️⃣ 打開金庫（初始化資料倉庫）
 *   3️⃣ 準備好菜單（載入分類資料）
 *
 * 重點：這是整個 App 最早執行的程式碼！
 *        比任何一個畫面（Activity/Fragment）都更早～
 */
class CocoCoinApp : Application() {

    /**
     * 🎬 App 啟動時自動呼叫（就像開店的「開門儀式」）
     *
     * 這個方法會在以下情況觸發：
     * - 使用者點擊 App 圖示打開 App
     * - 手機重新開機後，App 被喚醒
     * - 從背景切回前景（某些情況）
     *
     * 白話：只要 App 活過來，onCreate 就會被執行
     */
    override fun onCreate() {
        super.onCreate()  // 先叫爸爸（Application）做他的初始化工作

        // ========== 🔐 步驟 1：確保 Firebase 已登入 ==========
        // FirebaseAuthManager 就像「保全系統」
        // ensureSignedIn 會檢查：
        // - 已經登入？太好了，繼續
        // - 還沒登入？自動幫你匿名登入（就像發一張臨時訪客證）
        //
        // 為什麼要匿名登入？
        // 1. 使用者不需要註冊就能用雲端備份
        // 2. 以後想用 Google 登入時，可以把匿名資料合併過去
        // 3. 無痛升級體驗，不會嚇跑使用者
        FirebaseAuthManager.ensureSignedIn(this) {
            // ========== 📦 步驟 2：初始化資料倉庫 ==========
            // 登入完成後（拿到訪客證），才能打開金庫
            // ensureInitialized 會做很多事情：
            // - 遷移舊版資料（如果有）
            // - 與雲端同步（上傳/下載/合併）
            // - 準備好讓使用者開始記帳
            CocoCoinRepository.getInstance(this).ensureInitialized()

            // 💡 注意：ensureInitialized 是「非同步」的
            //    裡面的雲端同步不會阻塞畫面啟動
            //    使用者在看到畫面的同時，背景默默備份～
        }
    }
}

/**
 * 💡【為什麼需要自訂 Application？】
 *
 * 如果你沒有寫 CocoCoinApp，Android 會用預設的 Application
 * 但預設的沒有地方讓你寫「開機要做的事」
 *
 * 自訂的好處：
 * ✅ 可以放全域初始化的程式碼
 * ✅ 可以放整個 App 共用的資料（但要小心記憶體）
 * ✅ 可以處理所有 Activity 的生命週期（registerActivityLifecycleCallbacks）
 *
 * 記得在 AndroidManifest.xml 註冊：
 * <application
 *     android:name=".CocoCoinApp"
 *     ... >
 * </application>
 *
 * 如果忘了註冊，Android 會用預設的，你的 onCreate 永遠不會被呼叫！
 */

/**
 * 🎭【啟動流程 - 劇場版】
 *
 * 第 1 幕：使用者點擊 App 圖示
 *    ↓
 * 第 2 幕：Android 系統建立 CocoCoinApp 實例
 *    ↓
 * 第 3 幕：呼叫 onCreate()
 *    ↓
 * 第 4 幕：FirebaseAuthManager.ensureSignedIn()
 *    ├─ 檢查是否有 Google 帳戶已登入
 *    ├─ 沒有？自動匿名登入（UID: firebase:anonymous:abc123）
 *    └─ 有？用那個帳戶
 *    ↓
 * 第 5 幕：ensureSignedIn 的 callback 被觸發
 *    ↓
 * 第 6 幕：CocoCoinRepository.ensureInitialized()
 *    ├─ 遷移舊版資料（從 SharedPreferences 到 Room）
 *    ├─ 與雲端比對版本（本機 vs Firebase）
 *    ├─ 決定要上傳還是下載
 *    └─ 合併完成
 *    ↓
 * 第 7 幕：MainActivity 啟動（使用者看到首頁畫面）
 *    ↓
 * 第 8 幕：使用者開始記帳！🎉
 *
 * 整個過程通常不到 1 秒，使用者無感～
 */