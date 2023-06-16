/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import androidx.annotation.MainThread
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.internal.utils.TimeProvider

internal class RumContextDataHandler(
    private val rumContextProvider: RumContextProvider,
    private val timeProvider: TimeProvider
) {
    private var prevRumContext = SessionReplayRumContext()

    @MainThread
    internal fun createRumContextData(): RumContextData? {
        // we will make sure we get the timestamp on the UI thread to avoid time skewing
        val timestamp = timeProvider.getDeviceTimestamp()

        // TODO: RUMM-2426 Fetch the RumContext from the core SDKContext when available
        val newRumContext = rumContextProvider.getRumContext()

        if (newRumContext.isNotValid()) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
            return null
        }

        val rumContextData = RumContextData(timestamp, newRumContext.copy(), prevRumContext.copy())

        prevRumContext = newRumContext

        return rumContextData
    }
}
