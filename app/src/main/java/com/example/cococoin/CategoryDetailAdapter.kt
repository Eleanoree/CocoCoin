package com.example.cococoin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 🔄【分類明細列表的轉接器】
 *
 * 想像你要展示一疊「帳單明細」給客戶看：
 *
 * ┌─────────────────────────────────────┐
 * │ 📅 2026/04/07                       │
 * │ 📝 跟同事吃午餐        💰 -150      │
 * ├─────────────────────────────────────┤
 * │ 📅 2026/04/07                       │
 * │ 📝 搭捷運              💰 -30       │
 * ├─────────────────────────────────────┤
 * │ 📅 2026/04/06                       │
 * │ 📝 買咖啡              💰 -120      │
 * └─────────────────────────────────────┘
 *
 * RecyclerView 就像一個「自動回收再利用的列表」
 * Adapter 就像「裝飾師」，負責：
 * 1. 決定每個項目的長相（onCreateViewHolder）
 * 2. 把資料填到項目上（onBindViewHolder）
 * 3. 告訴列表總共有幾個項目（getItemCount）
 *
 * 為什麼叫 RecyclerView？（回收視圖）
 * - 當你滑動列表，超出螢幕的項目不會被銷毀
 * - 它們會被「回收」再利用，給新出現的資料使用
 * - 這樣很省記憶體，滑 1000 筆也不會當機！
 *
 * @param items 要顯示的資料清單（可變列表，因為可以更新）
 */
class CategoryDetailAdapter(
    private val items: MutableList<CategoryDetailItem>  // 📋 帳單明細資料
) : RecyclerView.Adapter<CategoryDetailAdapter.CategoryDetailViewHolder>() {

    /**
     * 🏷️【ViewHolder - 項目的收納盒】
     *
     * ViewHolder 就像一個「收納盒」，裡面放著一個項目所有的「View」
     * 這樣就不用每次重新找（findViewById），直接拿出來用就好～
     *
     * @property tvDetailDate 顯示日期（例如「2026/04/07」）
     * @property tvDetailTitle 顯示標題（備註或分類）
     * @property tvDetailAmount 顯示金額（NT$ -150）
     */
    inner class CategoryDetailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDetailDate: TextView = itemView.findViewById(R.id.tvDetailDate)       // 📅 日期
        val tvDetailTitle: TextView = itemView.findViewById(R.id.tvDetailTitle)     // 📝 標題
        val tvDetailAmount: TextView = itemView.findViewById(R.id.tvDetailAmount)   // 💰 金額
    }

    /**
     * 🏭 建立 ViewHolder（製作收納盒）
     *
     * 這個方法會在「需要新的項目時」被呼叫
     * 就像工廠生產線，需要新的零件時就生產一個
     *
     * 製作流程：
     * 1. 讀取設計圖（item_category_detail.xml）
     * 2. 把設計圖變成真正的 View（inflate）
     * 3. 放進收納盒（ViewHolder）
     * 4. 傳回去給 RecyclerView
     *
     * @param parent 父容器（RecyclerView 自己）
     * @param viewType 項目類型（如果有不同樣式可以用來區分）
     * @return 全新的 ViewHolder
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryDetailViewHolder {
        // 🔨 把 XML 設計圖變成實際的 View
        // LayoutInflater 就像「3D 列印機」，把設計圖印出來
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_detail, parent, false)  // false = 不要自動加入到 parent

        // 📦 放進收納盒，準備出貨
        return CategoryDetailViewHolder(view)
    }

    /**
     * 🔢 總共有多少項目
     *
     * RecyclerView 會問：「嘿，總共有幾筆資料要顯示啊？」
     *
     * @return 資料清單的長度（例如：items.size = 3）
     */
    override fun getItemCount(): Int = items.size

    /**
     * 🖌️ 把資料綁定到 ViewHolder（把內容寫上去）
     *
     * 這個方法會在「項目即將顯示在螢幕上」時被呼叫
     *
     * 流程：
     * 1. RecyclerView 拿出一個收納盒（ViewHolder）
     * 2. 根據 position 找到對應的資料（items[position]）
     * 3. 把資料塞進收納盒的每個格子裡
     * 4. 完成！使用者看到畫面了～
     *
     * @param holder 收納盒（裡面有 TextView 們）
     * @param position 這是第幾個項目（0, 1, 2, ...）
     */
    override fun onBindViewHolder(holder: CategoryDetailViewHolder, position: Int) {
        // 📋 取出對應位置的資料
        val item = items[position]

        // ✨ 把資料填到畫面上
        holder.tvDetailDate.text = item.date                      // 日期：2026/04/07
        holder.tvDetailTitle.text = item.noteOrCategory           // 標題：跟同事吃午餐
        holder.tvDetailAmount.text = "NT$ ${item.amount}"        // 金額：NT$ -150
    }

    /**
     * 🔄 更新資料（外部呼叫時用）
     *
     * 白話：換一批新的資料顯示
     *
     * 使用情境：
     * - 使用者點擊不同的分類（餐飲 → 交通）
     * - 使用者切換月份（四月 → 五月）
     * - 使用者新增/刪除交易後
     *
     * 流程：
     * 1. 清空舊資料
     * 2. 加入新資料
     * 3. 通知 RecyclerView：「資料變了，請重新整理」
     *
     * @param newItems 新的資料清單
     */
    fun updateData(newItems: List<CategoryDetailItem>) {
        items.clear()           // 🗑️ 清空舊資料
        items.addAll(newItems)  // 📥 加入新資料

        // 📢 廣播：資料變了！
        // notifyDataSetChanged 就像對 RecyclerView 大喊：
        // 「嘿！資料完全不一樣了，全部重畫吧！」
        notifyDataSetChanged()

        // 💡 更高效的做法（進階）：
        // 如果只是新增一筆，可以用 notifyItemInserted(position)
        // 如果只是刪除一筆，可以用 notifyItemRemoved(position)
        // 這樣會有動畫效果，體驗更好～
        // 但為了簡單，這裡用全量刷新
    }
}

