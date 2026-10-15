/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import com.datadog.tools.detekt.ext.resolveCalledFunction
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

/**
 * A rule to detect direct usage of system static time methods.
 *
 * These methods should be replaced with TimeProvider to allow for better testability
 * and consistent time handling across the SDK.
 *
 * @active
 */
class PreferTimeProvider(
    config: Config
) : Rule(config, "Prefer using TimeProvider instead of static system time calls for better testability."),
    RequiresAnalysisApi {

    private val allowedFiles: List<Regex> by config(defaultValue = DEFAULT_ALLOWED_FILES) {
        it.map { pattern -> Regex(pattern) }
    }

    private var currentFileName: String = ""

    override fun visitKtFile(file: KtFile) {
        currentFileName = file.name
        super.visitKtFile(file)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (isFileAllowed()) return

        val (calledFunction, hasParameters) = analyze(expression) {
            val symbol = resolveCalledFunction(expression)
            symbol?.callableId?.asSingleFqName() to (symbol?.valueParameters?.isNotEmpty() == true)
        }
        if (calledFunction == null) return

        // Overloads with parameters (e.g. `Instant.now(clock)`) get the time from an injected source
        val isProhibited = calledFunction.asString() in KOTLIN_TIME_FUNCTIONS ||
            (calledFunction.asString() in PROHIBITED_TIME_METHODS && !hasParameters)
        if (isProhibited) {
            report(
                Finding(
                    Entity.from(expression),
                    "Use TimeProvider instead of ${calledFunction.displayName()} for better testability."
                )
            )
        }
    }

    private fun isFileAllowed(): Boolean {
        return allowedFiles.any { it.matches(currentFileName) }
    }

    /**
     * @return the function as written in code, e.g. `System.currentTimeMillis()` or `measureNanoTime()`
     */
    private fun FqName.displayName(): String {
        val name = if (asString() in KOTLIN_TIME_FUNCTIONS) {
            shortName().asString()
        } else {
            "${parent().shortName()}.${shortName()}"
        }
        return "$name()"
    }

    internal companion object {
        internal val DEFAULT_ALLOWED_FILES = listOf(
            ".*DdRumContentProvider.*",
            ".*TimeProvider.*"
        )

        internal val KOTLIN_TIME_FUNCTIONS = setOf(
            "kotlin.system.measureNanoTime",
            "kotlin.system.measureTimeMillis"
        )

        internal val PROHIBITED_TIME_METHODS = setOf(
            "android.os.SystemClock.elapsedRealtime",
            "android.os.SystemClock.elapsedRealtimeNanos",
            "android.os.SystemClock.uptimeMillis",
            "java.lang.System.currentTimeMillis",
            "java.lang.System.nanoTime",
            "java.time.Clock.systemDefaultZone",
            "java.time.Clock.systemUTC",
            "java.time.Instant.now"
        )
    }
}
