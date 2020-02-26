/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import android.os.Build
import android.util.Log
import com.datadog.android.Datadog
import java.util.regex.Pattern

internal class LogcatLogHandler(
    internal val serviceName: String,
    internal val callerNameStackIndex: Int = DEFAULT_LOGGER_CALLER_STACK_INDEX
) : LogHandler {

    // region LogHandler

    override fun handleLog(
        level: Int,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>,
        tags: Set<String>
    ) {
        val tag = resolveTag()
        if (Build.MODEL == null) {
            println("${levelPrefixes[level]}/$tag: $message")
            throwable?.printStackTrace()
        } else {
            Log.println(level, tag, message)
            if (throwable != null) {
                Log.println(
                    level,
                    tag,
                    Log.getStackTraceString(throwable)
                )
            }
        }
    }

    private fun resolveTag(): String {
        return if (Datadog.isDebug) {
            return resolveTagFromCallerClassName()
        } else {
            serviceName
        }
    }

    private fun resolveTagFromCallerClassName(): String {
        val stackTrace = Throwable().stackTrace
        if (stackTrace.size <= callerNameStackIndex) {
            return serviceName
        }

        // remove the Anonymous class name
        val className = stackTrace[callerNameStackIndex].className
        var tag = stripAnonymousPart(className)
        tag = tag.substring(tag.lastIndexOf('.') + 1)

        return sanitizeTag(tag)
    }

    private fun sanitizeTag(tag: String): String {
        return if (tag.length < MAX_TAG_LENGTH || Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tag
        } else {
            tag.substring(0, MAX_TAG_LENGTH)
        }
    }

    private fun stripAnonymousPart(className: String): String {
        val matcher = ANONYMOUS_CLASS.matcher(className)
        return if (matcher.find()) {
            matcher.replaceAll("")
        } else {
            className
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

        private val ANONYMOUS_CLASS =
            Pattern.compile("(\\$\\d+)+$")
        private const val MAX_TAG_LENGTH = 23

        internal const val SDK_LOGGER_CALLER_STACK_INDEX = 6
        internal const val DEFAULT_LOGGER_CALLER_STACK_INDEX = 7
    }
}
