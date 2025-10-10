/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.utils

import android.app.ActivityManager
import com.datadog.android.rum.DdRumContentProvider

/**
 * In instrumentation tests, the process is treated as [IMPORTANCE_FOREGROUND_SERVICE],
 * which differs from the behavior in a typical user environment. To prevent this difference
 * from affecting test results, this function can be called to override the relevant variable
 * inside [DdRumContentProvider], allowing the SDK to correctly identify that the application
 * is in the foreground.
 */
fun overrideProcessImportance() {
    DdRumContentProvider.processImportance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}
