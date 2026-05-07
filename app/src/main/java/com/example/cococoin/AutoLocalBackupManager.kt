package com.example.cococoin

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * 💾【自動本機備份管理器】
 *
 * 想像你有一個超貼心的「自動存檔小精靈」：
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │ 🧚‍♀️ 自動存檔小精靈                                      │
 * │                                                         │
 * │ 任務：每次你記完一筆帳，小精靈就會自動把資料存到        │
 * │      你指定的手機資料夾裡！                             │
 * │                                                         │
 * │ 好處：                                                  │
 * │ ✅ 不用手動按備份，完全自動                             │
 * │ ✅ 換手機時把資料夾複製過去就能還原                     │
 * │ ✅ 即使沒有網路也能備份（純本機）                       │
 * │ ✅ 備份檔是 JSON 格式，可以用任何文字編輯器打開         │
 * └─────────────────────────────────────────────────────────┘
 *
 * 流程：
 * 1. 使用者選擇一個資料夾（例如 Download/CocoCoinBackup/）
 * 2. 小精靈記住這個資料夾
 * 3. 每次資料變動，自動把最新資料寫成 JSON 存進去
 * 4. 檔案名稱固定叫「cococoin-auto-backup.json」
 *
 * 注意：這個是「本機備份」，不是雲端！
 *       雲端備份是 FirebaseSyncManager 負責的～
 */
