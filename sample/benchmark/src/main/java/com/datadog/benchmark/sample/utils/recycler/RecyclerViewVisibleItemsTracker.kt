/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.utils.recycler

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.ListDelegationAdapter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy

internal fun <T : BaseRecyclerViewItem, A : ListDelegationAdapter<List<T>>> trackRecyclerViewVisibleItems(
    recyclerView: RecyclerView,
    layoutManager: LinearLayoutManager,
    adapter: A
): Flow<List<T>> {
    fun getVisibleItems(): List<T> {
        val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
        val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

        val result = (firstVisibleItem..lastVisibleItem).mapNotNull { itemIndex ->
            adapter.items?.getOrNull(itemIndex)
        }

        return result
    }

    return channelFlow {
        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                trySend(getVisibleItems())
            }
        }

        recyclerView.addOnScrollListener(scrollListener)

        trySend(getVisibleItems())

        awaitClose {
            recyclerView.removeOnScrollListener(scrollListener)
        }
    }.distinctUntilChangedBy { items -> items.map { it.key } }
}
