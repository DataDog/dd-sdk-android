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
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * A rule to detekt `check`, `checkNotNull` calls.
 * @active
 */
class RequireInternal : Rule() {

    override val issue: Issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "This rule reports when an exception is thrown.",
        Debt.TWENTY_MINS
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        val callee = expression.calleeExpression
        val isCheckMethod = callee?.textMatches(REQUIRE_LITERAL) == true
        val isCheckNotNullMethod = callee?.textMatches(REQUIRE_NOT_NULL_LITERAL) == true
        if ((isCheckMethod || isCheckNotNullMethod) && !expression.isContainingEntryPointPublic()) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    message = "A require is called from an internal or private part of the code."
                )
            )
        }
        super.visitCallExpression(expression)
    }

    companion object {
        private const val REQUIRE_LITERAL = "require"
        private const val REQUIRE_NOT_NULL_LITERAL = "requireNotNull"
    }
}