class AutoLocalBackupManager(
    private val context: Context  // 📱 App 的環境資訊（需要它來存取檔案）
) {

    // ========== 📝 小筆記本（SharedPreferences）==========
    // 用來記住：
    // - 使用者選了哪個資料夾（tree_uri）
    // - 資料夾叫什麼名字（folder_name）
    // - 上次備份時間（last_backup_at）
    // - 上次備份結果訊息（last_message）
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ========== 📂 設定備份資料夾 ==========

    /**
     * 📁 設定備份資料夾（使用者選擇後呼叫）
     *
     * 白話：使用者透過系統檔案選擇器選了一個資料夾，
     *       小精靈把這個資料夾的位置記在小筆記本裡，
     *       以後自動備份就存到這裡！
     *
     * Android 檔案存取小知識：
     * 現代 Android 不能直接存取檔案系統的路徑（像是 /sdcard/Download/）
     * 要用「URI」（Uniform Resource Identifier）來代表檔案或資料夾
     * 就像網址一樣，例如：「content://com.android.externalstorage/...」
     *
     * DocumentFile 是 Android 提供的檔案操作工具，
     * 可以透過 URI 來讀寫檔案，不用管底層路徑～
     *
     * @param uri 使用者選擇的資料夾 URI（從系統檔案選擇器取得）
     *
     * 使用範例：
     * val launcher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
     *     if (uri != null) {
     *         backupManager.configureFolder(uri)
     *     }
     * }
     * launcher.launch(null)
     */
    fun configureFolder(uri: Uri) {
        // 🔄 把 Uri 轉成 DocumentFile（Android 的文件物件）
        // DocumentFile 就像一個「檔案總管指標」，可以操作檔案和資料夾
        val folder = DocumentFile.fromTreeUri(context, uri)

        // 📝 儲存設定到小筆記本（永久記住）
        // 就像在牆上釘一個便條紙：「備份資料夾在這裡喔！👉」
        prefs.edit()
            .putString(KEY_TREE_URI, uri.toString())           // 📍 儲存資料夾的路徑（URI 字串）
            .putString(KEY_FOLDER_NAME, folder?.name ?: "已選擇資料夾")  // 🏷️ 儲存資料夾名稱（例如「我的備份」）
            .putString(KEY_LAST_MESSAGE, "已設定自動本機備份資料夾")  // 💬 記錄狀態訊息
            .apply()  // 立即儲存（不要等到最後）
    }

    /**
     * 🧹 清除備份設定（停用自動備份時呼叫）
     *
     * 白話：使用者說「我不想自動備份了」，小精靈就把便條紙撕掉
     *
     * 效果：之後資料變動時，不會再自動備份
     */
    fun clearConfiguration() {
        prefs.edit()
            .remove(KEY_TREE_URI)        // 🗑️ 刪除資料夾路徑
            .remove(KEY_FOLDER_NAME)     // 🗑️ 刪除資料夾名稱
            .putString(KEY_LAST_MESSAGE, "尚未設定自動本機備份")  // 💬 更新狀態訊息
            .apply()
    }

    // ========== 📊 取得狀態 ==========

    /**
     * 📋 取得目前的自動備份狀態
     *
     * 白話：小精靈回報目前的設定狀況：
     * - 有設定資料夾嗎？（isConfigured）
     * - 資料夾叫什麼名字？（folderName）
     * - 上次備份是什麼時候？（lastBackupAt）
     * - 上次備份結果如何？（lastMessage）
     *
     * 這些資訊會在「設定」頁面顯示給使用者看
     *
     * @return AutoLocalBackupStatus 狀態物件
     */
    fun getStatus(): AutoLocalBackupStatus {
        return AutoLocalBackupStatus(
            isConfigured = getTreeUri() != null,  // ✅ 有設定資料夾就是已啟用
            folderName = prefs.getString(KEY_FOLDER_NAME, null),  // 📁 資料夾名稱
            lastBackupAt = prefs.getLong(KEY_LAST_BACKUP_AT, 0L).takeIf { it > 0L },  // 🕐 上次備份時間
            lastMessage = prefs.getString(KEY_LAST_MESSAGE, "尚未設定自動本機備份").orEmpty()  // 💬 狀態訊息
        )
    }

    // ========== 💾 執行自動備份 ==========

    /**
     * 💾 執行自動備份（把資料寫到備份檔）
     *
     * 白話：小精靈把目前的所有記帳資料，存成 JSON 檔案，
     *       放到使用者之前選的資料夾裡～
     *
     * 這個方法會在每次資料變動時被自動呼叫：
     * - 新增交易 → 自動備份
     * - 編輯交易 → 自動備份
     * - 刪除交易 → 自動備份
     * - 新增帳戶 → 自動備份
     * - 修改預算 → 自動備份
     *
     * 使用者完全不需要手動操作，超貼心！
     *
     * @param snapshot 資料快照（裡面有所有交易、帳戶、預算、分類）
     * @return 操作結果（成功/失敗 + 訊息）
     */
    fun backupSnapshot(snapshot: CocoCoinSnapshot): OperationResult {
        // 🔍 步驟 1：檢查有沒有設定資料夾
        val treeUri = getTreeUri()
            ?: return OperationResult.fail("尚未設定自動本機備份資料夾")
        // 白話：小精靈想存檔，但找不到資料夾位置（便條紙不見了）

        // 🛡️ 步驟 2：用 runCatching 建立防護罩
        // 白話：接下來的操作可能會出錯（檔案寫入失敗、權限問題...）
        //      用 runCatching 包起來，有錯誤就抓下來，不要讓 App 當機！
        return runCatching {

            // 📂 步驟 3：找到備份資料夾
            val folder = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("找不到備份資料夾")  // 白話：URI 存在但資料夾不見了？

            // 📄 步驟 4：尋找是否已有備份檔
            // 檔案名稱固定為「cococoin-auto-backup.json」
            val targetFile = folder.findFile(AUTO_BACKUP_FILE_NAME)
                ?: folder.createFile("application/json", AUTO_BACKUP_FILE_NAME.removeSuffix(".json"))
                ?: error("無法建立自動備份檔")

            // 🎁 步驟 5：把資料快照轉成 JSON 字串
            // CocoCoinBackupCodec 是編碼器（Encoder）
            // 負責把 Kotlin 物件變成 JSON 字串
            val json = CocoCoinBackupCodec.encode(snapshot)

            // ✍️ 步驟 6：寫入檔案
            // openOutputStream：打開一個寫入通道
            // bufferedWriter：加上緩衝區，寫入更有效率
            // use：自動關閉檔案（不用自己寫 close()）
            context.contentResolver.openOutputStream(targetFile.uri, "wt")?.bufferedWriter()?.use { writer ->
                writer.write(json)
            } ?: error("無法寫入自動備份檔")
            // 白話：小精靈打開檔案，把 JSON 寫進去，然後關上

            // ✅ 步驟 7：更新備份狀態（記錄成功）
            prefs.edit()
                .putLong(KEY_LAST_BACKUP_AT, System.currentTimeMillis())  // 🕐 記錄現在時間
                .putString(KEY_LAST_MESSAGE, "已自動更新本機備份")  // 💬 記錄成功訊息
                .apply()

            OperationResult.ok("已自動更新本機備份")

        }.getOrElse { exception ->
            // ❌ 步驟 8：備份失敗，記錄錯誤訊息
            // 白話：小精靈存檔失敗，在便條紙上寫下原因
            prefs.edit()
                .putString(KEY_LAST_MESSAGE, exception.message ?: "自動本機備份失敗")
                .apply()

            OperationResult.fail(exception.message ?: "自動本機備份失敗")
        }
    }

    // ========== 🔧 內部輔助函式 ==========

    /**
     * 🔍 取得儲存的資料夾 Uri
     *
     * 白話：從小筆記本裡讀出之前存的路徑
     *
     * @return 資料夾的 URI，如果沒設定過則回傳 null
     */
    private fun getTreeUri(): Uri? {
        return prefs.getString(KEY_TREE_URI, null)?.let(Uri::parse)
        // 步驟：讀取字串 → 轉成 Uri 物件 → 如果字串是 null 就不執行
    }

    // ========== 📦 伴生物件（常數區） ==========

    companion object {
        // 📝 小筆記本的名稱（不會跟其他 SharedPreferences 衝突）
        private const val PREFS_NAME = "cococoin_auto_backup"

        // 🔑 各種儲存鍵（就像便條紙上的標籤）
        private const val KEY_TREE_URI = "tree_uri"           // 資料夾路徑
        private const val KEY_FOLDER_NAME = "folder_name"      // 資料夾名稱
        private const val KEY_LAST_BACKUP_AT = "last_backup_at"  // 上次備份時間
        private const val KEY_LAST_MESSAGE = "last_message"    // 上次備份訊息

        // 📄 自動備份檔的檔名（固定名稱，不會亂變）
        const val AUTO_BACKUP_FILE_NAME = "cococoin-auto-backup.json"
    }
}

