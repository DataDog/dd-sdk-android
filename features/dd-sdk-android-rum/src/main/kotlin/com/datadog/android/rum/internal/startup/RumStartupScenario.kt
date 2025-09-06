/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity

internal sealed interface RumStartupScenario {
    val startTimeNanos: Long
    val hasSavedInstanceStateBundle: Boolean

    val activityName: String
    val activity: Activity

    val nStart: Int

    data class Cold(
        override val startTimeNanos: Long,
        override val hasSavedInstanceStateBundle: Boolean,
        override val activityName: String,
        override val activity: Activity,
        val gapNanos: Long,
        override val nStart: Int,
    ) : RumStartupScenario

    data class WarmFirstActivity(
        override val startTimeNanos: Long,
        override val hasSavedInstanceStateBundle: Boolean,
        override val activityName: String,
        override val activity: Activity,
        val gapNanos: Long,
        val processStartedInForeground: Boolean,
        override val nStart: Int,
    ) : RumStartupScenario

    data class WarmAfterActivityDestroyed(
        override val startTimeNanos: Long,
        override val hasSavedInstanceStateBundle: Boolean,
        override val activityName: String,
        override val activity: Activity,
        override val nStart: Int,
    ) : RumStartupScenario
}

internal fun RumStartupScenario.name(): String {
    return when (this) {
        is RumStartupScenario.Cold -> "cold"
        is RumStartupScenario.WarmFirstActivity -> "warm_first_activity"
        is RumStartupScenario.WarmAfterActivityDestroyed -> "warm_after_activity_destroyed"
    }
}
