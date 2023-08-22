/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.log.internal.utils.ISO_8601
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal fun Forge.aFormattedTimestamp(format: String = ISO_8601): String {
    val simpleDateFormat = SimpleDateFormat(format, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return simpleDateFormat.format(this.aTimestamp())
}