/**
 * 📊【自動本機備份狀態】
 *
 * 這個 data class 用來打包備份的狀態資訊，
 * 讓 UI 可以顯示給使用者看～
 *
 * @param isConfigured 是否已設定備份資料夾
 * @param folderName 資料夾名稱（例如「我的備份」）
 * @param lastBackupAt 上次備份時間（時間戳，null 表示從未備份過）
 * @param lastMessage 上次備份結果訊息（例如「已自動更新本機備份」）
 */

/**
 * 🎭【自動備份完整流程 - 劇場版】
 *
 * 場景：使用者第一次設定自動備份
 *
 * 第 1 幕：使用者打開「設定」頁面
 *    ↓
 * 第 2 幕：點擊「自動本機備份」選項
 *    ↓
 * 第 3 幕：系統跳岀資料夾選擇器
 *    ↓
 * 第 4 幕：使用者選擇「Download/CocoCoinBackup/」
 *    ↓
 * 第 5 幕：呼叫 configureFolder(uri)
 *    ├─ 把 URI 存到 SharedPreferences
 *    ├─ 把資料夾名稱「CocoCoinBackup」存起來
 *    └─ 記錄訊息：「已設定自動本機備份資料夾」
 *    ↓
 * 第 6 幕：使用者開始記帳（午餐 100 元）
 *    ↓
 * 第 7 幕：Repository 呼叫 syncRemoteAsync()
 *    ├─ firebaseSyncManager.pushSnapshot()  → 上傳雲端
 *    └─ autoLocalBackupManager.backupSnapshot() → 自動本機備份
 *    ↓
 * 第 8 幕：backupSnapshot() 執行
 *    ├─ 找到資料夾 /Download/CocoCoinBackup/
 *    ├─ 建立/更新 cococoin-auto-backup.json
 *    ├─ 寫入 JSON 資料
 *    └─ 記錄時間和成功訊息
 *    ↓
 * 第 9 幕：使用者換手機，把整個 CocoCoinBackup 資料夾複製過去
 *    ↓
 * 第 10 幕：新手機的 App 讀取備份檔，還原所有資料 ✨
 *
 * 完全自動，使用者無感！就像 iCloud 備份一樣方便～
 */

/**
 * 💡【DocumentFile 小教室】
 *
 * 為什麼不能用傳統的 File？
 *
 * 傳統 File（舊 Android）：
 * val file = File("/sdcard/Download/backup.json")
 * file.writeText(json)
 *
 * 為什麼不行了？
 * Android 10 以後，不能直接存取檔案系統路徑（安全性考量）
 * 就像你家門牌號碼不能隨便給陌生人一樣～
 *
 * DocumentFile + URI 的做法：
 * val uri = Uri.parse("content://com.android.externalstorage/...")
 * val document = DocumentFile.fromTreeUri(context, uri)
 *
 * 好處：
 * ✅ 使用者明確授權（跳出選擇器自己選資料夾）
 * ✅ 不需要 WRITE_EXTERNAL_STORAGE 權限
 * ✅ 符合 Google 的最新安全規範
 *
 * 缺點：
 * ❌ 程式碼稍微複雜
 * ❌ 不能直接用 File API
 *
 * 但你的程式碼已經完美處理了這些！👍
 */

/**
 * 🌟【設計亮點總結】
 *
 * 1. 完全自動化
 *    使用者設定一次後，之後完全不用管
 *
 * 2. 不影響使用者體驗
 *    備份在背景執行，使用者繼續記帳
 *
 * 3. 優雅的錯誤處理
 *    用 runCatching 包起來，失敗也不會讓 App 當機
 *
 * 4. 可復原性
 *    備份檔是純文字 JSON，任何編輯器都能打開
 *
 * 5. 跨裝置相容
 *    URI 方式存取，符合 Android 最新規範
 *
 * 6. 狀態可視化
 *    使用者可以在設定頁面看到備份狀態
 */

/**
 * 💬【備份策略對比】
 *
 * ┌─────────────┬──────────────────┬──────────────────┐
 * │             │ 雲端備份          │ 本機備份          │
 * │             │ (FirebaseSync)   │ (AutoLocalBackup) │
 * ├─────────────┼──────────────────┼──────────────────┤
 * │ 儲存位置    │ Google 伺服器     │ 手機內部儲存空間   │
 * │ 需要網路    │ ✅ 需要           │ ❌ 不需要         │
 * │ 跨裝置同步  │ ✅ 自動同步       │ ❌ 手動複製       │
 * │ 隱私程度    │ 資料在雲端        │ 資料只在自己手機   │
 * │ 備份頻率    │ 即時             │ 即時              │
 * │ 適合情境    │ 換手機、多裝置    │ 離線備份、私密資料 │
 * └─────────────┴──────────────────┴──────────────────┘
 *
 * 兩個都有是最好的！雙重保障，資料永不遺失～
 */