/**
 * 📚【RecyclerView 小教室 - 為什麼這麼設計？】
 *
 * 古老作法（ListView）：
 * - 滑動 1000 筆：建立 1000 個 View
 * - 記憶體爆炸 💥 手機變慢
 *
 * RecyclerView 的聰明做法：
 * ┌─────────────────────────────────────────────┐
 * │ 螢幕可以看到的項目：5 個                      │
 * │ 實際建立的 ViewHolder：5 + 預留 2 = 7 個     │
 * │                                              │
 * │ 當你往下滑：                                  │
 * │ - 項目 0 滑出螢幕 → 被回收                   │
 * │ - 項目 5 滑進螢幕 → 拿回收的 ViewHolder 重複使用 │
 * │                                              │
 * │ 這樣滑 100000 筆也不會記憶體爆炸！            │
 * └─────────────────────────────────────────────┘
 *
 * 這就是「回收」（Recycle）的意義！
 */

/**
 * 🎭【Adapter 完整運作流程 - 劇場版】
 *
 * 時間：使用者打開「餐飲」分類明細
 *
 * 第 1 幕：建立 Adapter
 *    val adapter = CategoryDetailAdapter(mutableListOf())
 *    rvCategoryDetail.adapter = adapter
 *    ↓
 * 第 2 幕：處理資料（從 ViewModel 或 Repository 取得）
 *    val items = listOf(
 *        CategoryDetailItem("2026/04/07", "午餐", -150),
 *        CategoryDetailItem("2026/04/06", "餐飲", -120),
 *        CategoryDetailItem("2026/04/05", "晚餐", -200)
 *    )
 *    ↓
 * 第 3 幕：更新資料
 *    adapter.updateData(items)
 *    ↓
 * 第 4 幕：RecyclerView 開始運作
 *    ├─ getItemCount() → 3（RecyclerView：喔，總共 3 筆）
 *    ├─ onCreateViewHolder() → 製造 2 個 ViewHolder 放著待命
 *    ├─ onBindViewHolder(holder, 0) → 顯示「午餐 -150」
 *    ├─ onBindViewHolder(holder, 1) → 顯示「餐飲 -120」
 *    └─ onBindViewHolder(holder, 2) → 顯示「晚餐 -200」
 *    ↓
 * 第 5 幕：使用者看到畫面 ✨
 *    ↓
 * 第 6 幕：使用者滑動列表
 *    ├─ 項目 0 滑出去 → 回收 ViewHolder
 *    └─ 項目 3 滑進來 → 用回收的 ViewHolder 顯示新資料
 */