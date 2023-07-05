/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.utils

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal const val ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

@Suppress("UnsafeThirdPartyFunctionCall")
internal fun buildLogDateFormat(): SimpleDateFormat =
    // NPE cannot happen here, ISO_8601 pattern is valid
    SimpleDateFormat(ISO_8601, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
