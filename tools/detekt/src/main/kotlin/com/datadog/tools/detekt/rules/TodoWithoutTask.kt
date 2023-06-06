/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty

/**
 * A rule to detekt to-do comments without a Jira task.
 * @active
 */
class TodoWithoutTask : Rule() {
    override val issue: Issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "This rule reports when a TODO comment is missing a task number.",
        Debt.TEN_MINS
    )

    override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
        reportIfInvalid(declaration.docComment)
        super.visitObjectDeclaration(declaration)
    }

    override fun visitClass(klass: KtClass) {
        reportIfInvalid(klass.docComment)
        super.visitClass(klass)
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        reportIfInvalid(function.docComment)
        super.visitNamedFunction(function)
    }

    override fun visitProperty(property: KtProperty) {
        reportIfInvalid(property.docComment)
        super.visitProperty(property)
    }

    override fun visitComment(comment: PsiComment) {
        reportIfInvalid(comment)

        super.visitComment(comment)
    }

    private fun reportIfInvalid(comment: PsiComment?) {
        if (comment != null && comment.isInvalid()) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(comment),
                    message = "A comment with TODO is missing a task number."
                )
            )
        }
    }

    private fun PsiComment.isInvalid(): Boolean = TODO_REGEX.find(text) != null

    companion object {
        private const val TODO_PATTERN = ".*\\sTODO\\s(?!\\s*[A-Z]+-\\d+).*"
        private val TODO_REGEX = Regex(TODO_PATTERN)
    }
}
