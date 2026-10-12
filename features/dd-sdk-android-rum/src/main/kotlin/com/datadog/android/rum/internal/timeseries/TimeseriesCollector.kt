/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface TimeseriesCollector {
    /** Called when a RUM session becomes the active one. */
    fun onSessionStart(sessionId: String, sessionType: RumSessionType)

    /** Called when the active RUM session stops, expires, or is renewed. */
    fun onSessionStop(sessionId: String)

    /** Called for every RUM event, with the resulting [RumContext]. */
    fun onRumContextUpdate(newRumContext: RumContext)

    /**
     * Called when the process has at least one started, and therefore visible, Activity.
     * This is the app visibility state, which is independent of the RUM session state.
     */
    fun onUiVisible()

    /**
     * Called when the process has no started Activity left, so nothing is visible.
     * This is the app visibility state, which is independent of the RUM session state.
     */
    fun onUiHidden()
}
