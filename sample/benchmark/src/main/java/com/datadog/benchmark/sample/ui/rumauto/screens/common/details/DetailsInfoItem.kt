/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.common.details

import com.datadog.benchmark.sample.utils.recycler.BaseRecyclerViewItem
import com.datadog.sample.benchmark.databinding.ItemDetailsInfoBinding
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding

internal data class DetailsInfoItem(
    val startText: String,
    val endText: String,
    override val key: String
) : BaseRecyclerViewItem

internal fun detailsInfoItemDelegate() = adapterDelegateViewBinding<DetailsInfoItem, BaseRecyclerViewItem, ItemDetailsInfoBinding>(
    { layoutInflater, root -> ItemDetailsInfoBinding.inflate(layoutInflater, root, false) }
) {
    bind {
        binding.startText.text = item.startText
        binding.endText.text = item.endText
    }
}
