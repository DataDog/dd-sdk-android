/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import java.util.concurrent.TimeUnit

internal class DefaultViewUpdatePredicate(
    private val viewUpdateThreshold: Long = VIEW_UPDATE_THRESHOLD_IN_NS
) : ViewUpdatePredicate {
    private var lastViewUpdateTimestamp = System.nanoTime() - VIEW_UPDATE_THRESHOLD_IN_NS

    override fun canUpdateView(isViewComplete: Boolean, event: RumRawEvent): Boolean {
        val isFatalError = event is RumRawEvent.AddError && event.isFatal
        val isThresholdReached = System.nanoTime() - lastViewUpdateTimestamp > viewUpdateThreshold
        if (isViewComplete ||
            isFatalError ||
            isThresholdReached
        ) {
            lastViewUpdateTimestamp = System.nanoTime()
            return true
        }
        return false
    }

    companion object {
        internal val VIEW_UPDATE_THRESHOLD_IN_NS = TimeUnit.SECONDS.toNanos(30)
    }
}
