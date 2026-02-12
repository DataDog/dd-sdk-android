/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import com.datadog.android.rum.startup.AppStartupActivityPredicate

/**
 * Default implementation of [AppStartupActivityPredicate] that allows all Activities
 * to be tracked for app startup TTID measurement.
 */
internal object DefaultAppStartupActivityPredicate : AppStartupActivityPredicate {
    override fun shouldTrackStartup(activity: Activity): Boolean = true
}
