package com.example.cococoin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// 分類明細列表的轉接器：
// 負責把 CategoryDetailItem 資料變成畫面上的一個個項目
class CategoryDetailAdapter(
    private val items: MutableList<CategoryDetailItem>  // 要顯示的資料清單
) : RecyclerView.Adapter<CategoryDetailAdapter.CategoryDetailViewHolder>() {

    // ViewHolder：持有畫面上的元件
    inner class CategoryDetailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDetailDate: TextView = itemView.findViewById(R.id.tvDetailDate)       // 日期
        val tvDetailTitle: TextView = itemView.findViewById(R.id.tvDetailTitle)     // 標題（備註或分類）
        val tvDetailAmount: TextView = itemView.findViewById(R.id.tvDetailAmount)   // 金額
    }

    // 建立 ViewHolder（載入 item_category_detail.xml 這個項目模板）
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryDetailViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_detail, parent, false)
        return CategoryDetailViewHolder(view)
    }

    // 總共有多少項目
    override fun getItemCount(): Int = items.size

    // 把資料綁定到 ViewHolder
    override fun onBindViewHolder(holder: CategoryDetailViewHolder, position: Int) {
        val item = items[position]
        holder.tvDetailDate.text = item.date           // 顯示日期
        holder.tvDetailTitle.text = item.noteOrCategory // 顯示備註或分類
        holder.tvDetailAmount.text = "NT$ ${item.amount}"  // 顯示金額
    }

    // 更新資料（外部呼叫時用）
    fun updateData(newItems: List<CategoryDetailItem>) {
        items.clear()           // 清空舊資料
        items.addAll(newItems)  // 加入新資料
        notifyDataSetChanged()  // 通知 RecyclerView 重新整理
    }
}