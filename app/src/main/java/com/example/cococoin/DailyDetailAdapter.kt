package com.example.cococoin

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// 每日明細列表的轉接器：
// 負責把 DailyDetailItem 資料變成畫面上的一個個項目
class DailyDetailAdapter(
    private val items: MutableList<DailyDetailItem>  // 要顯示的資料清單
) : RecyclerView.Adapter<DailyDetailAdapter.DailyDetailViewHolder>() {

    // ViewHolder：持有畫面上的元件，避免重複 findViewById
    inner class DailyDetailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDailyDetailTitle: TextView = itemView.findViewById(R.id.tvDailyDetailTitle)    // 標題
        val tvDailyDetailSub: TextView = itemView.findViewById(R.id.tvDailyDetailSub)        // 副標題
        val tvDailyDetailAmount: TextView = itemView.findViewById(R.id.tvDailyDetailAmount)  // 金額
    }

    // 建立 ViewHolder（載入 item_daily_detail.xml 這個項目模板）
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DailyDetailViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_daily_detail, parent, false)
        return DailyDetailViewHolder(view)
    }

    // 總共有多少項目
    override fun getItemCount(): Int = items.size

    // 把資料綁定到 ViewHolder
    override fun onBindViewHolder(holder: DailyDetailViewHolder, position: Int) {
        val item = items[position]

        // 設定標題和副標題
        holder.tvDailyDetailTitle.text = item.title
        holder.tvDailyDetailSub.text = item.subTitle

        // 根據類型設定金額顏色和符號
        if (item.type == "收入") {
            holder.tvDailyDetailAmount.text = "+NT$ ${item.amount}"   // 收入前面加 +
            holder.tvDailyDetailAmount.setTextColor(Color.parseColor("#2E7D32"))  // 綠色
        } else {
            holder.tvDailyDetailAmount.text = "-NT$ ${item.amount}"   // 支出前面加 -
            holder.tvDailyDetailAmount.setTextColor(Color.parseColor("#C62828"))  // 紅色
        }
    }

    // 更新資料（外部呼叫時用）
    fun updateData(newItems: List<DailyDetailItem>) {
        items.clear()           // 清空舊資料
        items.addAll(newItems)  // 加入新資料
        notifyDataSetChanged()  // 通知 RecyclerView 重新整理
    }
}