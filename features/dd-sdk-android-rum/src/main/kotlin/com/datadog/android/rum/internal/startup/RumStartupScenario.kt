/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import java.lang.ref.WeakReference

internal sealed interface RumStartupScenario {
    val initialTimeNs: Long
    val hasSavedInstanceStateBundle: Boolean
    val activity: WeakReference<Activity>

    class Cold(
        override val initialTimeNs: Long,
        override val hasSavedInstanceStateBundle: Boolean,
        override val activity: WeakReference<Activity>,
        val appStartActivityOnCreateGapNs: Long
    ) : RumStartupScenario

    class WarmFirstActivity(
        override val initialTimeNs: Long,
        override val hasSavedInstanceStateBundle: Boolean,
        override val activity: WeakReference<Activity>,
        val appStartActivityOnCreateGapNs: Long
    ) : RumStartupScenario

    class WarmAfterActivityDestroyed(
        override val initialTimeNs: Long,
        override val hasSavedInstanceStateBundle: Boolean,
        override val activity: WeakReference<Activity>
    ) : RumStartupScenario
}

internal val RumStartupScenario.name: String get() = when (this) {
    is RumStartupScenario.Cold -> "cold"
    is RumStartupScenario.WarmAfterActivityDestroyed -> "warm_after_activity_destroyed"
    is RumStartupScenario.WarmFirstActivity -> "warm_first_activity"
}

internal val RumStartupScenario.appStartActivityOnCreateGapNs: Long? get() = when (this) {
    is RumStartupScenario.Cold -> appStartActivityOnCreateGapNs
    is RumStartupScenario.WarmFirstActivity -> appStartActivityOnCreateGapNs
    is RumStartupScenario.WarmAfterActivityDestroyed -> null
}
