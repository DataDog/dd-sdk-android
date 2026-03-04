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
 * During application launch the SDK tracks the first `Activity` that is created and uses
 * the time it draws its first frame to compute the TTID value. Use this if you want to skip
 * some Activities and instead make the SDK consider the 2nd, 3rd Activity's first frame.
 * Example use cases are:
 * 1. Splash screens implemented as `Activity`.
 * 2. Activities that launch another Activity in their `onCreate()` method and call `finish()` on themselves.
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
