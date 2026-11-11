/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.view.View
import androidx.annotation.UiThread
import com.datadog.android.lint.InternalApi

/**
 * Optional cross-platform integration implemented alongside [SessionReplayInternalCallback].
 * A key resets only the heatmap identity path at this view. It does not restrict capture.
 */
@InternalApi
interface SessionReplayHeatmapScreenRootProvider {
    /**
     * Returns a stable, qualified logical screen key, or null for an ordinary view.
     * Implementations must perform only bounded, non-blocking UI-thread work.
     * If this method throws, capture continues without heatmap identifiers for the affected subtree.
     */
    @UiThread
    fun getHeatmapScreenRootKey(view: View): String?
}
