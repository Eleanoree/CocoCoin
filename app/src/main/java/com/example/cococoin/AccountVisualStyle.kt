package com.example.cococoin

import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.core.graphics.toColorInt

// 帳戶的視覺樣式：
// 定義一個帳戶的圖示和顏色（例如「現金」是黃色系，「信用卡」是紫色系）
data class AccountVisualStyle(
    val symbol: String,           // 顯示在圓形圖示中的符號（例如「現」、「卡」、「銀」）
    val badgeFillColor: String,   // 圓形圖示的背景顏色
    val badgeStrokeColor: String, // 圓形圖示的邊框顏色
    val badgeTextColor: String,   // 圓形圖示的文字顏色
    val tagFillColor: String,     // 標籤的背景顏色
    val tagTextColor: String      // 標籤的文字顏色
)

// 帳戶視覺樣式解析器：
// 根據帳戶名稱自動判斷是哪種類型的帳戶（現金、信用卡、銀行...），
// 並回傳對應的顏色和圖示，讓 UI 更有辨識度
object AccountVisualStyleResolver {

    // 根據帳戶名稱解析出對應的視覺樣式
    fun resolve(accountName: String): AccountVisualStyle {
        val name = accountName.lowercase()  // 轉小寫，方便比對

        return when {
            // 現金類帳戶（例如「現金」、「Cash」）
            isCashAccount(name) -> AccountVisualStyle(
                symbol = "現",
                badgeFillColor = "#F7E6D4",
                badgeStrokeColor = "#E2C3A0",
                badgeTextColor = "#93653B",
                tagFillColor = "#FBF0E3",
                tagTextColor = "#8D6A49"
            )

            // 信用卡類帳戶（例如「國泰信用卡」、「VISA」）
            isCreditCardAccount(name) -> AccountVisualStyle(
                symbol = "卡",
                badgeFillColor = "#E7E3F5",
                badgeStrokeColor = "#CFC3E8",
                badgeTextColor = "#6F5E95",
                tagFillColor = "#F3EEFA",
                tagTextColor = "#73638E"
            )

            // 銀行帳戶（例如「國泰銀行」、「郵局」）
            isBankAccount(name) -> AccountVisualStyle(
                symbol = "銀",
                badgeFillColor = "#E4EDF7",
                badgeStrokeColor = "#C5D7EA",
                badgeTextColor = "#5B7693",
                tagFillColor = "#EEF4FA",
                tagTextColor = "#5F748A"
            )

            // 電子支付/數位帳戶（例如「悠遊卡」、「Line Pay」、「街口」）
            isDigitalAccount(name) -> AccountVisualStyle(
                symbol = "電",
                badgeFillColor = "#E3F0EA",
                badgeStrokeColor = "#BED8CA",
                badgeTextColor = "#5F8574",
                tagFillColor = "#EEF6F1",
                tagTextColor = "#607C70"
            )

            // 禮券/點數類（例如「7-11禮券」、「全家點數」）
            isVoucherAccount(name) -> AccountVisualStyle(
                symbol = "券",
                badgeFillColor = "#F4E9DF",
                badgeStrokeColor = "#E0CAB2",
                badgeTextColor = "#906B4B",
                tagFillColor = "#FAF2E8",
                tagTextColor = "#8A6A4B"
            )

            // 預設樣式（無法分類的帳戶）
            else -> AccountVisualStyle(
                symbol = "戶",
                badgeFillColor = "#ECE7E1",
                badgeStrokeColor = "#D7CEC5",
                badgeTextColor = "#7B6E66",
                tagFillColor = "#F4F0EB",
                tagTextColor = "#756A63"
            )
        }
    }

    // 將樣式套用到圓形圖示
    fun applyIconBadge(textView: TextView, style: AccountVisualStyle) {
        textView.text = style.symbol                     // 設定符號（例如「現」）
        textView.setTextColor(style.badgeTextColor.toColorInt())  // 設定文字顏色

        // 設定背景（圓形 + 顏色 + 邊框）
        val background = (textView.background.mutate() as GradientDrawable)
        background.setColor(style.badgeFillColor.toColorInt())
        background.setStroke(1, style.badgeStrokeColor.toColorInt())
    }

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