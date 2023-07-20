/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tv.sample

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.datadog.android.tv.sample.model.Episode

class EpisodeRecyclerView private constructor() {

    class Adapter(
        val onEpisodeSelected: (Episode?, Int) -> Unit
    ) : RecyclerView.Adapter<ViewHolder>() {

        private val data: MutableList<Episode> = mutableListOf()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemView = LayoutInflater.from(parent.context).inflate(
                R.layout.episode_item,
                parent,
                false
            )
            return ViewHolder(itemView, onEpisodeSelected)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.render(data[position])
        }

        override fun getItemCount(): Int {
            return data.size
        }

        @SuppressLint("NotifyDataSetChanged")
        internal fun updateData(newData: List<Episode>) {
            data.clear()
            data.addAll(newData)
            notifyDataSetChanged()
            onEpisodeSelected(null, -1)
        }
    }

    class ViewHolder(
        itemView: View,
        onEpisodeSelected: (Episode, Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        lateinit var episode: Episode

        val textView = itemView.findViewById<TextView>(R.id.title)

        init {
            itemView.setOnClickListener {
                updateEpisodeHighlight(true)
                onEpisodeSelected(episode, 0)
            }
            itemView.setOnFocusChangeListener { view, b ->
                updateEpisodeHighlight(b)
                onEpisodeSelected(episode, 0)
            }
        }

        private fun updateEpisodeHighlight(selected: Boolean) {
            val color = if (selected) R.color.dd_purple_200 else R.color.text_default
            textView.setTextColor(textView.resources.getColor(color, null))
        }

        fun render(episode: Episode) {
            this.episode = episode
            textView.text = episode.title
        }
    }
}
