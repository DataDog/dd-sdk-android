/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.startup

import android.app.Activity

/**
 * Predicate to determine which Activities should be considered for app startup
 * Time To Initial Display (TTID) reporting.
 *
 * Use this to exclude "interstitial Activities" - Activities that are launched during
 * app startup but immediately launch another Activity in their `onCreate()` method
 * and call `finish()` on themselves (e.g., splash screens, authentication screens).
 *
 * These interstitial Activities may never draw a frame, which can prevent TTID from
 * being reported when the actual main Activity is displayed.
 *
 * **Performance Note:** This predicate is called for every Activity during app startup.
 * Ensure the implementation is lightweight and doesn't perform expensive operations
 * (e.g., disk I/O, network calls, heavy computations).
 *
 * Example usage:
 * ```
 * RumConfiguration.Builder(applicationId)
 *     .setAppStartupActivityPredicate { activity ->
 *         // Exclude authentication and splash activities from TTID measurement
 *         activity !is AuthenticationActivity && activity !is SplashActivity
 *     }
 *     .build()
 * ```
 */
fun interface AppStartupActivityPredicate {
    /**
     * Determines whether the given Activity should be considered for TTID measurement.
     *
     * This method is called on the main thread during Activity creation. Keep the
     * implementation fast and avoid blocking operations.
     *
     * @param activity The Activity being evaluated during app startup.
     * @return true if this Activity should be included in TTID measurement, false to exclude it.
     */
    fun shouldTrackStartup(activity: Activity): Boolean
}
