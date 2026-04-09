package com.example.cococoin

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

// Firebase 初始化器
object FirebaseInitializer {
    @Volatile  // 確保多執行緒時變數的變化能即時被看到
    private var attemptedInitialization = false  // 是否已經嘗試初始化過

    // 檢查 Firebase 是否可用
    fun canUseFirebase(context: Context): Boolean {
        ensureInitialized(context.applicationContext)
        return FirebaseApp.getApps(context.applicationContext).isNotEmpty()
    }

    // 確保 Firebase 已被初始化
    private fun ensureInitialized(context: Context) {
        // 如果已經嘗試過，就直接返回（避免重複初始化）
        if (attemptedInitialization) return

        synchronized(this) {  // 同步鎖，確保多執行緒安全
            if (attemptedInitialization) return
            attemptedInitialization = true

            // 如果已經有 Firebase 實例，就不需要再初始化
            if (FirebaseApp.getApps(context).isNotEmpty()) return

            // 嘗試用預設方式初始化（讀取 google-services.json）
            FirebaseApp.initializeApp(context)
            if (FirebaseApp.getApps(context).isNotEmpty()) return

            // 預設初始化失敗，從 strings.xml 讀取設定手動初始化
            val projectId = context.getString(R.string.firebase_project_id).trim()
            val applicationId = context.getString(R.string.firebase_application_id).trim()
            val apiKey = context.getString(R.string.firebase_api_key).trim()
            val storageBucket = context.getString(R.string.firebase_storage_bucket).trim()

            // 如果缺少必要資訊，就放棄初始化
            if (projectId.isEmpty() || applicationId.isEmpty() || apiKey.isEmpty()) {
                return
            }

            // 手動建立 Firebase 設定
            val builder = FirebaseOptions.Builder()
                .setProjectId(projectId)
                .setApplicationId(applicationId)
                .setApiKey(apiKey)

            if (storageBucket.isNotEmpty()) {
                builder.setStorageBucket(storageBucket)
            }

            // 嘗試手動初始化
            runCatching {
                FirebaseApp.initializeApp(context, builder.build())
            }
        }
    }
}