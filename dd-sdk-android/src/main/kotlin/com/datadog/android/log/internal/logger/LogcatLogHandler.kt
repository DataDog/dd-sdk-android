/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import android.os.Build
import android.util.Log

internal class LogcatLogHandler(
    internal val serviceName: String
) : LogHandler {

    // region LogHandler

    override fun handleLog(
        level: Int,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long?
    ) {
        if (Build.MODEL == null) {
            println("${levelPrefixes[level]}/$serviceName: $message")
            throwable?.printStackTrace()
        } else {
            Log.println(level, serviceName, message)
            if (throwable != null) {
                Log.println(
                    level,
                    serviceName,
                    Log.getStackTraceString(throwable)
                )
            }
        }
    }

    // endregion

    companion object {

        private val levelPrefixes = mapOf(
            Log.VERBOSE to "V",
            Log.DEBUG to "D",
            Log.INFO to "I",
            Log.WARN to "W",
            Log.ERROR to "E",
            Log.ASSERT to "A"
        )
    }
}
