/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.logs

import android.util.Log
import com.datadog.android.log.Logger
import com.datadog.android.nightly.utils.defaultTestAttributes
import com.datadog.android.v2.api.SdkCore
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge

fun initializeLogger(sdkCore: SdkCore) = Logger.Builder(sdkCore)
    .setName(LOGGER_NAME)
    .build()

fun Logger.sendRandomLog(
    testMethodName: String,
    forge: Forge,
    minLogLevel: Int = Log.VERBOSE,
    maxLogLevel: Int = Log.ASSERT
) {
    val message = forge.anAlphabeticalString()
    val throwable = forge.aNullable { forge.aThrowable() }
    val attributes = defaultTestAttributes(testMethodName)
    when (forge.anInt(min = minLogLevel, max = maxLogLevel + 1)) {
        Log.VERBOSE -> v(message, throwable, attributes)
        Log.DEBUG -> d(message, throwable, attributes)
        Log.INFO -> i(message, throwable, attributes)
        Log.WARN -> w(message, throwable, attributes)
        Log.ERROR -> e(message, throwable, attributes)
        Log.ASSERT -> wtf(message, throwable, attributes)
    }
}
