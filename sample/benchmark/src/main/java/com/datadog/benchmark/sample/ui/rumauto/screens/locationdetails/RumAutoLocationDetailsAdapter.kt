/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.locationdetails

import com.datadog.benchmark.sample.ui.rumauto.screens.common.details.characterItemDelegate
import com.datadog.benchmark.sample.utils.recycler.BaseRecyclerViewItem
import com.hannesdorfmann.adapterdelegates4.ListDelegationAdapter
import javax.inject.Inject

internal class RumAutoLocationDetailsAdapter @Inject constructor(
    private val viewModel: RumAutoLocationDetailsViewModel
) : ListDelegationAdapter<List<BaseRecyclerViewItem>>() {

    init {
        delegatesManager.apply {
            addDelegate(
                characterItemDelegate { viewModel.dispatch(RumAutoLocationDetailsAction.OnCharacterClicked(it)) }
            )
        }
    }
}
