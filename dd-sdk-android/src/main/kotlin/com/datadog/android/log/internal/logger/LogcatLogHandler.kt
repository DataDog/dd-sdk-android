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
import java.util.regex.Pattern

internal class LogcatLogHandler(
    internal val serviceName: String,
    nestedDepth: Int = 0
) : LogHandler {

    private val callerNameStackIndex: Int

    init {
        callerNameStackIndex = DEFAULT_LOGGER_CALLER_STACK_INDEX + nestedDepth
    }
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
        val className: String = tryAndSearchClassName() ?: return serviceName
        var tag = stripAnonymousPart(className)
        tag = tag.substring(tag.lastIndexOf('.') + 1)

        return sanitizeTag(tag)
    }

    private fun tryAndSearchClassName(): String? {
        val stackTrace = Throwable().stackTrace
        // it might happen that when called from Java code the stack index to be ++
        // due to the Java - Kotlin bridge method so we need an extra check here.
        for (i in callerNameStackIndex until stackTrace.size) {
            val className = stackTrace[i].className
            if (className != LOGGER_CLASS_NAME)
                return className
        }
        return null
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
        return matcher.replaceAll("")
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

        private const val DEFAULT_LOGGER_CALLER_STACK_INDEX = 7
        private val LOGGER_CLASS_NAME = Logger::class.java.canonicalName
    }
}
