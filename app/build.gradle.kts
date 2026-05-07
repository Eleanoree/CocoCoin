/**
 * 🏗️【App 的建築藍圖】- build.gradle.kts
 *
 * 想像你要蓋一棟叫做「CocoCoin 記帳 App」的大樓：
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │                  🏗️ 建築藍圖                             │
 * ├─────────────────────────────────────────────────────────┤
 * │                                                          │
 *  │  📋 基本設定（這棟樓要蓋幾層？用什麼材料？）              │
 * │  ├─ 使用 Kotlin 語言（施工語言）                         │
 * │  ├─ 支援 Android 7.0 以上（門檻高度）                    │
 * │  └─ 目標 Android 14（最新的電梯系統）                    │
 * │                                                          │
 * │  🔌 Plugins（聘請哪些專家）                              │
 * │  ├─ Android Application（建築執照）                      │
 * │  ├─ Kotlin（Kotlin 施工隊）                             │
 * │  ├─ KSP（Room 資料庫的小幫手）                          │
 * │  └─ Firebase（雲端服務連線）                            │
 * │                                                          │
 * │  📦 Dependencies（購買哪些建材）                         │
 * │  ├─ AndroidX（地基與結構）                               │
 * │  ├─ Room（保險箱，存放記帳資料）                         │
 * │  ├─ Firebase（雲端備份系統）                            │
 * │  ├─ Material Design（室內設計與家具）                   │
 * │  └─ MPAndroidChart（分析圖表，圓餅圖長條圖）            │
 * │                                                          │
 * └─────────────────────────────────────────────────────────┘
 *
 * 這個檔案的作用：
 * 1️⃣ 告訴 Gradle（工頭）要用哪些工具和材料
 * 2️⃣ 設定 App 支援的 Android 版本範圍
 * 3️⃣ 宣告需要下載哪些第三方套件
 *
 * 注意：這個檔案修改後，要按「Sync Now」讓 Gradle 重新讀取！
 */

// ========== 🔌 Plugins（聘請專家團隊） ==========

plugins {
    // 🏛️ 告訴系統：這是一個 Android 應用程式（建築執照）
    id("com.android.application")

    // 🅺 用 Kotlin 語言來寫（聘請 Kotlin 施工隊）
    id("org.jetbrains.kotlin.android")

    // 🔧 啟用一個叫 KSP 的小助手（專門幫 Room 資料庫寫隱藏代碼）
    // KSP = Kotlin Symbol Processing，像是「自動化文書處理員」
    // 他會幫我們自動產生 DAO 的實作程式碼，不用自己寫！
    id("com.google.devtools.ksp")

    // ☁️ 用來啟用 Firebase 服務（雲端服務的合約）
    // alias(libs.plugins.google.gms.google.services) 是新版寫法
    // 舊版寫法是：id("com.google.gms.google-services")
    alias(libs.plugins.google.gms.google.services)
}

// ========== 📋 Android 設定（大樓規格） ==========

