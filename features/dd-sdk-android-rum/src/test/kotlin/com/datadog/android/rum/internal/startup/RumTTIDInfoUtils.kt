/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import fr.xgouchet.elmyr.Forge
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds

internal fun Forge.testRumStartupScenarios(weakActivity: WeakReference<Activity>): List<RumStartupScenario> {
    val initialTimeNanos = aLong(min = 0, max = 1000000)
    val hasSavedInstanceStateBundle = aBool()

    return listOf(
        RumStartupScenario.Cold(
            initialTimeNanos = initialTimeNanos,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
            activity = weakActivity,
            gap = anInt(min = 0, max = 10000).milliseconds
        ),
        RumStartupScenario.WarmFirstActivity(
            initialTimeNanos = initialTimeNanos,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
            activity = weakActivity,
            gap = anInt(min = 0, max = 10000).milliseconds
        ),
        RumStartupScenario.WarmAfterActivityDestroyed(
            initialTimeNanos = initialTimeNanos,
            hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
            activity = weakActivity
        )
    )
}
