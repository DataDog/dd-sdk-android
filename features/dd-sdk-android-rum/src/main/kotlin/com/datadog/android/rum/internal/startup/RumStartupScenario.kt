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

    data class Cold(
        override val startTimeNanos: Long,
        override val hasSavedInstanceStateBundle: Boolean,
        override val activityName: String,
        override val activity: Activity,
        val gapNanos: Long,
    ) : RumStartupScenario

    data class WarmFirstActivity(
        override val startTimeNanos: Long,
        override val hasSavedInstanceStateBundle: Boolean,
        override val activityName: String,
        override val activity: Activity,
        val gapNanos: Long,
        val processStartedInForeground: Boolean,
    ) : RumStartupScenario

    data class WarmAfterActivityDestroyed(
        override val startTimeNanos: Long,
        override val hasSavedInstanceStateBundle: Boolean,
        override val activityName: String,
        override val activity: Activity
    ) : RumStartupScenario
}
