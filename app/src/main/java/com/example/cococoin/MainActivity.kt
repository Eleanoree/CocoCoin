package com.example.cococoin

import android.graphics.Color           // 這裡是調色盤，用來幫按鈕化妝（變顏色）
import android.os.Bundle                // 像一個快遞包裹，用來在 Activity 之間送資料
import android.view.View                // 畫面上所有東西（按鈕、文字）的爸爸級基礎類別
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity  // 所有 App 畫面的始祖（有了它，這個畫面才能活起來）
import androidx.fragment.app.Fragment

// 👑【總指揮中心】App 的主畫面，底下有五個按鈕（首頁、分析、記帳、資產、設定）
// 就像一台電視的遙控器 + 螢幕，按下面按鈕，上面螢幕（fragmentContainer）就會切換內容
class MainActivity : AppCompatActivity() {

    // ---------- 🎮 遙控器按鈕（五個大按鈕區域）----------
    // 這些是「按鈕的外殼」，就是你在畫面上可以戳的那一整塊區域
    private lateinit var navHome: View      // 首頁按鈕區（左上區）
    private lateinit var navAnalysis: View  // 分析按鈕區（中左區）
    private lateinit var navAdd: View       // 記帳按鈕區（中間那顆 C 位按鈕）
    private lateinit var navAssets: View    // 資產按鈕區（中右區）
    private lateinit var navSettings: View  // 設定按鈕區（右下區）

    // ---------- 🧸 按鈕的內部小機關（拿來做動畫、縮放，讓點擊更有感）----------
    // 想像成按鈕裡面的「內膽」，負責被點擊時的放大縮小感覺
    private lateinit var navHomeInner: View
    private lateinit var navAnalysisInner: View
    private lateinit var navAddInner: View
    private lateinit var navAssetsInner: View
    private lateinit var navSettingsInner: View

    // ---------- 🔵 圓形氣泡背景（選中按鈕時，後面會浮現一個圓圈）----------
    // 就像漫畫裡角色生氣時背後會出現火焰，這裡是「被選中」時背後會出現圓形光環
    private lateinit var bubbleHome: View
    private lateinit var bubbleAnalysis: View
    private lateinit var bubbleAdd: View
    private lateinit var bubbleAssets: View
    private lateinit var bubbleSettings: View

    // ---------- 🖼️ 圖示（ImageView）----------
    // 每個按鈕上的小圖案，比如房子（首頁）、圖表（分析）
    private lateinit var iconHome: ImageView
    private lateinit var iconAnalysis: ImageView
    private lateinit var iconAdd: ImageView
    private lateinit var iconAssets: ImageView
    private lateinit var iconSettings: ImageView

    // ---------- 📝 文字標籤----------
    // 每個按鈕下方的字：「首頁」、「分析」…
    private lateinit var textHome: TextView
    private lateinit var textAnalysis: TextView
    private lateinit var textAdd: TextView
    private lateinit var textAssets: TextView
    private lateinit var textSettings: TextView

