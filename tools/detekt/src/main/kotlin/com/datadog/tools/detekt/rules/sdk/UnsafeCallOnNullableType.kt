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
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtUnaryExpression

/**
 * The original UnsafeCallOnNullableType rule doesn't work with Android
 * projects using Kotlin (due to missing Type information).
 *
 * This rule will report any use of `!!`, even on non-nullable types.
 */
class UnsafeCallOnNullableType : Rule() {

    override val issue: Issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "This rule reports when an Unsafe null cast is used (ie: using !!).",
        Debt.TWENTY_MINS
    )

    override fun visitUnaryExpression(expression: KtUnaryExpression) {
        super.visitUnaryExpression(expression)

        if (expression.operationToken == KtTokens.EXCLEXCL) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Calling !! on a nullable type will throw a " +
                        "NullPointerException at runtime in case the value is null. " +
                        "It must be avoided."
                )
            )
        }
    }
}
