// App 本身的建築藍圖：告訴 Gradle 要用哪些 plugins、支援哪些 Android 版本、依賴哪些套件

plugins {
    id("com.android.application")        // 告訴系統：這是一個 Android 應用程式
    id("org.jetbrains.kotlin.android")   // 用 Kotlin 語言來寫
    id("com.google.devtools.ksp")        // 啟用一個叫 KSP 的小助手（專門幫 Room 資料庫寫隱藏代碼）
    alias(libs.plugins.google.gms.google.services)   // 用來啟用 Firebase 服務
}

android {
    // 命名空間：
    namespace = "com.example.cococoin"

    // 編譯 SDK 版本：
    compileSdk = 34

    defaultConfig {
        // 應用程式 ID：上架 Google Play 後唯一識別碼
        applicationId = "com.example.cococoin"

        // 最低支援 Android 7.0（API 24）
        minSdk = 24

        // 目標 SDK：
        targetSdk = 34

        // 版本代號（內部用，數字越大表示更新）
        versionCode = 1

        // 版本名稱（使用者看得到的）
        versionName = "1.0"

        // 測試用的自動化「跑分」工具設定
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 是否壓縮程式碼（移除沒用到的程式，讓 APK 變小）
            // false 表示不壓縮，方便除錯；上架前可以改 true
            isMinifyEnabled = false

            // 混淆規則檔：保護程式碼不被輕易反編譯（目前沒啟用）
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // 讓 Java 編譯器用 Java 17 的語法
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        // Kotlin 編譯出來的位元組碼也要相容 Java 17
        jvmTarget = "17"
    }
}

// 所有需要用到的「外部套件」
dependencies {
    // Android 核心套件：讓 App 能在各版本 Android 跑得順、畫面長得漂亮
    implementation("androidx.core:core-ktx:1.13.1")      // Kotlin 版 Android 核心功能
    implementation("androidx.appcompat:appcompat:1.7.0") // 支援舊版 Android 的相容套件
    implementation("com.google.android.material:material:1.12.0") // Material Design 元件

    // Activity 與 Lifecycle（畫面與生命週期）：讓頁面在旋轉或切換時資料不會消失
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")

    // UI 佈局相關：把按鈕、卡片排得整整齊齊
    implementation("androidx.cardview:cardview:1.0.0")           // 卡片樣式
    implementation("androidx.constraintlayout:constraintlayout:2.2.0") // 彈性佈局
    implementation("androidx.documentfile:documentfile:1.0.1")   // 存取檔案（例如備份）

    // Room 資料庫：存放記帳資料的「保險箱」
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")   // 支援 Kotlin 協程
    ksp("androidx.room:room-compiler:2.6.1")         // 編譯時自動產生 DAO 實作程式碼

    // Google 登入憑證（Credentials API）
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Firebase 平台（雲端功能）
    implementation(platform("com.google.firebase:firebase-bom:33.7.0")) // BOM 統一版本
    implementation("com.google.firebase:firebase-auth")                 // 帳號登入
    implementation("com.google.firebase:firebase-firestore")            // 雲端資料庫

    // 測試用套件：用來檢查程式有沒有寫錯的自動化小工具
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // 圖表套件：MPAndroidChart（顯示圓餅圖、長條圖來分析花費）
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
}