    // 🎬 生命週期：當這個畫面「第一次被創造出來」的時候，會自動呼叫這裡
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)   // 載入設計好的畫面（就像鋪好桌布）

        bindViews()      // 把程式裡的變數跟 XML 裡的元件「牽紅線」綁定
        setupBottomNav() // 設定按鈕們的「點擊反應」（告訴它們：被戳了要幹嘛）

        // 如果是第一次打開 App（沒有儲存過的狀態），預設就要顯示首頁
        // 就像是打開電視，預設會轉到第 1 台（首頁）
        if (savedInstanceState == null) {
            showHomeFragment()
        }
    }

    // 🔗 綁定儀式：讓程式碼裡的變數（ex: navHome）真的指到螢幕上的那個按鈕
    // 白話版：給每個綽號配一個真人
    private fun bindViews() {
        // 大按鈕區域
        navHome = findViewById(R.id.navHome)
        navAnalysis = findViewById(R.id.navAnalysis)
        navAdd = findViewById(R.id.navAdd)
        navAssets = findViewById(R.id.navAssets)
        navSettings = findViewById(R.id.navSettings)

        // 內膽機關
        navHomeInner = findViewById(R.id.navHomeInner)
        navAnalysisInner = findViewById(R.id.navAnalysisInner)
        navAddInner = findViewById(R.id.navAddInner)
        navAssetsInner = findViewById(R.id.navAssetsInner)
        navSettingsInner = findViewById(R.id.navSettingsInner)

        // 圓形氣泡背景
        bubbleHome = findViewById(R.id.bubbleHome)
        bubbleAnalysis = findViewById(R.id.bubbleAnalysis)
        bubbleAdd = findViewById(R.id.bubbleAdd)
        bubbleAssets = findViewById(R.id.bubbleAssets)
        bubbleSettings = findViewById(R.id.bubbleSettings)

        // 圖示（Icon）
        iconHome = findViewById(R.id.iconHome)
        iconAnalysis = findViewById(R.id.iconAnalysis)
        iconAdd = findViewById(R.id.iconAdd)
        iconAssets = findViewById(R.id.iconAssets)
        iconSettings = findViewById(R.id.iconSettings)

        // 文字標籤
        textHome = findViewById(R.id.textHome)
        textAnalysis = findViewById(R.id.textAnalysis)
        textAdd = findViewById(R.id.textAdd)
        textAssets = findViewById(R.id.textAssets)
        textSettings = findViewById(R.id.textSettings)
    }

    // 🎯 設定底部導覽列：告訴每個按鈕「被點擊時，該切換到哪個頁面（Fragment）」
    // 白話版：把遙控器每個按鈕的「頻道」設定好
    private fun setupBottomNav() {
        // 點擊「首頁」按鈕 → 切換到首頁畫面，並且把按鈕變成「選中」的帥氣模樣
        navHome.setOnClickListener {
            replaceFragment(HomeFragment())
            selectBottomNav("home")  // 讓「首頁」按鈕亮起來，其他變暗
        }

        // 點擊「分析」按鈕 → 切換到分析頁面（看花了多少錢，長條圖圓餅圖那種）
        navAnalysis.setOnClickListener {
            replaceFragment(AnalysisFragment())
            selectBottomNav("analysis")
        }

        // 點擊「記帳」按鈕 → 切換到記帳頁面（輸入：午餐 150 元）
        navAdd.setOnClickListener {
            replaceFragment(AddTransactionFragment())
            selectBottomNav("add")
        }

        // 點擊「資產」按鈕 → 切換到資產頁面（看戶頭還有多少錢、存錢目標）
        navAssets.setOnClickListener {
            replaceFragment(AssetsFragment())
            selectBottomNav("assets")
        }

        // 點擊「設定」按鈕 → 切換到設定頁面（改幣別、備份、換主題之類的）
        navSettings.setOnClickListener {
            replaceFragment(SettingsFragment())
            selectBottomNav("settings")
        }
    }

    // 🧩 切換 Fragment（把中間的「動態內容區」整個換掉）
    // 白話版：把電視的「螢幕內容」從新聞台轉到電影台
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)  // 在 fragmentContainer 這個空盒子裡，放進新的 fragment
            .commit()  // 確定執行！立刻生效！
    }

    // ---------- 🚀 公開的跳轉方法（讓其他程式碼可以呼叫）----------
    // 這些方法就像是「遙控器上的快捷鍵」，別人可以直接叫你轉台

    // 顯示首頁 Fragment（例如登出後回到首頁）
    fun showHomeFragment() {
        replaceFragment(HomeFragment())
        selectBottomNav("home")
    }

    fun showAnalysisFragment() {
        replaceFragment(AnalysisFragment())
        selectBottomNav("analysis")
    }

    fun showAddTransactionFragment() {
        replaceFragment(AddTransactionFragment())
        selectBottomNav("add")
    }

    fun showAssetsFragment() {
        replaceFragment(AssetsFragment())
        selectBottomNav("assets")
    }

    fun showSettingsFragment() {
        replaceFragment(SettingsFragment())
        selectBottomNav("settings")
    }

    // ✨ 改變底部導覽列的外觀（哪一個按鈕看起來是「被選中」的）
    // 白話版：讓某個按鈕「發光、放大一點點」，其他按鈕「縮回去、變灰」
    fun selectBottomNav(selected: String) {
        // 正常狀態是「暖灰色」（沒被選中時的素顏模樣）
        // 選中狀態是「深褐色」（被選中時的美肌模式）
        val normalColor = Color.parseColor("#9A8F87")    // 正常：暖灰色（低調）
        val selectedColor = Color.parseColor("#8C7461")  // 選中：深褐色（主角光環）

        // 👇 第一步：把所有按鈕恢復成「正常、沒人選中」的樣子
        resetNavItem(navHomeInner, bubbleHome, iconHome, textHome, normalColor)
        resetNavItem(navAnalysisInner, bubbleAnalysis, iconAnalysis, textAnalysis, normalColor)
        resetNavItem(navAddInner, bubbleAdd, iconAdd, textAdd, normalColor)
        resetNavItem(navAssetsInner, bubbleAssets, iconAssets, textAssets, normalColor)
        resetNavItem(navSettingsInner, bubbleSettings, iconSettings, textSettings, normalColor)

        // 👇 第二步：根據「selected」這個參數，只讓對應的那個按鈕穿上「選中」的華麗服裝
        when (selected) {
            "home" -> setSelectedNavItem(navHomeInner, bubbleHome, iconHome, textHome, selectedColor)
            "analysis" -> setSelectedNavItem(navAnalysisInner, bubbleAnalysis, iconAnalysis, textAnalysis, selectedColor)
            "add" -> setSelectedNavItem(navAddInner, bubbleAdd, iconAdd, textAdd, selectedColor)
            "assets" -> setSelectedNavItem(navAssetsInner, bubbleAssets, iconAssets, textAssets, selectedColor)
            "settings" -> setSelectedNavItem(navSettingsInner, bubbleSettings, iconSettings, textSettings, selectedColor)
        }
    }

    // 🔄 把一個導覽按鈕恢復成「未選中」的平凡路人模樣
    // 白話：卸妝、縮小、變透明氣泡
    private fun resetNavItem(
        container: View,   // 按鈕的內部容器（內膽）
        bubble: View,      // 圓形背景（光環）
        icon: ImageView,   // 圖示
        text: TextView,    // 文字
        color: Int         // 要給它塗的顏色（通常是灰灰的）
    ) {
        bubble.setBackgroundResource(android.R.color.transparent)  // 圓形背景變透明（光環消失）
        icon.setColorFilter(color)    // 圖示變成普通的顏色（沒被選中）
        text.setTextColor(color)      // 文字也變成普通顏色
        container.alpha = 0.88f       // 整體變得稍微透明一點點（88%，低調）
        container.scaleX = 1f         // 寬度恢復原狀（不要大隻）
        container.scaleY = 1f         // 高度恢復原狀
    }

    // ✨ 把一個導覽按鈕設定成「選中」的巨星模樣
    // 白話：人生勝利組模式 — 背後有光環、字變深色、微微放大、完全不透明
    private fun setSelectedNavItem(
        container: View,
        bubble: View,
        icon: ImageView,
        text: TextView,
        color: Int
    ) {
        bubble.setBackgroundResource(R.drawable.bg_bottom_nav_selected_circle)  // 圓形背景出現！就像超級賽亞人的氣場
        icon.setColorFilter(color)    // 圖示變成深褐色（醒目）
        text.setTextColor(color)      // 文字也變成深褐色
        container.alpha = 1f          // 完全不透明（自信滿滿）
        container.scaleX = 1.02f      // 寬度放大 2%（微微凸顯）
        container.scaleY = 1.02f      // 高度放大 2%
    }
}