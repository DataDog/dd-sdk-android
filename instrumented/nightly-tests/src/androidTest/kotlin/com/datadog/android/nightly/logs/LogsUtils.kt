/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.logs

import com.datadog.android.log.Logger
import com.datadog.android.nightly.utils.defaultTestAttributes
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge

fun initializeLogger() = Logger.Builder()
    .setLoggerName(LOGGER_NAME)
    .build()

fun Logger.sendRandomLog(
    testMethodName: String,
    forge: Forge
) {
    val message = forge.anAlphabeticalString()
    val throwable = forge.aNullable { forge.aThrowable() }
    val attributes = defaultTestAttributes(testMethodName)
    when (forge.anInt(min = 1, max = 7)) {
        1 -> v(message, throwable, attributes)
        2 -> d(message, throwable, attributes)
        3 -> w(message, throwable, attributes)
        4 -> e(message, throwable, attributes)
        5 -> i(message, throwable, attributes)
        6 -> wtf(message, throwable, attributes)
    }
}
