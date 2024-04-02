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
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty

/**
 * A rule to detekt to-do comments without a Jira task.
 * @active
 */
class TodoWithoutTask(
    ruleSetConfig: Config
) : Rule(ruleSetConfig) {

    private val deprecatedPrefixes: List<String> by config(defaultValue = emptyList())

    override val issue: Issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "This rule reports when a TODO comment is missing a task number.",
        Debt.TEN_MINS
    )

    override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
        declaration.docComment?.let { reportIfInvalid(it) }
        super.visitObjectDeclaration(declaration)
    }

    override fun visitClass(klass: KtClass) {
        klass.docComment?.let { reportIfInvalid(it) }
        super.visitClass(klass)
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        function.docComment?.let { reportIfInvalid(it) }
        super.visitNamedFunction(function)
    }

    override fun visitProperty(property: KtProperty) {
        property.docComment?.let { reportIfInvalid(it) }
        super.visitProperty(property)
    }

    override fun visitComment(comment: PsiComment) {
        reportIfInvalid(comment)

        super.visitComment(comment)
    }

    private fun reportIfInvalid(comment: PsiComment) {
        if (comment.text.contains("TODO")) {
            val match = TODO_REGEX.find(comment.text)
            if (match == null) {
                reportInvalid(comment, "A comment with TODO is missing a Jira task number.")
            } else {
                val task = match.groupValues[MATCH_IDX_TASK].trim()
                val project = match.groupValues[MATCH_IDX_PROJECT]
                val number = match.groupValues[MATCH_IDX_NUMBER].toIntOrNull()

                if (number == null || number == 0) {
                    reportInvalid(
                        comment,
                        "A comment with TODO is missing a valid Jira task number; using $task is cheating."
                    )
                } else if (project in deprecatedPrefixes) {
                    reportInvalid(
                        comment,
                        "A comment with TODO is using an old Jira project identifier: $task."
                    )
                }
            }
        }
    }

    private fun reportInvalid(
        comment: PsiComment,
        message: String
    ) {
        report(
            CodeSmell(
                issue,
                Entity.from(comment),
                message
            )
        )
    }

    companion object {
        private const val MATCH_IDX_TASK = 1
        private const val MATCH_IDX_PROJECT = 2
        private const val MATCH_IDX_NUMBER = 3
        private const val TODO_PATTERN = ".*\\sTODO\\s(\\s*([A-Z]+)-(\\d+))(\\s.*)?"
        private val TODO_REGEX = Regex(TODO_PATTERN)
    }
}
