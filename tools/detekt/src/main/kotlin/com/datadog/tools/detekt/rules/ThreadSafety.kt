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
import io.gitlab.arturbosch.detekt.rules.fqNameOrNull
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.types.SimpleType

/**
 * A rule to ensure thread safety is ensured.
 * This rule is based on Android's thread information annotations (e.g.: `@MainThread`) to prevent
 * any method call that would cause an operation to be ran on the wrong thread group.
 * @active
 */
class ThreadSafety : Rule() {

    private enum class ThreadGroup(
        val className: String?
    ) {
        UNKNOWN(null),
        ANY("AnyThread"),
        MAIN("MainThread"),
        UI("UiThread"),
        WORKER("WorkerThread"),
        JS_INTERFACE("JavascriptInterface");

        fun asAnnotation(): String {
            return if (this == UNKNOWN) {
                "null"
            } else {
                "@$className"
            }
        }

        companion object {
            internal val allowedCalls: Map<ThreadGroup, Array<ThreadGroup>> = mapOf(
                UNKNOWN to arrayOf(UNKNOWN, ANY),
                ANY to arrayOf(UNKNOWN, ANY),
                MAIN to arrayOf(UNKNOWN, ANY, MAIN, UI),
                UI to arrayOf(UNKNOWN, ANY, MAIN, UI),
                WORKER to arrayOf(UNKNOWN, ANY, WORKER),
                JS_INTERFACE to arrayOf(UNKNOWN, ANY, WORKER)
            )
        }
    }

    private var parentFunGroup: ThreadGroup = ThreadGroup.UNKNOWN

    // region Rule

    override val issue = Issue(
        javaClass.simpleName,
        Severity.Security,
        "This rule reports when a method is called from the wrong thread.",
        Debt.TWENTY_MINS
    )

    override fun visitKtFile(file: KtFile) {
        if (bindingContext == BindingContext.EMPTY) {
            println("Missing BindingContext when checking file:${file.virtualFilePath}")
            return
        }
        super.visitKtFile(file)
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        if (bindingContext == BindingContext.EMPTY) {
            return
        }

        parentFunGroup = function.annotationEntries.mapNotNull {
            it.shortName?.asString()?.toMethodGroup()
        }.firstOrNull() ?: ThreadGroup.UNKNOWN

        super.visitNamedFunction(function)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        if (bindingContext == BindingContext.EMPTY) {
            println("No binding context :/")
            return
        }

        checkCallExpression(expression)

        super.visitCallExpression(expression)
    }

    // endregion

    // region Internal

    private fun Iterable<AnnotationDescriptor>.extractMethodGroup(): ThreadGroup {
        return mapNotNull {
            val type = it.type
            when (type) {
                is SimpleType -> {
                    val typeName = try {
                        type.fqNameOrNull()?.shortName()?.asString()
                    } catch (e: IllegalStateException) {
                        System.err.println(e.message)
                        null
                    }
                    if (typeName == null) {
                        println("\nUNABLE to get annotation name for $it")
                    }
                    typeName?.toMethodGroup()
                }
                else -> {
                    println("\nUnknown type class for $type (${type.javaClass})")
                    null
                }
            }
        }
            .firstOrNull() ?: ThreadGroup.UNKNOWN
    }

    private fun String.toMethodGroup(): ThreadGroup? {
        return ThreadGroup.values()
            .firstOrNull { it.className == this }
    }

    private fun checkCallExpression(expression: KtCallExpression) {
        val resolvedCall = expression.getResolvedCall(bindingContext) ?: return
        val callDescriptor = resolvedCall.candidateDescriptor

        val calleeGroup = callDescriptor.annotations.extractMethodGroup()
        val allowedCall = ThreadGroup.allowedCalls[parentFunGroup]?.contains(calleeGroup)

        if (allowedCall != true) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Calling a ${calleeGroup.asAnnotation()} annotated fun " +
                        "from a ${parentFunGroup.asAnnotation()} annotated fun " +
                        "could lead to unexpected behavior and must be avoided."
                )
            )
        }
    }

    // endregion
}
