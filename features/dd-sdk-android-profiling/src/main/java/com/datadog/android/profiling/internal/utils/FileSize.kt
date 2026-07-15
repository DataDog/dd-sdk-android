/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.utils

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.lengthSafe
import java.io.File

internal fun fileSizeSafe(filePath: String?, internalLogger: InternalLogger?): Long {
    if (filePath.isNullOrEmpty()) return 0L
    val file = File(filePath)
    return if (internalLogger != null) {
        file.lengthSafe(internalLogger)
    } else {
        try {
            file.length()
        } catch (_: SecurityException) {
            0L
        }
    }
}
