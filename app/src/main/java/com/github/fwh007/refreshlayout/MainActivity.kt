package com.github.fwh007.refreshlayout

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = MyAdapter()

        refresh_layout.setOnRefreshListener(object : CustomRefreshLayout.OnRefreshListener {
            override fun onRefresh() {
                Thread {
                    Thread.sleep(3000)
                    runOnUiThread {
                        refresh_layout.setRefreshing(false)
                    }
                }.start()
            }

        })
    }

    private inner class MyAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
//            return if (position == 0) 0 else 1
            return 1
        }

        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                0 ->
                    HeaderViewHolder(layoutInflater.inflate(R.layout.activity_main_header, parent, false))
                1 ->
                    ItemViewHolder(layoutInflater.inflate(R.layout.activity_main_item, parent, false))
                else ->
                    throw RuntimeException()
            }
        }

        override fun getItemCount(): Int {
            return 30
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is ItemViewHolder) {
                holder.textView.text = "out ${position}"
            }
        }

    }

    private class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    }

    private class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView as TextView
    }
}
