/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class LogForgeryFactory : ForgeryFactory<Log> {
    override fun getForgery(forge: Forge): Log {
        return Log(
            serviceName = forge.anAlphabeticalString(),
            message = forge.anAlphabeticalString(),
            userAgent = forge.anAlphabeticalString(),
            level = forge.anElementFrom(
                android.util.Log.VERBOSE, android.util.Log.DEBUG,
                android.util.Log.INFO, android.util.Log.WARN,
                android.util.Log.ERROR, android.util.Log.ASSERT,
                0, 1 // Yes those don't exist, but let's over all the edge cases…
            ),
            timestamp = forge.aLong(),
            throwable = null
        )
    }
}
