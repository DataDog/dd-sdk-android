/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import com.datadog.android.rum.internal.domain.Time
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.seconds

internal sealed interface RumStartupScenario {
    val initialTime: Time
    val hasSavedInstanceStateBundle: Boolean
    val activity: WeakReference<Activity>

    class Cold(
        override val hasSavedInstanceStateBundle: Boolean,
        override val activity: WeakReference<Activity>,
        val appStartActivityOnCreateGapNs: Long,
        override val initialTime: Time
    ) : RumStartupScenario

    class WarmFirstActivity(
        override val hasSavedInstanceStateBundle: Boolean,
        override val activity: WeakReference<Activity>,
        val appStartActivityOnCreateGapNs: Long,
        override val initialTime: Time
    ) : RumStartupScenario

    class WarmAfterActivityDestroyed(
        override val hasSavedInstanceStateBundle: Boolean,
        override val activity: WeakReference<Activity>,
        override val initialTime: Time
    ) : RumStartupScenario

    companion object {
        internal val START_GAP_THRESHOLD_NS = 10.seconds.inWholeNanoseconds

        /**
         * Builds the correct [RumStartupScenario] subtype from the raw timing data captured at
         * Activity creation time. This is the single source of truth for the Cold / WarmFirstActivity
         * / WarmAfterActivityDestroyed classification, shared by [RumAppStartupDetectorImpl] and
         * [com.datadog.android.rum.internal.RumFeature].
         */
        fun build(
            isFirstActivityForProcess: Boolean,
            hasSavedInstanceStateBundle: Boolean,
            activity: WeakReference<Activity>,
            processStartTime: Time,
            activityOnCreateTime: Time
        ): RumStartupScenario {
            return if (isFirstActivityForProcess) {
                val gapNs = activityOnCreateTime.nanoTime - processStartTime.nanoTime
                if (gapNs > START_GAP_THRESHOLD_NS) {
                    WarmFirstActivity(
                        hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                        activity = activity,
                        appStartActivityOnCreateGapNs = gapNs,
                        initialTime = activityOnCreateTime
                    )
                } else {
                    Cold(
                        hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                        activity = activity,
                        appStartActivityOnCreateGapNs = gapNs,
                        initialTime = processStartTime
                    )
                }
            } else {
                WarmAfterActivityDestroyed(
                    hasSavedInstanceStateBundle = hasSavedInstanceStateBundle,
                    activity = activity,
                    initialTime = activityOnCreateTime
                )
            }
        }
    }
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
