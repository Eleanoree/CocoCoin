package com.example.cococoin

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

// 自動本機備份管理器：
// 負責把記帳資料自動備份到手機的某個資料夾（使用者可以選要存哪裡）
// 每次資料變動時會自動更新備份檔，不用手動操作
class AutoLocalBackupManager(
    private val context: Context  // App 的環境資訊
) {
    // 用小筆記本儲存備份設定
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 設定備份資料夾（使用者選擇後呼叫）：
    // 記住使用者選的資料夾，以後自動備份就存到這裡
    fun configureFolder(uri: Uri) {
        // 把 Uri 轉成 DocumentFile（Android 的文件物件）
        val folder = DocumentFile.fromTreeUri(context, uri)

        // 儲存設定到小筆記本
        prefs.edit()
            .putString(KEY_TREE_URI, uri.toString())           // 儲存資料夾的路徑
            .putString(KEY_FOLDER_NAME, folder?.name ?: "已選擇資料夾")  // 儲存資料夾名稱
            .putString(KEY_LAST_MESSAGE, "已設定自動本機備份資料夾")  // 記錄狀態
            .apply()
    }

    // 清除備份設定（停用自動備份時呼叫）
    fun clearConfiguration() {
        prefs.edit()
            .remove(KEY_TREE_URI)        // 刪除資料夾路徑
            .remove(KEY_FOLDER_NAME)     // 刪除資料夾名稱
            .putString(KEY_LAST_MESSAGE, "尚未設定自動本機備份")  // 更新狀態訊息
            .apply()
    }

    // 取得目前的自動備份狀態
    fun getStatus(): AutoLocalBackupStatus {
        return AutoLocalBackupStatus(
            isConfigured = getTreeUri() != null,  // 有設定資料夾就是已啟用
            folderName = prefs.getString(KEY_FOLDER_NAME, null),
            lastBackupAt = prefs.getLong(KEY_LAST_BACKUP_AT, 0L).takeIf { it > 0L },
            lastMessage = prefs.getString(KEY_LAST_MESSAGE, "尚未設定自動本機備份").orEmpty()
        )
    }

    // 執行自動備份（把資料寫到備份檔）：
    // 把目前的記帳資料存到使用者之前選的資料夾裡
    fun backupSnapshot(snapshot: CocoCoinSnapshot): OperationResult {
        // 檢查有沒有設定資料夾
        val treeUri = getTreeUri()
            ?: return OperationResult.fail("尚未設定自動本機備份資料夾")

        return runCatching {  // 像防護罩，有錯誤就抓下來
            // 找到備份資料夾
            val folder = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("找不到備份資料夾")

            // 尋找是否已有備份檔（cococoin-auto-backup.json）
            val targetFile = folder.findFile(AUTO_BACKUP_FILE_NAME)
                ?: folder.createFile("application/json", AUTO_BACKUP_FILE_NAME.removeSuffix(".json"))
                ?: error("無法建立自動備份檔")

            // 把資料快照轉成 JSON 字串
            val json = CocoCoinBackupCodec.encode(snapshot)

            // 寫入檔案
            context.contentResolver.openOutputStream(targetFile.uri, "wt")?.bufferedWriter()?.use { writer ->
                writer.write(json)
            } ?: error("無法寫入自動備份檔")

            // 更新備份狀態（記錄備份時間和成功訊息）
            prefs.edit()
                .putLong(KEY_LAST_BACKUP_AT, System.currentTimeMillis())
                .putString(KEY_LAST_MESSAGE, "已自動更新本機備份")
                .apply()

            OperationResult.ok("已自動更新本機備份")
        }.getOrElse { exception ->
            // 備份失敗，記錄錯誤訊息
            prefs.edit()
                .putString(KEY_LAST_MESSAGE, exception.message ?: "自動本機備份失敗")
                .apply()
            OperationResult.fail(exception.message ?: "自動本機備份失敗")
        }
    }

    // 取得儲存的資料夾 Uri
    private fun getTreeUri(): Uri? {
        return prefs.getString(KEY_TREE_URI, null)?.let(Uri::parse)
    }

    companion object {
        private const val PREFS_NAME = "cococoin_auto_backup"        // 小筆記本名稱
        private const val KEY_TREE_URI = "tree_uri"                  // 資料夾路徑的儲存鍵
        private const val KEY_FOLDER_NAME = "folder_name"            // 資料夾名稱的儲存鍵
        private const val KEY_LAST_BACKUP_AT = "last_backup_at"      // 上次備份時間的儲存鍵
        private const val KEY_LAST_MESSAGE = "last_message"          // 上次備份訊息的儲存鍵
        const val AUTO_BACKUP_FILE_NAME = "cococoin-auto-backup.json"  // 自動備份檔的檔名
    }
}