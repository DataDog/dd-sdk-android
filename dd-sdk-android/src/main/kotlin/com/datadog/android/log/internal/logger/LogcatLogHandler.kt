/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import android.os.Build
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.log.Logger

internal class LogcatLogHandler(
    internal val serviceName: String
) : LogHandler {

    // region LogHandler

    override fun handleLog(
        level: Int,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>,
        tags: Set<String>
    ) {
        val stackElement = getCallerStackElement()
        val tag = resolveTag(stackElement)
        val suffix = resolveSuffix(stackElement)
        if (Build.MODEL == null) {
            println("${levelPrefixes[level]}/$tag: $message$suffix")
            throwable?.printStackTrace()
        } else {
            Log.println(level, tag, message + suffix)
            if (throwable != null) {
                Log.println(
                    level,
                    tag,
                    Log.getStackTraceString(throwable)
                )
            }
        }
    }

    // endregion

    // region Internal

    private fun resolveTag(stackTraceElement: StackTraceElement?): String {
        val tag = if (stackTraceElement == null) {
            serviceName
        } else {
            stackTraceElement.className
                .replace(ANONYMOUS_CLASS, "")
                .substringAfterLast('.')
        }
        return if (tag.length >= MAX_TAG_LENGTH && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            tag.substring(0, MAX_TAG_LENGTH)
        } else {
            tag
        }
    }

    private fun resolveSuffix(stackTraceElement: StackTraceElement?): String {
        return if (stackTraceElement == null) {
            ""
        } else {
            "\t| at .${stackTraceElement.methodName}" +
                "(${stackTraceElement.fileName}:${stackTraceElement.lineNumber})"
        }
    }

    private fun getCallerStackElement(): StackTraceElement? {
        return if (Datadog.isDebug) {
            val stackTrace = Throwable().stackTrace
            stackTrace.firstOrNull {
                it.className !in ignoredClassNames
            }
        } else {
            null
        }
    }

    // endregion

    companion object {

        private const val MAX_TAG_LENGTH = 23

        private val levelPrefixes = mapOf(
            Log.VERBOSE to "V",
            Log.DEBUG to "D",
            Log.INFO to "I",
            Log.WARN to "W",
            Log.ERROR to "E",
            Log.ASSERT to "A"
        )

        private val ANONYMOUS_CLASS = Regex("(\\$\\d+)+$")
        private val ignoredClassNames = arrayOf(
            Logger::class.java.canonicalName,
            LogcatLogHandler::class.java.canonicalName,
            ConditionalLogHandler::class.java.canonicalName,
            CombinedLogHandler::class.java.canonicalName,
            DatadogLogHandler::class.java.canonicalName
        )
    }
}
