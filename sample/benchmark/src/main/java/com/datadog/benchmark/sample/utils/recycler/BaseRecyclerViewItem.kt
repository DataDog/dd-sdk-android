/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.utils.recycler

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil
import com.hannesdorfmann.adapterdelegates4.ListDelegationAdapter

internal interface BaseRecyclerViewItem {
    val key: String
}

internal fun calculateRvDiff(
    oldList: List<BaseRecyclerViewItem>,
    newList: List<BaseRecyclerViewItem>,
    detectMoves: Boolean = false
): DiffUtil.DiffResult {
    val callback = object: DiffUtil.Callback() {
        override fun getOldListSize(): Int {
            return oldList.size
        }

        override fun getNewListSize(): Int {
            return newList.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].key == newList[newItemPosition].key
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    return DiffUtil.calculateDiff(callback, detectMoves)
}

@SuppressLint("NotifyDataSetChanged")
internal fun ListDelegationAdapter<List<BaseRecyclerViewItem>>.applyNewItems(newItems: List<BaseRecyclerViewItem>) {
    val itemsImpl = items
    if (itemsImpl == null) {
        items = newItems
        notifyDataSetChanged()
    } else {
        val diff = calculateRvDiff(itemsImpl, newItems)
        items = newItems
        diff.dispatchUpdatesTo(this)
    }
}
