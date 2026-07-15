/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.utils

import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.InternalLogger
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

private const val LOG_PROFILE_CREATION_TIME_READ_FAILED =
    "Failed to read ANR result file creation time."

@RequiresApi(Build.VERSION_CODES.O)
internal fun getFileCreationTimeMs(path: String, internalLogger: InternalLogger?): Long? {
    return try {
        @Suppress("UnsafeThirdPartyFunctionCall")
        // Caught by try/catch block.
        Files.readAttributes(Paths.get(path), BasicFileAttributes::class.java)
            .creationTime()
            .toMillis()
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
        internalLogger?.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { LOG_PROFILE_CREATION_TIME_READ_FAILED },
            t
        )
        null
    }
}
