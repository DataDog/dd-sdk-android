/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import android.util.Log
import com.datadog.android.log.Logger

internal class LogcatLogHandler(
    internal val serviceName: String,
    internal val useClassnameAsTag: Boolean,
    internal val isDebug: Boolean = false
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
        val stackElement = getCallerStackElement()
        val tag = resolveTag(stackElement)
        val suffix = resolveSuffix(stackElement)
        Log.println(level, tag, message + suffix)
        if (throwable != null) {
            Log.println(
                level,
                tag,
                Log.getStackTraceString(throwable)
            )
        }
    }

    override fun handleLog(
        level: Int,
        message: String,
        errorKind: String?,
        errorMessage: String?,
        errorStacktrace: String?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long?
    ) {
        val stackElement = getCallerStackElement()
        val tag = resolveTag(stackElement)
        val suffix = resolveSuffix(stackElement)
        Log.println(level, tag, message + suffix)
        if (errorStacktrace != null) {
            Log.println(
                level,
                tag,
                errorStacktrace
            )
        }
    }

    // endregion

    // region Internal

    private fun resolveTag(stackTraceElement: StackTraceElement?): String {
        return if (stackTraceElement == null) {
            serviceName
        } else {
            stackTraceElement.className
                .replace(ANONYMOUS_CLASS, "")
                .substringAfterLast('.')
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

    @Suppress("ThrowingExceptionsWithoutMessageOrCause")
    internal fun getCallerStackElement(): StackTraceElement? {
        return if (isDebug && useClassnameAsTag) {
            val stackTrace = Throwable().stackTrace
            findValidCallStackElement(stackTrace)
        } else {
            null
        }
    }

    internal fun findValidCallStackElement(
        stackTrace: Array<StackTraceElement>
    ): StackTraceElement? {
        return stackTrace.firstOrNull { element ->
            element.className !in IGNORED_CLASS_NAMES &&
                IGNORED_PACKAGE_PREFIXES.none { element.className.startsWith(it) }
        }
    }

    // endregion

    companion object {

        private val ANONYMOUS_CLASS = Regex("(\\$\\d+)+$")

        // internal for testing
        internal val IGNORED_CLASS_NAMES = arrayOf(
            Logger::class.java.canonicalName,
            LogHandler::class.java.canonicalName,
            LogHandler::class.java.canonicalName?.plus("\$DefaultImpls"),
            LogcatLogHandler::class.java.canonicalName,
            ConditionalLogHandler::class.java.canonicalName,
            CombinedLogHandler::class.java.canonicalName,
            DatadogLogHandler::class.java.canonicalName
        )

        // internal for testing
        internal val IGNORED_PACKAGE_PREFIXES = arrayOf(
            "com.datadog.android.timber",
            "timber.log"
        )
    }
}
