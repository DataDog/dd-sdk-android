/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import com.datadog.tools.detekt.ext.fqTypeName
import com.datadog.tools.detekt.rules.AbstractCallExpressionRule
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.resolve.BindingContext
import java.util.Stack

/**
 * This rule will report any call to a "third party" function that is considered unsafe, that is,
 * which could throw an exception.
 *
 * Third party functions are detected based on an internal package prefix: any method which has a
 * package name with this prefix is considered first party, anything else is third party.
 */
@RequiresTypeResolution
class UnsafeThirdPartyFunctionCall(
    config: Config
) : AbstractCallExpressionRule(config, includeTypeArguments = false) {

    private val internalPackagePrefix: String by config(defaultValue = "")
    private val treatUnknownFunctionAsThrowing: Boolean by config(defaultValue = true)
    private val knownThrowingCalls: List<String> by config(defaultValue = emptyList())
    private val knownSafeCalls: List<String> by config(defaultValue = emptyList())

    private val knownThrowingCallsMap: Map<String, List<String>> by lazy {
        knownThrowingCalls.map {
            val splitColon = it.split(':')
            val key = splitColon.first()
            if (splitColon.size == 1) {
                println("✘ ERROR WITH KNOWN THROWING CALL: $it")
            }
            val exceptions = splitColon[1].split(',').toList()
            key to exceptions
        }.toMap()
    }

    private val caughtExceptions = Stack<List<String>>()

    // region Rule

    override val issue: Issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "This rule reports when a call to an unsafe third party method is made " +
            "(i.e. a function throwing an uncaught exception).",
        Debt.TWENTY_MINS
    )

    override fun visitTryExpression(expression: KtTryExpression) {
        val caughtTypes = expression.catchClauses
            .mapNotNull {
                val typeReference = it.catchParameter?.typeReference
                bindingContext.get(BindingContext.TYPE, typeReference)?.fqTypeName()
            }
        caughtExceptions.push(caughtTypes)
        super.visitTryExpression(expression)
        caughtExceptions.pop()
    }

    // endregion

    // region AbstractCallExpressionRule

    @Suppress("ReturnCount")
    override fun visitResolvedFunctionCall(
        expression: KtCallExpression,
        resolvedCall: ResolvedFunCall
    ) {
        if (internalPackagePrefix.isNotEmpty() &&
            resolvedCall.containerFqName.startsWith(internalPackagePrefix)
        ) {
            return
        }

        if (resolvedCall.functionName in kotlinHelperMethods) {
            return
        }

        if (knownThrowingCallsMap.containsKey(resolvedCall.call)) {
            val knownThrowables = knownThrowingCallsMap[resolvedCall.call] ?: emptyList()
            checkCallThrowingExceptions(expression, resolvedCall.call, knownThrowables)
        } else if (treatUnknownFunctionAsThrowing && !knownSafeCalls.contains(resolvedCall.call)) {
            val message = "Calling ${resolvedCall.call} could throw exceptions, but this method is unknown"
            reportUnsafeCall(expression, message)
        }
    }

    // endregion

    // region Internal

    private fun checkCallThrowingExceptions(
        expression: KtCallExpression,
        call: String,
        exceptions: List<String>
    ) {
        val catchesAnyException = caughtExceptions.any { list ->
            list.any { e -> e in topLevelExceptions }
        }
        val catchesAnyError = caughtExceptions.any { list ->
            list.any { e -> e in topLevelErrors }
        }
        val uncaught = exceptions.filter { exception ->
            caughtExceptions.none { it.contains(exception) }
        }
            .filter {
                val isUncaughtException = it.endsWith("Exception") && !catchesAnyException
                val isUncaughtError = it.endsWith("Error") && !catchesAnyError
                isUncaughtException || isUncaughtError
            }

        if (uncaught.isEmpty()) {
            return
        }

        val msg = "Calling $call can throw the following exceptions: ${exceptions.joinToString()}."
        reportUnsafeCall(expression, msg)
    }

    private fun reportUnsafeCall(
        expression: KtCallExpression,
        message: String
    ) {
        report(CodeSmell(issue, Entity.from(expression), message = message))
    }

    // endregion

    companion object {
        private const val JAVA_EXCEPTION_CLASS = "java.lang.Exception"
        private const val JAVA_ERROR_CLASS = "java.lang.Error"
        private const val JAVA_THROWABLE_CLASS = "java.lang.Throwable"
        private const val KOTLIN_EXCEPTION_CLASS = "kotlin.Exception"
        private const val KOTLIN_ERROR_CLASS = "kotlin.Error"
        private const val KOTLIN_THROWABLE_CLASS = "kotlin.Throwable"

        private val topLevelExceptions = arrayOf(
            JAVA_EXCEPTION_CLASS,
            JAVA_THROWABLE_CLASS,
            KOTLIN_EXCEPTION_CLASS,
            KOTLIN_THROWABLE_CLASS
        )

        private val topLevelErrors = arrayOf(
            JAVA_ERROR_CLASS,
            JAVA_THROWABLE_CLASS,
            KOTLIN_ERROR_CLASS,
            KOTLIN_THROWABLE_CLASS
        )

        private val kotlinHelperMethods = arrayOf(
            "let", "run", "with", "apply", "also",
            "print", "println", "toString", "invoke"
        )
    }
}
