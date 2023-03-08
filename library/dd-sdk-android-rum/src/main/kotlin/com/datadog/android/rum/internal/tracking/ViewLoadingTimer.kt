/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.tracking

import java.util.WeakHashMap

internal class ViewLoadingTimer {
    private val viewsTimeAndState = WeakHashMap<Any, ViewLoadingInfo>()

    fun onCreated(view: Any) {
        viewsTimeAndState[view] = ViewLoadingInfo(System.nanoTime())
    }

    fun onStartLoading(view: Any) {
        val viewLoadingInfo = if (viewsTimeAndState.containsKey(view)) {
            viewsTimeAndState[view]
        } else {
            ViewLoadingInfo(System.nanoTime()).also { viewsTimeAndState[view] = it }
        }
        viewLoadingInfo?.let {
            if (it.loadingStart == null) {
                it.loadingStart = System.nanoTime()
            }
        }
    }

    fun onFinishedLoading(view: Any) {
        viewsTimeAndState[view]?.let {
            val loadingStart = it.loadingStart
            it.loadingTime =
                if (loadingStart != null) {
                    System.nanoTime() - loadingStart
                } else {
                    // in case the view was hidden but it was resumed directly
                    // without being started again
                    0
                }
            if (it.finishedLoadingOnce) {
                it.firstTimeLoading = false
            }
        }
    }

    fun onDestroyed(view: Any) {
        viewsTimeAndState.remove(view)
    }

    fun onPaused(view: Any) {
        viewsTimeAndState[view]?.let {
            it.loadingTime = 0
            it.loadingStart = null
            it.firstTimeLoading = false
            it.finishedLoadingOnce = true
        }
    }

    fun getLoadingTime(view: Any): Long? {
        viewsTimeAndState[view]?.let {
            return it.loadingTime
        }

        return null
    }

    fun isFirstTimeLoading(view: Any): Boolean {
        return viewsTimeAndState[view]?.firstTimeLoading ?: false
    }

    private data class ViewLoadingInfo(
        var loadingStart: Long?,
        var loadingTime: Long = 0,
        var firstTimeLoading: Boolean = true,
        var finishedLoadingOnce: Boolean = false
    )
}
