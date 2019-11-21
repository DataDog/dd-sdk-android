/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.forge

import com.datadog.android.log.internal.Log
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.Date

internal class LogForgeryFactory : ForgeryFactory<Log> {
    override fun getForgery(forge: Forge): Log {
        return Log(
            serviceName = forge.anAlphabeticalString(),
            level = forge.anElementFrom(
                android.util.Log.VERBOSE, android.util.Log.DEBUG,
                android.util.Log.INFO, android.util.Log.WARN,
                android.util.Log.ERROR, android.util.Log.ASSERT,
                0, 1 // Yes those don't exist, but let's cover all the edge cases…
            ),
            message = forge.anAlphabeticalString(),
            timestamp = forge.aLong(),
            userAgent = forge.anAlphabeticalString(),
            throwable = forge.getForgery(),
            attributes = forge.exhaustiveAttributes(),
            tags = forge.aMap { anAlphabeticalString() to aNumericalString() },
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
}
