/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import com.datadog.tools.detekt.ext.isContainingEntryPointPublic
import com.datadog.tools.detekt.ext.resolveCalledFunction
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * A rule to detekt `check`, `checkNotNull` calls.
 * @active
 */
class CheckInternal(
    config: Config
) : Rule(config, "This rule reports when an exception is thrown."),
    RequiresAnalysisApi {

    override fun visitCallExpression(expression: KtCallExpression) {
        val calledFunction = analyze(expression) {
            resolveCalledFunction(expression)?.callableId?.asSingleFqName()?.asString()
        }
        if (calledFunction in CHECK_FUNCTIONS && !expression.isContainingEntryPointPublic()) {
            report(
                Finding(
                    Entity.from(expression),
                    "A check is called from an internal or private part of the code."
                )
            )
        }
        super.visitCallExpression(expression)
    }

    private companion object {
        private val CHECK_FUNCTIONS = setOf(
            "kotlin.check",
            "kotlin.checkNotNull"
        )
    }
}
