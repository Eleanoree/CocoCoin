package com.example.cococoin

import android.graphics.Color           // 顏色處理
import android.os.Bundle                // 用來傳遞資料的包裹
import android.view.View                // 畫面上的元件基礎類別
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity  // App 活動的基礎類別
import androidx.fragment.app.Fragment

// App 的主畫面，包含底部導覽列和內容區域
class MainActivity : AppCompatActivity() {

    // 宣告底部導覽列的 5 個按鈕容器
    private lateinit var navHome: View      // 首頁按鈕區域
    private lateinit var navAnalysis: View  // 分析按鈕區域
    private lateinit var navAdd: View       // 記帳按鈕區域
    private lateinit var navAssets: View    // 資產按鈕區域
    private lateinit var navSettings: View  // 設定按鈕區域

    // 按鈕內部的容器（用來做動畫效果）
    private lateinit var navHomeInner: View
    private lateinit var navAnalysisInner: View
    private lateinit var navAddInner: View
    private lateinit var navAssetsInner: View
    private lateinit var navSettingsInner: View

    // 按鈕的圓形背景（選中時會顯示）
    private lateinit var bubbleHome: View
    private lateinit var bubbleAnalysis: View
    private lateinit var bubbleAdd: View
    private lateinit var bubbleAssets: View
    private lateinit var bubbleSettings: View

    // 按鈕的圖示（ImageView）
    private lateinit var iconHome: ImageView
    private lateinit var iconAnalysis: ImageView
    private lateinit var iconAdd: ImageView
    private lateinit var iconAssets: ImageView
    private lateinit var iconSettings: ImageView

    // 按鈕的文字標籤
    private lateinit var textHome: TextView
    private lateinit var textAnalysis: TextView
    private lateinit var textAdd: TextView
    private lateinit var textAssets: TextView
    private lateinit var textSettings: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupBottomNav()