android {
    // 🏷️ 命名空間：就像這棟大樓的「地址」
    // 用來區分不同 App 的資源（layout、drawable 等）
    // 以前寫在 AndroidManifest.xml 的 package 屬性，現在獨立出來了
    namespace = "com.example.cococoin"

    // 📚 編譯 SDK 版本：用哪個版本的 Android 工具來蓋房子
    // 34 = Android 14（2023 年發布）
    compileSdk = 34

    // ========== 預設設定（大樓的基本規格） ==========
    defaultConfig {
        // 🆔 應用程式 ID：上架 Google Play 後的唯一識別碼
        // 就像這棟大樓的「統一編號」
        // 一旦上架就不能改，改了會變成不同的 App！
        applicationId = "com.example.cococoin"

        // 📱 最低支援 Android 版本（門檻高度）
        // 24 = Android 7.0 Nougat（2016 年發布）
        // 代表 Android 7.0 以下的舊手機不能安裝
        // 目前覆蓋率約 99% 以上的裝置
        minSdk = 24

        // 🎯 目標 SDK（這棟樓是用最新的標準設計的）
        // 表示 App 有針對 Android 14 做最佳化
        targetSdk = 34

        // 🔢 版本代號（內部用，數字越大表示更新）
        // versionCode 是用來判斷「哪個版本比較新」
        // 每次上架 Google Play 都要 +1
        versionCode = 1

        // 📛 版本名稱（使用者看得到的）
        // 使用者會在 Play 商店看到「1.0」、「1.1」、「2.0」
        versionName = "1.0"

        // 🧪 測試用的自動化「跑分」工具設定
        // AndroidJUnitRunner 會幫我們執行自動化測試
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ========== 編譯類型（大樓的建造模式） ==========
    buildTypes {
        // 🚀 Release 模式：要上架到 Play 商店的正式版本
        release {
            // 是否壓縮程式碼（移除沒用到的程式，讓 APK 變小）
            // false 表示不壓縮，方便除錯；上架前可以改 true
            // 改成 true 後，可以讓 APK 檔案大小減少 30-50%！
            isMinifyEnabled = false

            // 🛡️ 混淆規則檔：保護程式碼不被輕易反編譯
            // 混淆（ProGuard）會把類別名稱變成 a, b, c 這種無意義的名字
            // 目前沒啟用（isMinifyEnabled = false），所以這個檔案沒作用
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        // 🧪 Debug 模式：開發測試時用（預設）
        // 不用寫，Gradle 會自動產生
        // debug {
        //     isMinifyEnabled = false  // 偵錯模式不壓縮，方便除錯
        // }
    }

    // ========== Java 編譯設定 ==========
    compileOptions {
        // 🆙 讓 Java 編譯器用 Java 17 的語法
        // Java 17 是 2021 年發布的 LTS（長期支援版本）
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ========== Kotlin 編譯設定 ==========
    kotlinOptions {
        // 🆙 Kotlin 編譯出來的位元組碼也要相容 Java 17
        // 這樣 Java 和 Kotlin 的程式碼才能互相呼叫
        jvmTarget = "17"
    }
}

// ========== 📦 依賴套件（購買建材） ==========
// 所有需要用到的「外部套件」，就像買水泥、鋼筋、磁磚一樣

dependencies {
    // ========== 🧱 Android 核心套件（地基與結構） ==========

    // Kotlin 版 Android 核心功能
    // 提供 ViewModel、Lifecycle 等基本元件
    implementation("androidx.core:core-ktx:1.13.1")

    // 支援舊版 Android 的相容套件
    // 讓新版功能也可以在舊手機上運作
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Material Design 元件
    // 提供漂亮的按鈕、卡片、輸入框等 UI 元件
    implementation("com.google.android.material:material:1.12.0")

    // ========== 📱 Activity 與 Lifecycle（畫面與生命週期） ==========
    // 讓頁面在旋轉或切換時資料不會消失

    // Activity 的 Kotlin 擴充功能
    implementation("androidx.activity:activity-ktx:1.9.0")

    // 生命週期執行階段（處理 onStart、onStop 等）
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")

    // ViewModel 的 Kotlin 擴充功能
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")

    // ========== 🎨 UI 佈局相關（室內設計） ==========
    // 把按鈕、卡片排得整整齊齊

    // 卡片樣式（像信用卡那樣的圓角卡片）
    implementation("androidx.cardview:cardview:1.0.0")

    // 彈性佈局（讓畫面在不同尺寸的手機上都能正常顯示）
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // 存取檔案（例如備份到手機資料夾）
    implementation("androidx.documentfile:documentfile:1.0.1")

    // ========== 🏦 Room 資料庫（存放記帳資料的保險箱） ==========

    // Room 執行時期（實際運作的核心）
    implementation("androidx.room:room-runtime:2.6.1")

    // Room 對 Kotlin 協程的支援（讓資料庫操作不會卡畫面）
    implementation("androidx.room:room-ktx:2.6.1")

    // KSP 編譯器：編譯時自動產生 DAO 的實作程式碼
    // 使用 ksp() 而不是 implementation()
    // 因為這只在編譯時期需要，執行時期不需要
    ksp("androidx.room:room-compiler:2.6.1")

    // ========== 🔐 Google 登入憑證（Credentials API） ==========
    // 讓使用者可以用 Google 帳號登入

    // 憑證管理核心
    implementation("androidx.credentials:credentials:1.3.0")

    // Google Play 服務的憑證支援
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")

    // Google ID 登入（讓使用者用 Google 帳號登入）
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // ========== ☁️ Firebase 平台（雲端服務） ==========

    // Firebase BOM（Bill of Materials，物料清單）
    // 統一管理所有 Firebase 套件的版本，不用自己一個一個對
    // 就像買材料時，廠商提供一個「材料包」保證版本相容
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))

    // Firebase 帳號登入
    implementation("com.google.firebase:firebase-auth")

    // Firebase 雲端資料庫（Firestore）
    implementation("com.google.firebase:firebase-firestore")

    // ========== 🧪 測試用套件（品質檢查員） ==========
    // 用來檢查程式有沒有寫錯的自動化小工具

    // 單元測試（測試單一功能）
    testImplementation("junit:junit:4.13.2")

    // Android 儀器測試（測試整個畫面流程）
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    // Espresso（模擬使用者點擊、滑動）
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // ========== 📊 圖表套件（分析頁面用） ==========
    // MPAndroidChart：顯示圓餅圖、長條圖來分析花費
    // 讓你看到「餐飲佔總支出 30%」這種漂亮的圖表
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
}

/**
 * 💡【版本號小教室】
 *
 * versionCode 和 versionName 的關係：
 *
 * versionCode = 1 -> versionName = "1.0"
 * versionCode = 2 -> versionName = "1.0.1"（小更新）
 * versionCode = 3 -> versionName = "1.1.0"（新增功能）
 * versionCode = 4 -> versionName = "2.0.0"（大改版）
 *
 * 使用者看到的是 versionName
 * Google Play 用 versionCode 判斷哪個更新
 * 所以 versionCode 每次都要 +1，不能跳號！
 */

/**
 * 🔢【minSdk 與 targetSdk 小教室】
 *
 * minSdk = 24（Android 7.0）
 * ├─ 代表 App 不能安裝在 Android 6.0 以下的舊手機
 * └─ 為什麼？因為有些功能需要較新的系統支援
 *
 * targetSdk = 34（Android 14）
 * ├─ 代表 App 是為了 Android 14 設計的
 * ├─ 在 Android 14 上會用最新的行為模式
 * └─ 在舊手機上會用相容模式（但仍然可以跑）
 *
 * 最佳實踐：
 * targetSdk 應該永遠是當前最新版本
 * minSdk 根據使用者的手機分布決定
 */

/**
 * 🎭【依賴套件的完整分類】
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │                  📦 依賴套件分類                         │
 * ├─────────────────────────────────────────────────────────┤
 * │                                                          │
 * │  🧱 核心基礎                                            │
 * │  ├─ core-ktx：Kotlin 核心工具                           │
 * │  ├─ appcompat：舊版相容性                              │
 * │  └─ material：Material Design 漂亮元件                 │
 * │                                                          │
 * │  📱 生命週期                                            │
 * │  ├─ activity-ktx：Activity 擴充                        │
 * │  ├─ lifecycle-runtime-ktx：生命週期處理                │
 * │  └─ lifecycle-viewmodel-ktx：ViewModel 支援            │
 * │                                                          │
 * │  🏦 資料儲存                                            │
 * │  ├─ room-runtime：資料庫核心                            │
 * │  ├─ room-ktx：協程支援                                  │
 * │  └─ room-compiler：自動產生程式碼                      │
 * │                                                          │
 * │  ☁️ 雲端服務                                            │
 * │  ├─ firebase-bom：版本管理                             │
 * │  ├─ firebase-auth：登入系統                            │
 * │  └─ firebase-firestore：雲端資料庫                     │
 * │                                                          │
 * │  📊 視覺化                                              │
 * │  └─ MPAndroidChart：圖表顯示                           │
 * │                                                          │
 * └─────────────────────────────────────────────────────────┘
 */

/**
 * 🌟【什麼是 BOM？為什麼要用它？】
 *
 * BOM = Bill of Materials（物料清單）
 *
 * 以前（沒有 BOM）：
 * implementation("com.google.firebase:firebase-auth:33.7.0")
 * implementation("com.google.firebase:firebase-firestore:33.7.0")
 * // 要手動確保版本號一致，很麻煩！
 *
 * 現在（有 BOM）：
 * implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
 * implementation("com.google.firebase:firebase-auth")        // 不用寫版本
 * implementation("com.google.firebase:firebase-firestore")   // 不用寫版本
 *
 * 好處：
 * ✅ 版本號由 BOM 統一管理
 * ✅ 確保所有 Firebase 套件互相相容
 * ✅ 更新時只要改 BOM 版本就好
 */

/**
 * 💰【MPAndroidChart 圖表套件】
 *
 * 這是一個第三方的開源套件（不是 Google 官方）
 * 用來畫出：
 * - 📊 長條圖：每月支出比較
 * - 🥧 圓餅圖：各分類佔比
 * - 📈 折線圖：收入支出趨勢
 *
 * 在「分析」頁面會用到他！
 */