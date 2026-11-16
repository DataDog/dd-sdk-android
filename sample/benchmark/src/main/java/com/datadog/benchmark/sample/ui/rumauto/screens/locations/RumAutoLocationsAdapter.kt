/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.locations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.datadog.sample.benchmark.databinding.ItemLocationBinding

internal class RumAutoLocationsAdapter(
    private val onLocationClicked: (Int) -> Unit
) : ListAdapter<RumAutoLocationItem, RumAutoLocationsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onLocationClicked)
    }

    internal class ViewHolder(private val binding: ItemLocationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RumAutoLocationItem, onLocationClicked: (Int) -> Unit) {
            binding.locationTitle.text = item.title
            binding.locationFirstSubtitle.text = item.firstSubtitle
            binding.locationSecondSubtitle.text = item.secondSubtitle
            binding.root.setOnClickListener { onLocationClicked(item.locationId) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<RumAutoLocationItem>() {
        override fun areItemsTheSame(oldItem: RumAutoLocationItem, newItem: RumAutoLocationItem): Boolean =
            oldItem.locationId == newItem.locationId

        override fun areContentsTheSame(oldItem: RumAutoLocationItem, newItem: RumAutoLocationItem): Boolean =
            oldItem == newItem
    }
}
