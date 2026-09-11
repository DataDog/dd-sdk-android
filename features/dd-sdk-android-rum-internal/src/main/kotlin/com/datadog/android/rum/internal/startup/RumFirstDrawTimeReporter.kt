/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

// These types are public only so that :features:dd-sdk-android-rum and
// :features:dd-sdk-android-rum-prelaunch can share them across module boundaries. They are not
// part of the SDK's public API and carry no KDoc for that reason.
@file:Suppress(
    "PackageNameVisibility",
    "UndocumentedPublicClass",
    "UndocumentedPublicFunction",
    "UndocumentedPublicProperty"
)

package com.datadog.android.rum.internal.startup

import android.app.Activity

/**
 * Reports the timestamp of the first drawn frame for a given [Activity].
 *
 * Used by the RUM SDK to measure Time To Initial Display (TTID) by observing when the
 * activity's window first renders its content on screen.
 */
interface RumFirstDrawTimeReporter {

    /**
     * An opaque handle returned by [subscribeToFirstFrameDrawn].
     *
     * Call [unsubscribe] to cancel the subscription and release all internal listener
     * registrations, breaking any retain cycles before the Activity is garbage-collected.
     */
    interface Handle {
        /**
         * Cancels this subscription. Idempotent — safe to call multiple times.
         *
         * After this call the [Callback] will never fire, and all internal listener
         * registrations (WindowCallback, OnAttachStateChange, OnDraw) are removed.
         */
        fun unsubscribe()
    }

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
     * Callers must store the returned [Handle] and call [Handle.unsubscribe] when the
     * Activity is destroyed to prevent memory leaks.
     *
     * @param activity The activity whose first frame draw should be observed.
     * @param callback The callback to invoke when the first frame is drawn.
     * @return A [Handle] that can cancel this subscription.
     */
    fun subscribeToFirstFrameDrawn(activity: Activity, callback: Callback): Handle
}
