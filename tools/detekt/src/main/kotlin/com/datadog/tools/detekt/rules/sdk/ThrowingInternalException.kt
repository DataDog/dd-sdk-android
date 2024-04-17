/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import com.datadog.tools.detekt.ext.isContainingEntryPointPublic
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtThrowExpression

/**
 * A rule to detekt thrown exceptions.
 * @active
 */
class ThrowingInternalException : Rule() {

    override val issue: Issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "This rule reports when an exception is thrown from a private or internal class.",
        Debt.TWENTY_MINS
    )

    override fun visitThrowExpression(expression: KtThrowExpression) {
        if (!expression.isContainingEntryPointPublic()) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    message = "An exception is thrown from an internal or private part of the code."
                )
            )
        }
        super.visitThrowExpression(expression)
    }
}