        // 如果是第一次啟動（沒有儲存過的狀態），就顯示首頁
        if (savedInstanceState == null) {
            showHomeFragment()
        }
    }

    // 把程式碼中的變數跟 XML 裡的元件綁定在一起
    private fun bindViews() {
        navHome = findViewById(R.id.navHome)
        navAnalysis = findViewById(R.id.navAnalysis)
        navAdd = findViewById(R.id.navAdd)
        navAssets = findViewById(R.id.navAssets)
        navSettings = findViewById(R.id.navSettings)

        navHomeInner = findViewById(R.id.navHomeInner)
        navAnalysisInner = findViewById(R.id.navAnalysisInner)
        navAddInner = findViewById(R.id.navAddInner)
        navAssetsInner = findViewById(R.id.navAssetsInner)
        navSettingsInner = findViewById(R.id.navSettingsInner)

        bubbleHome = findViewById(R.id.bubbleHome)
        bubbleAnalysis = findViewById(R.id.bubbleAnalysis)
        bubbleAdd = findViewById(R.id.bubbleAdd)
        bubbleAssets = findViewById(R.id.bubbleAssets)
        bubbleSettings = findViewById(R.id.bubbleSettings)

        iconHome = findViewById(R.id.iconHome)
        iconAnalysis = findViewById(R.id.iconAnalysis)
        iconAdd = findViewById(R.id.iconAdd)
        iconAssets = findViewById(R.id.iconAssets)
        iconSettings = findViewById(R.id.iconSettings)

        textHome = findViewById(R.id.textHome)
        textAnalysis = findViewById(R.id.textAnalysis)
        textAdd = findViewById(R.id.textAdd)
        textAssets = findViewById(R.id.textAssets)
        textSettings = findViewById(R.id.textSettings)
    }

    // 設定底部導覽列的點擊事件
    private fun setupBottomNav() {
        // 點擊「首頁」按鈕 → 切換到首頁 Fragment
        navHome.setOnClickListener {
            replaceFragment(HomeFragment())
            selectBottomNav("home")  // 同時改變按鈕的外觀（選中狀態）
        }

        // 點擊「分析」按鈕 → 切換到分析頁面
        navAnalysis.setOnClickListener {
            replaceFragment(AnalysisFragment())
            selectBottomNav("analysis")
        }

        // 點擊「記帳」按鈕 → 切換到記帳頁面
        navAdd.setOnClickListener {
            replaceFragment(AddTransactionFragment())
            selectBottomNav("add")
        }

        // 點擊「資產」按鈕 → 切換到資產頁面
        navAssets.setOnClickListener {
            replaceFragment(AssetsFragment())
            selectBottomNav("assets")
        }

        // 點擊「設定」按鈕 → 切換到設定頁面
        navSettings.setOnClickListener {
            replaceFragment(SettingsFragment())
            selectBottomNav("settings")
        }
    }

    // 切換 Fragment（把中間的內容換掉）
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)  // 把 container 裡的內容換掉
            .commit()  // 執行更換
    }

    // 顯示首頁Fragment（從其他地方呼叫，例如登出後）
    fun showHomeFragment() {
        replaceFragment(HomeFragment())
        selectBottomNav("home")
    }

    // 顯示分析Fragment
    fun showAnalysisFragment() {
        replaceFragment(AnalysisFragment())
        selectBottomNav("analysis")
    }

    // 顯示記帳Fragment
    fun showAddTransactionFragment() {
        replaceFragment(AddTransactionFragment())
        selectBottomNav("add")
    }

    // 顯示資產Fragment
    fun showAssetsFragment() {
        replaceFragment(AssetsFragment())
        selectBottomNav("assets")
    }

    // 顯示設定Fragment
    fun showSettingsFragment() {
        replaceFragment(SettingsFragment())
        selectBottomNav("settings")
    }

    // 改變底部導覽列的外觀（哪個按鈕被選中）
    fun selectBottomNav(selected: String) {
        // 正常狀態是淺灰色，選中狀態是深褐色
        val normalColor = Color.parseColor("#9A8F87")    // 正常：暖灰色
        val selectedColor = Color.parseColor("#8C7461")  // 選中：深褐色

        // 先把所有按鈕恢復成「正常」狀態
        resetNavItem(navHomeInner, bubbleHome, iconHome, textHome, normalColor)
        resetNavItem(navAnalysisInner, bubbleAnalysis, iconAnalysis, textAnalysis, normalColor)
        resetNavItem(navAddInner, bubbleAdd, iconAdd, textAdd, normalColor)
        resetNavItem(navAssetsInner, bubbleAssets, iconAssets, textAssets, normalColor)
        resetNavItem(navSettingsInner, bubbleSettings, iconSettings, textSettings, normalColor)

        // 再把「被選中的」那個按鈕改成「選中」狀態
        when (selected) {
            "home" -> setSelectedNavItem(navHomeInner, bubbleHome, iconHome, textHome, selectedColor)
            "analysis" -> setSelectedNavItem(navAnalysisInner, bubbleAnalysis, iconAnalysis, textAnalysis, selectedColor)
            "add" -> setSelectedNavItem(navAddInner, bubbleAdd, iconAdd, textAdd, selectedColor)
            "assets" -> setSelectedNavItem(navAssetsInner, bubbleAssets, iconAssets, textAssets, selectedColor)
            "settings" -> setSelectedNavItem(navSettingsInner, bubbleSettings, iconSettings, textSettings, selectedColor)
        }
    }

    // 把一個導覽按鈕恢復成「未選中」的樣子
    private fun resetNavItem(
        container: View,   // 按鈕的內部容器
        bubble: View,      // 圓形背景
        icon: ImageView,   // 圖示
        text: TextView,    // 文字標籤
        color: Int         // 顏色（正常狀態用）
    ) {
        bubble.setBackgroundResource(android.R.color.transparent)  // 圓形背景變透明
        icon.setColorFilter(color)    // 圖示變成正常顏色
        text.setTextColor(color)      // 文字變成正常顏色
        container.alpha = 0.88f       // 稍微透明一點點（88% 不透明）
        container.scaleX = 1f         // 恢復原始寬度
        container.scaleY = 1f         // 恢復原始高度
    }

    // 把一個導覽按鈕設定成「選中」的樣子
    private fun setSelectedNavItem(
        container: View,
        bubble: View,
        icon: ImageView,
        text: TextView,
        color: Int
    ) {
        bubble.setBackgroundResource(R.drawable.bg_bottom_nav_selected_circle)  // 顯示圓形背景
        icon.setColorFilter(color)    // 圖示變成選中顏色
        text.setTextColor(color)      // 文字變成選中顏色
        container.alpha = 1f          // 完全不透明
        container.scaleX = 1.02f      // 稍微放大一點點（2%）
        container.scaleY = 1.02f      // 稍微放大一點點（2%）
    }
}