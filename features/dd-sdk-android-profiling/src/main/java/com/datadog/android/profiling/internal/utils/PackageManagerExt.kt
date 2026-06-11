/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.utils

import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.InternalLogger

@RequiresApi(Build.VERSION_CODES.Q)
internal fun PackageManager.getProfilingModuleLongVersionCode(internalLogger: InternalLogger): Long {
    val packageInfo = try {
        getPackageInfo("com.google.android.profiling", PackageManager.MATCH_APEX)
    } catch (e: PackageManager.NameNotFoundException) {
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { "System package com.google.android.profiling is not found" },
            throwable = e
        )
        null
    }
    return packageInfo?.longVersionCode ?: 0L
}
