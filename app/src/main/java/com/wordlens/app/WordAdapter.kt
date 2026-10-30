package com.wordlens.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WordItem(
    val word: String,
    var bangla: String? = null,
    var synonyms: String? = null
)

class WordAdapter : RecyclerView.Adapter<WordAdapter.VH>() {

    private val items = mutableListOf<WordItem>()

    fun submit(list: List<WordItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun items(): List<WordItem> = items

    fun changed(index: Int) {
        if (index in items.indices) notifyItemChanged(index)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val word: TextView = view.findViewById(R.id.tvWord)
        val bangla: TextView = view.findViewById(R.id.tvBangla)
        val syn: TextView = view.findViewById(R.id.tvSyn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_word, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.word.text = item.word
        holder.bangla.text = item.bangla ?: "খুঁজছি…"
        holder.syn.text = if (item.synonyms == null) "" else "সমার্থক: " + item.synonyms
        holder.syn.visibility = if (item.synonyms == null) View.GONE else View.VISIBLE
    }

    override fun getItemCount(): Int = items.size
}
