/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.startup

import android.app.Activity

/**
 * Reports the timestamp of the first drawn frame for a given [Activity].
 *
 * Used by the RUM SDK to measure Time To Initial Display (TTID) by observing when the
 * activity's window first renders its content on screen.
 */
interface RumFirstDrawTimeReporter {

    /**
     * Callback invoked when the first frame of an activity's window has been drawn.
     */
    interface Callback {
        /**
         * Called once the first frame has been drawn.
         *
         * @param timestampNs The elapsed realtime timestamp of the first draw, in nanoseconds.
         */
        fun onFirstFrameDrawn(timestampNs: Long)
    }

    /**
     * Subscribes to receive a callback when the first frame of [activity]'s window is drawn.
     *
     * The [callback] is guaranteed to be invoked at most once per subscription.
     *
     * @param activity The activity whose first frame draw should be observed.
     * @param callback The callback to invoke when the first frame is drawn.
     */
    fun subscribeToFirstFrameDrawn(activity: Activity, callback: Callback)
}
