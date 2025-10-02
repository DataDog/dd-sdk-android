/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import java.lang.ref.WeakReference
import kotlin.time.Duration

internal sealed interface RumStartupScenario {
    val initialTimeNanos: Long
    val hasSavedInstanceStateBundle: Boolean
    val activity: WeakReference<Activity>

    class Cold(
        override val initialTimeNanos: Long,
        override val hasSavedInstanceStateBundle: Boolean,
        override val activity: WeakReference<Activity>,
        val gap: Duration
    ) : RumStartupScenario

    class WarmFirstActivity(
        override val initialTimeNanos: Long,
        override val hasSavedInstanceStateBundle: Boolean,
        override val activity: WeakReference<Activity>,
        val gap: Duration
    ) : RumStartupScenario

    class WarmAfterActivityDestroyed(
        override val initialTimeNanos: Long,
        override val hasSavedInstanceStateBundle: Boolean,
        override val activity: WeakReference<Activity>
    ) : RumStartupScenario
}

internal val RumStartupScenario.name: String get() = when (this) {
    is RumStartupScenario.Cold -> "cold"
    is RumStartupScenario.WarmAfterActivityDestroyed -> "warm_after_activity_destroyed"
    is RumStartupScenario.WarmFirstActivity -> "warm_first_activity"
}

internal val RumStartupScenario.gap: Duration? get() = when (this) {
    is RumStartupScenario.Cold -> gap
    is RumStartupScenario.WarmFirstActivity -> gap
    is RumStartupScenario.WarmAfterActivityDestroyed -> null
}
