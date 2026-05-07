// ============================================================
// 📋 每日明細列表的轉接器 — 白話文：交易明細的「排版印刷機」
// ============================================================
//
// 情境劇：想像你有一疊「交易小卡片」（DailyDetailItem），想要把它們貼到一面展示牆上
//         這個 Adapter 就是那個負責「排版」的人：
//           1. 接收一堆 DailyDetailItem 資料
//           2. 把它們一張一張印出來（變成 ViewHolder）
//           3. 決定每張卡片的「收入」要綠色、「支出」要紅色
//           4. 金額前面加上 + 或 - 號
//
// RecyclerView.Adapter 是 Android 的標準元件
// 白話文：它是「資料」和「畫面」之間的橋樑，負責把資料轉換成顯示項目
// ============================================================
package com.example.cococoin

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// 每日明細列表的轉接器
// items 參數：要顯示的資料清單（MutableList 表示可以修改，例如清空、新增）
class DailyDetailAdapter(
    private val items: MutableList<DailyDetailItem>
) : RecyclerView.Adapter<DailyDetailAdapter.DailyDetailViewHolder>() {

    // ============================================================
    // 🖼️ ViewHolder — 白話文：一個空白的「卡片框架」
    // ============================================================
    // ViewHolder 就像一個「空白的相框」，等著你把資料填進去
    // inner class 表示這個類別跟外面的 DailyDetailAdapter 有關係（可以存取它的東西）
    inner class DailyDetailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // 從 item_daily_detail.xml 設計圖裡找出三個需要填資料的欄位
        val tvDailyDetailTitle: TextView = itemView.findViewById(R.id.tvDailyDetailTitle)    // 📌 標題（例如「餐飲」）
        val tvDailyDetailSub: TextView = itemView.findViewById(R.id.tvDailyDetailSub)        // 📝 副標題（例如「支出 ・ 現金」）
        val tvDailyDetailAmount: TextView = itemView.findViewById(R.id.tvDailyDetailAmount)  // 💰 金額（例如「-NT$ 100」）
    }

    // ============================================================
    // 🏭 建立 ViewHolder — 白話文：製造一個空白的「卡片框架」
    // ============================================================
    // 這個函式會在需要新的卡片時被呼叫
    // 它會讀取 item_daily_detail.xml 這個設計圖，把它變成真正的 View 物件
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DailyDetailViewHolder {
        // LayoutInflater 就像一個「3D 列印機」，把 XML 設計圖變成實際的畫面
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_daily_detail, parent, false)
        return DailyDetailViewHolder(view)  // 把印好的空框架包裝成 ViewHolder 回傳
    }

    // ============================================================
    // 🔢 總共有多少項目 — 白話文：牆上要貼幾張卡片？
    // ============================================================
    override fun getItemCount(): Int = items.size

    // ============================================================
    // 🔗 把資料綁定到 ViewHolder — 白話文：把內容填進空白的相框裡
    // ============================================================
    // 這個函式會在每個項目顯示到畫面上時被呼叫
    // position 表示要處理第幾個位置的卡片（0, 1, 2, 3...）
    override fun onBindViewHolder(holder: DailyDetailViewHolder, position: Int) {
        // 從清單中拿出這個位置對應的資料
        val item = items[position]

        // ----- 填寫基本資料 -----
        holder.tvDailyDetailTitle.text = item.title      // 標題：「餐飲」
        holder.tvDailyDetailSub.text = item.subTitle     // 副標題：「支出 ・ 現金」

        // ----- 根據類型（收入/支出）決定顏色和符號 -----
        // 就像「收入用綠色笑臉，支出用紅色哭臉」的概念
        if (item.type == "收入") {
            // 💚 收入：綠色系，金額前面加 + 號
            holder.tvDailyDetailAmount.text = "+NT$ ${item.amount}"
            holder.tvDailyDetailAmount.setTextColor(Color.parseColor("#2E7D32"))  // 深綠色（代表賺錢的愉悅感）
        } else {
            // ❤️ 支出：紅色系，金額前面加 - 號
            holder.tvDailyDetailAmount.text = "-NT$ ${item.amount}"
            holder.tvDailyDetailAmount.setTextColor(Color.parseColor("#C62828"))  // 深紅色（代表花錢的心痛感）
        }
    }

    // ============================================================
    // 🔄 更新資料 — 白話文：換一批新的卡片上牆
    // ============================================================
    // 當使用者點擊不同日期時，會呼叫這個函式來更換顯示的內容
    fun updateData(newItems: List<DailyDetailItem>) {
        items.clear()           // 把舊的卡片全部撕掉（清空）
        items.addAll(newItems)  // 貼上新的卡片（加入新資料）
        notifyDataSetChanged()  // 通知 RecyclerView：「牆上的內容變了，請重新顯示！」
    }
}