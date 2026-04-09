package com.example.cococoin

import android.app.Application

// Application 類別：
// App 的入口
class CocoCoinApp : Application() {

    // App 啟動時呼叫
    override fun onCreate() {
        super.onCreate()

        // 確保 Firebase 已登入（沒有的話會自動匿名登入）
        FirebaseAuthManager.ensureSignedIn(this) {
            // 登入完成後，初始化資料倉庫（會觸發雲端同步）
            CocoCoinRepository.getInstance(this).ensureInitialized()
        }
    }
}