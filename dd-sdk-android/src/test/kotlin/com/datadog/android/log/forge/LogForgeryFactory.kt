/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.forge

import android.util.Log as AndroidLog
import com.datadog.android.log.internal.Log
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.Date

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
            userAgent = forge.anAlphabeticalString(),
            throwable = forge.getForgery(),
            attributes = forge.exhaustiveAttributes(),
            tags = forge.exhaustiveTags(),
            networkInfo = forge.getForgery()
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
            null
        ).map { anAlphabeticalString() to it }
            .toMap().toMutableMap()
        map[""] = anHexadecimalString()
        map[aWhitespaceString()] = anHexadecimalString()
        return map
    }

    private fun Forge.exhaustiveTags(): Map<String, String?> {
        val map = listOf(
            aNumericalString(),
            anAlphabeticalString(),
            null
        ).map { anAlphabeticalString() to it }
            .toMap().toMutableMap()
        map[""] = anHexadecimalString()
        map[aWhitespaceString()] = anHexadecimalString()
        return map
    }
}
