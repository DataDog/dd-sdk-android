/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.locations

import com.datadog.benchmark.sample.utils.recycler.BaseRecyclerViewItem
import com.datadog.sample.benchmark.databinding.ItemLocationBinding
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding

internal data class RumAutoLocationItem(
    val title: String,
    val firstSubtitle: String,
    val secondSubtitle: String,
    val locationId: Int,
    override val key: String
): BaseRecyclerViewItem

internal fun rumAutoLocationDelegate(
    onLocationClicked: (Int) -> Unit
) = adapterDelegateViewBinding<RumAutoLocationItem, BaseRecyclerViewItem, ItemLocationBinding>(
    { layoutInflater, root -> ItemLocationBinding.inflate(layoutInflater, root, false) }
) {
    bind {
        binding.locationTitle.text = item.title
        binding.locationFirstSubtitle.text = item.firstSubtitle
        binding.locationSecondSubtitle.text = item.secondSubtitle
        binding.root.setOnClickListener {
            onLocationClicked(item.locationId)
        }
    }
}
