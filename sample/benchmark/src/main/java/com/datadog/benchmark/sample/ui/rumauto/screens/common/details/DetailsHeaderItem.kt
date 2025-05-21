/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.common.details

import com.datadog.sample.benchmark.databinding.ItemDetailsHeaderBinding
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding

internal data class DetailsHeaderItem(
    val text: String,
)

internal fun detailsHeaderItemDelegate() = adapterDelegateViewBinding<DetailsHeaderItem, Any, ItemDetailsHeaderBinding>(
    { layoutInflater, root -> ItemDetailsHeaderBinding.inflate(layoutInflater, root, false) }
) {
    bind {
        binding.itemDetailsHeaderText.text = item.text
    }
}
