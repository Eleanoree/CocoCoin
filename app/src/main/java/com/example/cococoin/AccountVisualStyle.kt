// ============================================================
// 🎨 帳戶視覺樣式 — 白話文：每個錢包的「衣服」和「化妝」
// ============================================================
//
// 情境劇：想像你的記帳本裡有各種帳戶：
//           👛 現金（黃色系）
//           💳 信用卡（紫色系）
//           🏦 銀行帳戶（藍色系）
//           📱 電子支付（綠色系）
//           🎫 禮券/點數（橘色系）
//
// 這個 AccountVisualStyle 就是定義每個錢包「長什麼樣子」的配方！
// 它包含了：
//   - symbol：圓形圖示裡的字（「現」、「卡」、「銀」）
//   - 各種顏色（背景色、邊框色、文字色）
// ============================================================
package com.example.cococoin

import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.core.graphics.toColorInt

data class AccountVisualStyle(
    val symbol: String,           // 🔤 顯示在圓形圖示中的符號（例如「現」、「卡」、「銀」）
    val badgeFillColor: String,   // 🎨 圓形圖示的背景顏色（例如淺黃色）
    val badgeStrokeColor: String, // 🖌️ 圓形圖示的邊框顏色（例如深黃色）
    val badgeTextColor: String,   // 📝 圓形圖示的文字顏色（例如深棕色）
    val tagFillColor: String,     // 🏷️ 標籤的背景顏色（例如淺米色）
    val tagTextColor: String      // 📝 標籤的文字顏色（例如棕色）
)

// ============================================================
// 🎨 帳戶視覺樣式解析器 — 白話文：錢包的「美妝師」+「色彩顧問」
// ============================================================
//
// 情境劇：想像你輸入了一個新帳戶叫「國泰信用卡」
//         這個解析器會自動判斷：
//           - 這是信用卡 → 用紫色系
//           - 圓形圖示寫「卡」
//           - 標籤也是紫色系
//
// 這樣使用者在記帳時，看到紫色標籤就知道「這筆是刷卡」！
// 不用特別記帳戶名稱，顏色會說話～
//
// object 關鍵字：單例模式，白話文就是「全公司只有一位美妝師」
// ============================================================
object AccountVisualStyleResolver {

    // ============================================================
    // 🔍 根據帳戶名稱解析出對應的視覺樣式 — 白話文：看名字決定化什麼妝
    // ============================================================
    fun resolve(accountName: String): AccountVisualStyle {
        val name = accountName.lowercase()  // 轉小寫，方便比對（「現金」和「現金」都一樣）

        return when {
            // 👛 現金類帳戶（例如「現金」、「Cash」）
            isCashAccount(name) -> AccountVisualStyle(
                symbol = "現",
                badgeFillColor = "#F7E6D4",  // 淺米黃色（像鈔票的顏色）
                badgeStrokeColor = "#E2C3A0", // 深米黃色
                badgeTextColor = "#93653B",   // 咖啡色（像銅板的顏色）
                tagFillColor = "#FBF0E3",     // 更淺的米色標籤
                tagTextColor = "#8D6A49"      // 深咖啡色文字
            )

            // 💳 信用卡類帳戶（例如「國泰信用卡」、「VISA」）
            isCreditCardAccount(name) -> AccountVisualStyle(
                symbol = "卡",
                badgeFillColor = "#E7E3F5",  // 淺紫色（信用卡的顏色）
                badgeStrokeColor = "#CFC3E8", // 深紫色
                badgeTextColor = "#6F5E95",   // 紫灰色
                tagFillColor = "#F3EEFA",     // 更淺的紫色標籤
                tagTextColor = "#73638E"      // 紫灰色文字
            )

            // 🏦 銀行帳戶（例如「國泰銀行」、「郵局」）
            isBankAccount(name) -> AccountVisualStyle(
                symbol = "銀",
                badgeFillColor = "#E4EDF7",  // 淺藍色（銀行的顏色，代表專業、穩重）
                badgeStrokeColor = "#C5D7EA", // 深藍色
                badgeTextColor = "#5B7693",   // 藍灰色
                tagFillColor = "#EEF4FA",     // 更淺的藍色標籤
                tagTextColor = "#5F748A"      // 藍灰色文字
            )

            // 📱 電子支付/數位帳戶（例如「悠遊卡」、「Line Pay」、「街口」）
            isDigitalAccount(name) -> AccountVisualStyle(
                symbol = "電",
                badgeFillColor = "#E3F0EA",  // 淺綠色（科技感、環保、數位）
                badgeStrokeColor = "#BED8CA", // 深綠色
                badgeTextColor = "#5F8574",   // 綠灰色
                tagFillColor = "#EEF6F1",     // 更淺的綠色標籤
                tagTextColor = "#607C70"      // 綠灰色文字
            )

            // 🎫 禮券/點數類（例如「7-11禮券」、「全家點數」）
            isVoucherAccount(name) -> AccountVisualStyle(
                symbol = "券",
                badgeFillColor = "#F4E9DF",  // 淺橘色（像禮券的顏色）
                badgeStrokeColor = "#E0CAB2", // 深橘色
                badgeTextColor = "#906B4B",   // 棕橘色
                tagFillColor = "#FAF2E8",     // 更淺的橘色標籤
                tagTextColor = "#8A6A4B"      // 棕橘色文字
            )

            // 🧾 預設樣式（無法分類的帳戶）
            else -> AccountVisualStyle(
                symbol = "戶",
                badgeFillColor = "#ECE7E1",  // 淺灰色（中性色，誰都可以用）
                badgeStrokeColor = "#D7CEC5", // 中灰色
                badgeTextColor = "#7B6E66",   // 深灰色
                tagFillColor = "#F4F0EB",     // 更淺的灰色標籤
                tagTextColor = "#756A63"      // 深灰色文字
            )
        }
    }

