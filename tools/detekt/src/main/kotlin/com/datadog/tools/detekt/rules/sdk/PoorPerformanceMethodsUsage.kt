/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.tools.detekt.rules.sdk

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * A rule to detect methods that could cause performance issues.
 * Full list of detected methods listed in [PoorPerformanceMethodsUsage.POOR_PERFORMANCE_METHODS_NAMES]
 * @active
 */
class PoorPerformanceMethodsUsage : Rule() {

    override val issue: Issue = Issue(
        ISSUE_ID,
        severity = Severity.Performance,
        description = "This rule reports when method that might cause performance impact is used",
        debt = Debt.TWENTY_MINS
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        val referencedName = expression.getCallNameExpression()?.getReferencedName()
        if (referencedName !in POOR_PERFORMANCE_METHODS_NAMES) return

        val functionAnnotations = expression.getStrictParentOfType<KtNamedFunction>()
            ?.getAnnotationEntries() ?: emptyList()

        val expressionAnnotations = expression.getStrictParentOfType<KtAnnotatedExpression>()
            ?.annotationEntries ?: emptyList()

        val applicableAnnotations = (functionAnnotations + expressionAnnotations)
            .filter { it.shortName?.asString() == SUPPRESSION_ANNOTATION_NAME }

        val suppressedIssues = applicableAnnotations
            .map { annotation -> annotation.arguments }
            .flatten()

        if (ISSUE_ID !in suppressedIssues) {
            reportPotentialPerformanceImpact(
                expression,
                "Potential performance issue detected for method: $referencedName"
            )
        }
    }

    private val KtAnnotationEntry.arguments: List<String>
        get() = valueArguments
            .mapNotNull { argument ->
                when (val expression = argument.getArgumentExpression()) {
                    is KtStringTemplateExpression -> expression.entries.joinToString("") { it.text }
                    else -> throw IllegalArgumentException(
                        """
                            Complex arguments are not supported. 
                            Please use @${SUPPRESSION_ANNOTATION_NAME}("methodName") or
                             @${SUPPRESSION_ANNOTATION_NAME}("firstMethod", "secondMethod") 
                        """.trimIndent()
                    )
                }
            }

    private fun reportPotentialPerformanceImpact(
        expression: KtCallExpression,
        message: String
    ) {
        report(CodeSmell(issue, Entity.from(expression), message))
    }

    companion object {
        private const val ISSUE_ID = "PotentiallyPoorPerformanceMethodUsage"
        private const val SUPPRESSION_ANNOTATION_NAME = "Suppress"
        private val POOR_PERFORMANCE_METHODS_NAMES = setOf(
            "joinToString"
        )
    }
}
