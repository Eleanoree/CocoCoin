package com.example.cococoin

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * 🔥【Firebase 初始化器】
 *
 * 想像你要開一間「記帳咖啡廳」：
 * - Firebase 就像政府的營業執照，有了它才能合法經營（使用雲端功能）
 * - 這個初始化器就是你的「開店辦證照專員」
 *
 * 他的工作很辛苦：
 * 1️⃣ 先試試看能不能拿到「預設執照」（google-services.json）
 * 2️⃣ 如果拿不到，就從口袋裡掏出備用資料（strings.xml）手動申請
 * 3️⃣ 確保只申請一次，不會重複跑流程（很聰明，不會浪費力氣）
 *
 * ⚠️ 為什麼要這麼麻煩？
 * 因為有些開發環境沒有 google-services.json（例如開源專案、特殊建置）
 * 所以要多一個「手動設定」的備案，就像帶備用鑰匙出門一樣！
 */
object FirebaseInitializer {

    /**
     * 🚩 是否已經嘗試過初始化？
     *
     * 就像你已經去辦過證照了，就不會傻傻地再跑一趟政府機關
     *
     * @Volatile 是一個魔法關鍵字，意思大概是：
     * 「嘿！這個變數可能會被多個工人同時使用，
     *   要確保每個人看到的都是最新版本，不能有人看到舊資料！」
     *
     * 白話：就像在群組裡宣布事情，要確保所有人都收到最新消息，
     *       不能有人還停留在「昨天的舊公告」
     */
    @Volatile
    private var attemptedInitialization = false  // 預設 false：還沒跑過流程

    /**
     * 🔍 檢查 Firebase 是否可用（就像問：證照辦好了嗎？）
     *
     * @param context 應用程式上下文（App 的身份證，用來知道「我是誰」）
     * @return true 表示 Firebase 可用，false 表示無法使用（可能網路問題或設定錯誤）
     *
     * 使用範例：
     * if (FirebaseInitializer.canUseFirebase(context)) {
     *     // 可以安心使用雲端功能了！
     *     uploadDataToCloud()
     * } else {
     *     // 沒有 Firebase，就乖乖把資料存本地吧
     *     saveDataLocally()
     * }
     */
    fun canUseFirebase(context: Context): Boolean {
        // 先確保初始化流程跑過（就像確認已經去過政府機關）
        ensureInitialized(context.applicationContext)

        // 檢查 Firebase 應用列表是不是空的
        // 白話：櫃檯有沒有發給我證照？有的話列表就不會是空的～
        return FirebaseApp.getApps(context.applicationContext).isNotEmpty()
    }

    /**
     * 🏢 確保 Firebase 已被初始化（開店辦證照的完整流程）
     *
     * 這是整個初始化器的核心大腦！
     * 裡面有滿滿的「聰明判斷」，避免重複執行、避免浪費資源
     *
     * @param context 應用程式上下文
     */
    private fun ensureInitialized(context: Context) {
        // ---------- 🧠 第一層檢查：已經試過了嗎？ ----------
        // 白話：已經跑過流程了就不需要再跑一次（就跟已經辦好證照不用再去排隊一樣）
        if (attemptedInitialization) return

        // ---------- 🔒 同步鎖（魔法保護罩）----------
        // 為什麼需要 synchronized？
        // 想像有 10 個客人同時衝進來櫃檯說「我要辦證照！」
        // 如果不控管，就會發生「10 個櫃員同時處理同一件事」的混亂
        //
        // synchronized 就像在門口放一個「一次只接待一位客人」的告示牌
        // 確保同時間只有一個執行緒（工人）能進來辦事～
        synchronized(this) {
            // 第二層檢查：進入保護區後再確認一次
            // （因為可能在你排隊的時候，前面的人已經辦好了）
            if (attemptedInitialization) return
            attemptedInitialization = true  // 標記「已經試過了」，下次就不用再試

            // ---------- 方式 1：檢查是否已經有 Firebase 實例 ----------
            // 白話：櫃檯已經發過證照了？有的話就收工回家～
            if (FirebaseApp.getApps(context).isNotEmpty()) return

            // ---------- 方式 2：用預設方式初始化（讀取 google-services.json）----------
            // 白話：先試試看「標準流程」能不能拿到證照
            // google-services.json 就像 Google 官方發給你的「快速通關卡」
            FirebaseApp.initializeApp(context)
            if (FirebaseApp.getApps(context).isNotEmpty()) return  // 成功了！收工～

            // ---------- 方式 3：手動設定（沒有快速通關卡時的備案）----------
            // 白話：標準流程失敗了！可能沒有 google-services.json 檔案
            // 這時候只好從口袋裡掏出「備用資料」手動填申請表

            // 先從 strings.xml 讀取 Firebase 需要的四胞胎資料
            // 這些就像申請證照需要的：
            // - 專案 ID（類似統一編號）
            // - 應用程式 ID（App 的身分證字號）
            // - API 金鑰（通關密碼）
            // - 儲存桶（雲端硬碟的地址）
            val projectId = context.getString(R.string.firebase_project_id).trim()
            val applicationId = context.getString(R.string.firebase_application_id).trim()
            val apiKey = context.getString(R.string.firebase_api_key).trim()
            val storageBucket = context.getString(R.string.firebase_storage_bucket).trim()

            // 🚨 檢查有沒有缺資料
            // 白話：如果缺少「統一編號」或「身分證字號」或「通關密碼」
            //       那就不用辦了，直接放棄（辦了也不會過）
            if (projectId.isEmpty() || applicationId.isEmpty() || apiKey.isEmpty()) {
                // 靜靜地離開，不發出任何聲音（低調失敗）
                return
            }

            // ---------- 🏗️ 手動建立 Firebase 設定（填寫申請表）----------
            // 使用建造者模式（Builder Pattern），像在填一個表格：
            // - 第一欄：專案 ID
            // - 第二欄：應用程式 ID
            // - 第三欄：API 金鑰
            // - 第四欄：儲存桶（選填，沒有也沒關係）
            val builder = FirebaseOptions.Builder()
                .setProjectId(projectId)
                .setApplicationId(applicationId)
                .setApiKey(apiKey)

            // 如果儲存桶資料有填，就加上去（就像申請表上的「選填欄位」）
            if (storageBucket.isNotEmpty()) {
                builder.setStorageBucket(storageBucket)
            }

            // ---------- 🎲 嘗試手動初始化（遞交申請表）----------
            // runCatching 就像一個「安全氣囊」：
            // 如果申請失敗（例如網路壞掉、資料錯誤），不會讓 App 當機爆炸
            // 只會默默地把失敗吞掉，然後當作沒事繼續執行
            runCatching {
                // 拿著填好的申請表，去櫃檯辦證照！
                FirebaseApp.initializeApp(context, builder.build())
            }
            // 注意：這邊沒有檢查是否成功，因為即使失敗也不影響 App 主要功能
            // 白話：辦不成證照就算了，大不了不用雲端功能嘛～
        }
    }
}