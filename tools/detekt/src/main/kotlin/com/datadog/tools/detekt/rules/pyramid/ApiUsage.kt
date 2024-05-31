/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.pyramid

import com.datadog.tools.detekt.rules.AbstractCallExpressionRule
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.io.File

/**
 * @active
 */
@RequiresTypeResolution
class ApiUsage(
    config: Config
) : AbstractCallExpressionRule(config, simplifyLocalTypes = true, includeTypeArguments = false) {

    private val outputFileName: String by config(defaultValue = "apiUsage.log")
    private val outputFile: File by lazy { File(outputFileName) }

    private var visitingTestFunction = false

    // region Rule

    override val issue: Issue = Issue(
        javaClass.simpleName,
        Severity.Maintainability,
        "This rule reports api usages.",
        Debt.FIVE_MINS
    )

    override fun visitNamedFunction(function: KtNamedFunction) {
        val annotations = function.annotationEntries.mapNotNull {
            it.shortName?.asString()?.resolveFullType()
        }

        if (annotations.any { it in testAnnotations }) {
            visitingTestFunction = true
            super.visitNamedFunction(function)
            visitingTestFunction = false
        }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        if (visitingTestFunction) super.visitCallExpression(expression)
    }

    // endregion

    // region AbstractCallExpressionRule

    override fun visitResolvedFunctionCall(
        expression: KtCallExpression,
        resolvedCall: ResolvedFunCall
    ) {
        outputFile.appendText(resolvedCall.call)
        outputFile.appendText("\n")
    }

    // endregion

    companion object {
        internal val testAnnotations = arrayOf(
            "org.junit.jupiter.api.BeforeEach",
            "org.junit.jupiter.api.Test",
            "org.junit.jupiter.api.RepeatedTest"
        )
    }
}
