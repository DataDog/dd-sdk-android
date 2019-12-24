/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.forge

import android.util.Log as AndroidLog
import com.datadog.android.log.internal.domain.Log
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal class LogForgeryFactory : ForgeryFactory<Log> {
    override fun getForgery(forge: Forge): Log {
        return Log(
            serviceName = forge.anAlphabeticalString(),
            level = forge.anElementFrom(
                0, 1,
                AndroidLog.VERBOSE, AndroidLog.DEBUG,
                AndroidLog.INFO, AndroidLog.WARN,
                AndroidLog.ERROR, AndroidLog.ASSERT
            ),
            message = forge.anAlphabeticalString(),
            timestamp = forge.aLong(),
            throwable = forge.getForgery(),
            attributes = forge.exhaustiveAttributes(),
            tags = forge.exhaustiveTags(),
            networkInfo = forge.getForgery(),
            loggerName = forge.anAlphabeticalString(),
            threadName = forge.anAlphabeticalString()
        )
    }

    private fun Forge.exhaustiveAttributes(): Map<String, Any?> {
        val map = listOf(
            aBool(),
            anInt(),
            aLong(),
            aFloat(),
            aDouble(),
            anAsciiString(),
            getForgery<Date>(),
            getForgery<Locale>(),
            getForgery<TimeZone>(),
            getForgery<File>(),
            null
        ).map { anAlphabeticalString() to it }
            .toMap().toMutableMap()
        map[""] = anHexadecimalString()
        map[aWhitespaceString()] = anHexadecimalString()
        return map
    }

    private fun Forge.exhaustiveTags(): List<String> {
        return aList { aStringMatching("[a-z]([a-z0-9_:./-]{0,198}[a-z0-9_./-])?") }
    }
}