    // ============================================================
    // 🎨 將樣式套用到圓形圖示 — 白話文：幫錢包的小圓圈化妝
    // ============================================================
    // 這個函式負責把樣式實際應用到畫面上的圓形 TextView
    // 例如：記帳時左邊會有一個小圓圈，裡面寫「現」或「卡」
    fun applyIconBadge(textView: TextView, style: AccountVisualStyle) {
        textView.text = style.symbol                     // 設定符號（例如「現」）
        textView.setTextColor(style.badgeTextColor.toColorInt())  // 設定文字顏色

        // 設定背景（圓形 + 顏色 + 邊框）
        val background = (textView.background.mutate() as GradientDrawable)
        background.setColor(style.badgeFillColor.toColorInt())   // 設定背景色
        background.setStroke(1, style.badgeStrokeColor.toColorInt())  // 設定邊框（寬 1px）
    }

    // ============================================================
    // 🔍 判斷函式們 — 白話文：美妝師的「分類鏡」
    // ============================================================
    // 這些函式根據帳戶名稱的關鍵字來判斷是哪種類型的帳戶
    // 就像美妝師看到「信用卡」三個字，就知道要畫紫色系！

    // 判斷是否是現金類帳戶
    private fun isCashAccount(name: String): Boolean {
        return name.contains("現金") || name.contains("cash")
    }

    // 判斷是否是信用卡類帳戶
    private fun isCreditCardAccount(name: String): Boolean {
        return name.contains("信用卡") ||
                name.contains("visa") ||
                name.contains("master") ||
                name.contains("jcb") ||
                name.contains("amex") ||
                name.contains("card")
    }

    // 判斷是否是銀行帳戶
    private fun isBankAccount(name: String): Boolean {
        return name.contains("銀行") ||
                name.contains("郵局") ||
                name.contains("帳戶") ||
                name.contains("活存") ||
                name.contains("存款") ||
                name.contains("台新") ||
                name.contains("國泰") ||
                name.contains("玉山") ||
                name.contains("中信") ||
                name.contains("富邦") ||
                name.contains("永豐") ||
                name.contains("兆豐") ||
                name.contains("第一") ||
                name.contains("合作金庫") ||
                name.contains("華南") ||
                name.contains("彰銀") ||
                name.contains("上海") ||
                name.contains("bank")
    }

    // 判斷是否是電子支付/數位帳戶
    private fun isDigitalAccount(name: String): Boolean {
        return name.contains("悠遊卡") ||
                name.contains("一卡通") ||
                name.contains("icash") ||
                name.contains("line pay") ||
                name.contains("街口") ||
                name.contains("全支付") ||
                name.contains("全盈") ||
                name.contains("apple pay") ||
                name.contains("google pay") ||
                name.contains("台灣pay") ||
                name.contains("支付") ||
                name.contains("pay")
    }

    // 判斷是否是禮券/點數類帳戶
    private fun isVoucherAccount(name: String): Boolean {
        return name.contains("禮券") ||
                name.contains("點數") ||
                name.contains("券") ||
                name.contains("禮卡") ||
                name.contains("gift")
    }
}