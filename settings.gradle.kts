// Gradle 的「插件管理區塊」 決定要去哪裡找「施工工具（plugin）」
pluginManagement {
    repositories {
        google()                 // Google 官方工具倉庫（Android 核心工具）
        mavenCentral()           // 主流開源工具倉庫（大部分 Java/Kotlin 套件）
        gradlePluginPortal()     // Gradle 官方工具市集
    }
}

// 依賴解析管理：決定要去哪裡下載 App 需要用到的「材料（library）」
dependencyResolutionManagement {
    // 設定「倉庫模式」：FAIL_ON_PROJECT_REPOS 意思是「不准各個子專案自己亂加倉庫」，避免版本衝突
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()                // Google 的套件（AndroidX、Firebase 等）
        mavenCentral()          // 大多數開源套件（如 Retrofit、OkHttp）
        maven("https://jitpack.io")  // JitPack 倉庫：有些 GitHub 專案透過這裡發行
        // 此 app 用到的 MPAndroidChart 圖表套件就在 JitPack 上
    }
}

// 專案名稱：
rootProject.name = "CocoCoin"

// 包含哪個模組：
include(":app")