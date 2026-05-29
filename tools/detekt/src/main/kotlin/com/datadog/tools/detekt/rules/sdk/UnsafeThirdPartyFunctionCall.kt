/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import com.datadog.tools.detekt.rules.AbstractCallExpressionRule
import com.datadog.tools.detekt.rules.sdk.rule.thirdparty.CodeParser
import com.datadog.tools.detekt.rules.sdk.rule.thirdparty.CodeParser.KtMethodParameter
import com.datadog.tools.detekt.rules.sdk.rule.thirdparty.DetektConfigParser
import com.datadog.tools.detekt.rules.sdk.rule.thirdparty.DetektConfigValidator
import com.datadog.tools.detekt.rules.sdk.rule.thirdparty.SignatureRule
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
import java.util.Stack

/**
 * Reports any call to a "third party" function that is considered unsafe (i.e. could throw an
 * exception). Third party functions are detected based on an internal package prefix: any method
 * with that prefix is treated as first party.
 *
 * The decision logic lives in this rule. Config-string parsing is delegated to [DetektConfigParser]
 * and PSI-level extraction to [CodeParser];
 * both feed into [com.datadog.tools.detekt.rules.sdk.rule.thirdparty.SignatureRule] which is the
 * single abstraction matching/validating either direct or wildcarded YAML records.
 */
@RequiresTypeResolution
class UnsafeThirdPartyFunctionCall(
    config: Config
) : AbstractCallExpressionRule(config, includeTypeArguments = false) {

    private val codeParser = CodeParser()
    private val ruleParser = DetektConfigParser()
    private val configValidator = DetektConfigValidator()
    private val internalPackagePrefix: String by config(defaultValue = "")
    private val treatUnknownFunctionAsThrowing: Boolean by config(defaultValue = true)
    private val knownSafeAndroidCalls: List<SignatureRule> by config(emptyList(), ruleParser::parseSafeCalls)
    private val knownSafeThirdPartyCalls: List<SignatureRule> by config(emptyList(), ruleParser::parseSafeCalls)
    private val knownThrowingCalls: List<SignatureRule> by config(emptyList(), ruleParser::parseThrowingCalls)
    private val knownSafeCalls: List<SignatureRule> by lazy { knownSafeAndroidCalls + knownSafeThirdPartyCalls }
    private val caughtExceptions = Stack<Set<String>>()

    // Exact entries are looked up via O(1) map; wildcards fall through to list scan.
    // Throwing category is always checked before safe category (preserves original semantics).
    private val exactThrowingCalls: Map<String, SignatureRule>
    private val wildcardThrowingCalls: List<SignatureRule>
    private val exactSafeCalls: Map<String, SignatureRule>
    private val wildcardSafeCalls: List<SignatureRule>

    override val issue: Issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "This rule reports when a call to an unsafe third party method is made " +
            "(i.e. a function throwing an uncaught exception).",
        Debt.TWENTY_MINS
    )

    init {
        configValidator.validate(knownSafeCalls, knownThrowingCalls)
        exactThrowingCalls = knownThrowingCalls.filter { it.canUseExactLookup }.associateBy { it.source }
        wildcardThrowingCalls = knownThrowingCalls.filterNot { it.canUseExactLookup }
        exactSafeCalls = knownSafeCalls.filter { it.canUseExactLookup }.associateBy { it.source }
        wildcardSafeCalls = knownSafeCalls.filterNot { it.canUseExactLookup }
    }

    override fun visitTryExpression(expression: KtTryExpression) {
        caughtExceptions.push(codeParser.parseCaughtTypes(expression, bindingContext))
        super.visitTryExpression(expression)
        caughtExceptions.pop()
    }

    @Suppress("ReturnCount")
    override fun visitResolvedFunctionCall(
        expression: KtCallExpression,
        resolvedCall: ResolvedFunCall
    ) {
        if (resolvedCall.functionName in kotlinHelperMethods ||
            resolvedCall.isBelongsToInternalPrefix(internalPackagePrefix)
        ) {
            return
        }

        val params = codeParser.parseFormalParams(expression, bindingContext)
        classifyAndReport(expression, signature = resolvedCall.call, params = params)
    }

    @Suppress("ReturnCount")
    private fun classifyAndReport(expression: KtCallExpression, signature: String, params: List<KtMethodParameter>) {
        val throwingMatch = exactThrowingCalls[signature] ?: wildcardThrowingCalls.firstOrNull { it.matches(signature) }
        if (throwingMatch != null) {
            throwingMatch.validate(params)
            checkCallThrowingExceptions(expression, signature, throwingMatch.exceptions)
            return
        }

        val safeMatch = exactSafeCalls[signature] ?: wildcardSafeCalls.firstOrNull { it.matches(signature) }
        if (safeMatch != null) {
            safeMatch.validate(params)
            return
        }

        if (treatUnknownFunctionAsThrowing) {
            reportUnsafeCall(
                expression,
                "Calling $signature could throw exceptions, but this method is unknown"
            )
        }
    }

    private fun checkCallThrowingExceptions(
        expression: KtCallExpression,
        call: String,
        exceptions: List<String>
    ) {
        val catchesAnyException = caughtExceptions.any { list -> list.any { e -> e in topLevelExceptions } }
        val catchesAnyError = caughtExceptions.any { list -> list.any { e -> e in topLevelErrors } }
        val uncaught = exceptions.filter { exception -> caughtExceptions.none { it.contains(exception) } }.filter {
            val isUncaughtException = !catchesAnyException && it.endsWith("Exception")
            val isUncaughtError = !catchesAnyError && it.endsWith("Error")
            isUncaughtException || isUncaughtError
        }

        if (uncaught.isEmpty()) return

        reportUnsafeCall(
            expression = expression,
            message = "Calling $call can throw the following exceptions: ${uncaught.joinToString()}."
        )
    }

    private fun reportUnsafeCall(expression: KtCallExpression, message: String) {
        report(CodeSmell(issue, Entity.from(expression), message = message + WILDCARD_CONFIG_RULES))
    }

    companion object {
        private const val WILDCARD_CONFIG_RULES =
            " Config wildcard rules: '*' matches non-nullable generic, Any, or java.lang.Object parameters only; " +
                "'?' matches both nullable and non-nullable generic, Any, or java.lang.Object parameters. " +
                "'?' covers '*', but '*' does not cover nullable types."
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

        private fun ResolvedFunCall.isBelongsToInternalPrefix(
            internalPackagePrefix: String
        ): Boolean {
            val isInternal =
                containerFqName.startsWith(internalPackagePrefix) || containingPackage.startsWith(internalPackagePrefix)

            return internalPackagePrefix.isNotEmpty() && isInternal
        }
    }
}
