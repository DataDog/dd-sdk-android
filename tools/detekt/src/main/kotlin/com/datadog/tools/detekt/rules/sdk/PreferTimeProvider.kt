/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
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
    ruleSetConfig: Config = Config.empty
) : Rule(ruleSetConfig) {

    private val allowedFiles: List<Regex> by config(defaultValue = DEFAULT_ALLOWED_FILES) {
        it.map { pattern -> Regex(pattern) }
    }

    private var currentFileName: String = ""

    override val issue: Issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "Prefer using TimeProvider instead of static system time calls for better testability.",
        Debt.TEN_MINS
    )

    override fun visitKtFile(file: KtFile) {
        currentFileName = file.name
        super.visitKtFile(file)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (isFileAllowed()) return

        val calleeName = expression.calleeExpression?.text ?: return
        if (calleeName in KOTLIN_TIME_FUNCTIONS) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Use TimeProvider instead of $calleeName() for better testability."
                )
            )
        }
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)

        if (isFileAllowed()) return

        val expressionText = expression.text
        val detectedMethod = detectProhibitedTimeMethod(expressionText)
        if (detectedMethod != null) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Use TimeProvider instead of $detectedMethod for better testability."
                )
            )
        }
    }

    private fun detectProhibitedTimeMethod(expressionText: String): String? {
        return PROHIBITED_TIME_METHODS.firstOrNull { expressionText.startsWith(it) }
    }

    private fun isFileAllowed(): Boolean {
        return allowedFiles.any { it.matches(currentFileName) }
    }

    internal companion object {
        internal val DEFAULT_ALLOWED_FILES = listOf(
            ".*DdRumContentProvider.*",
            ".*Time\\.kt",
            ".*TimeProvider.*"
        )

        internal val KOTLIN_TIME_FUNCTIONS = setOf(
            "measureNanoTime",
            "measureTimeMillis"
        )

        internal val PROHIBITED_TIME_METHODS = listOf(
            "Clock.systemDefaultZone()",
            "Clock.systemUTC()",
            "Instant.now()",
            "System.currentTimeMillis()",
            "System.nanoTime()",
            "SystemClock.elapsedRealtime()",
            "SystemClock.elapsedRealtimeNanos()",
            "SystemClock.uptimeMillis()"
        )
    }
}
