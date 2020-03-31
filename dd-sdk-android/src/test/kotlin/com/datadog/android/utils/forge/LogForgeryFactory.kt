/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import android.util.Log as AndroidLog
import com.datadog.android.log.internal.domain.Log
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class LogForgeryFactory : ForgeryFactory<Log> {
    override fun getForgery(forge: Forge): Log {
        return Log(
            serviceName = forge.anAlphabeticalString(),
            level = forge.anElementFrom(
                0, 1,
                AndroidLog.VERBOSE, AndroidLog.DEBUG,
                AndroidLog.INFO, AndroidLog.WARN,
                AndroidLog.ERROR, AndroidLog.ASSERT,
                Log.CRASH
            ),
            message = forge.anAlphabeticalString(),
            timestamp = forge.aLong(),
            throwable = forge.getForgery(),
            attributes = forge.exhaustiveAttributes(),
            tags = forge.exhaustiveTags(),
            networkInfo = forge.getForgery(),
            userInfo = forge.getForgery(),
            loggerName = forge.anAlphabeticalString(),
            threadName = forge.anAlphabeticalString()
        )
    }

    private fun Forge.exhaustiveTags(): List<String> {
        return aList { aStringMatching("[a-z]([a-z0-9_:./-]{0,198}[a-z0-9_./-])?") }
    }
}
