// 「最外層的施工說明書」，定義所有子模組（如 app）共享的工具版本

plugins {
    // apply false：「先宣告有這個工具，但真正啟用要等到子模組（app）再說」

    id("com.android.application") version "8.5.2" apply false
    // Android App 建置工具（把程式碼變成 APK）

    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    // Kotlin 語言支援（讓 Android 看懂 Kotlin 程式碼）

    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    // Compose UI 工具

    id("com.google.devtools.ksp") version "2.0.0-1.0.24" apply false
    // KSP（Kotlin Symbol Processing）：用來自動產生程式碼，Room 資料庫需要

    id("com.google.gms.google-services") version "4.4.4" apply false
    // Google 服務工具（Firebase 登入、雲端資料庫需要）
}