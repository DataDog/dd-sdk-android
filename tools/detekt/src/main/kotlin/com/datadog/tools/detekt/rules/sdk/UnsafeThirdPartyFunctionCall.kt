/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import com.datadog.tools.detekt.ext.fqTypeName
import com.datadog.tools.detekt.rules.AbstractCallExpressionRule
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.config
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import java.util.Stack

/**
 * This rule will report any call to a "third party" function that is considered unsafe, that is,
 * which could throw an exception.
 *
 * Third party functions are detected based on an internal package prefix: any method which has a
 * package name with this prefix is considered first party, anything else is third party.
 */
class UnsafeThirdPartyFunctionCall(
    config: Config
) : AbstractCallExpressionRule(
    config,
    "This rule reports when a call to an unsafe third party method is made " +
        "(i.e. a function throwing an uncaught exception).",
    includeTypeArguments = false
) {

    private val internalPackagePrefix: String by config(defaultValue = "")
    private val treatUnknownFunctionAsThrowing: Boolean by config(defaultValue = true)
    private val knownThrowingCalls: List<String> by config(defaultValue = emptyList())
    private val knownSafeAndroidCalls: List<String> by config(defaultValue = emptyList())
    private val knownSafeThirdPartyCalls: List<String> by config(defaultValue = emptyList())

    private val knownSafeCalls: Set<String> by lazy {
        (knownSafeAndroidCalls + knownSafeThirdPartyCalls).toSet()
    }

    private val knownThrowingCallsMap: Map<String, List<String>> by lazy {
        knownThrowingCalls.associate {
            val splitColon = it.split(':')
            val key = splitColon.first()
            if (splitColon.size == 1) {
                println("✘ ERROR WITH KNOWN THROWING CALL: $it")
            }
            val exceptions = splitColon[1].split(',').toList()
            key to exceptions
        }
    }

    private val caughtExceptions = Stack<List<String>>()

    // region Rule

    override fun visitTryExpression(expression: KtTryExpression) {
        val caughtTypes = expression.catchClauses
            .mapNotNull {
                val typeReference = it.catchParameter?.typeReference ?: return@mapNotNull null
                analyze(typeReference) { fqTypeName(typeReference.type) }
            }
        // A catch clause only handles the exceptions thrown from the try block, not from catch or finally blocks
        caughtExceptions.push(caughtTypes)
        expression.tryBlock.accept(this)
        caughtExceptions.pop()
        expression.catchClauses.forEach { it.accept(this) }
        expression.finallyBlock?.accept(this)
    }

    // endregion

    // region AbstractCallExpressionRule

    @Suppress("ReturnCount")
    override fun visitResolvedFunctionCall(
        expression: KtCallExpression,
        resolvedCall: ResolvedFunCall
    ) {
        if (internalPackagePrefix.isNotEmpty()) {
            val belongsToInternalContainer = resolvedCall.containerFqName.startsWith(internalPackagePrefix) ||
                resolvedCall.containingPackage.startsWith(internalPackagePrefix)
            if (belongsToInternalContainer) return
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
        val caughtTypes = caughtExceptions.flatten().toSet()
        val uncaught = analyze(expression) {
            exceptions.filterNot { isCaught(it, caughtTypes) }
        }

        if (uncaught.isEmpty()) {
            return
        }

        val msg = "Calling $call can throw the following exceptions: ${exceptions.joinToString()}."
        reportUnsafeCall(expression, msg)
    }

    /**
     * An exception is caught by a catch clause on its own type or on any of its super types
     * (e.g. a `FileNotFoundException` is caught by `catch (e: IOException)`).
     */
    private fun KaSession.isCaught(exception: String, caughtTypes: Set<String>): Boolean {
        if (exception in caughtTypes) return true

        val exceptionClass = findExceptionClass(exception)
        if (exceptionClass == null) {
            // Unknown class (e.g. missing from the classpath), only top level catch clauses can be checked
            val catchesAnyException = caughtTypes.any { it in topLevelExceptions }
            val catchesAnyError = caughtTypes.any { it in topLevelErrors }
            val isUncaughtException = exception.endsWith("Exception") && !catchesAnyException
            val isUncaughtError = exception.endsWith("Error") && !catchesAnyError
            return !isUncaughtException && !isUncaughtError
        }

        return exceptionClass.defaultType.allSupertypes.any {
            (it as? KaClassType)?.classId?.asSingleFqName()?.asString() in caughtTypes
        }
    }

    /**
     * A fully qualified name doesn't tell where the package ends, try each split until the class is found
     * (e.g. `android.content.res.Resources.NotFoundException` is the nested class `Resources.NotFoundException`).
     */
    private fun KaSession.findExceptionClass(exception: String): KaClassSymbol? {
        val segments = exception.split('.')
        return (segments.size - 1 downTo 1).firstNotNullOfOrNull { packageSize ->
            val packageName = FqName.fromSegments(segments.take(packageSize))
            val className = FqName.fromSegments(segments.drop(packageSize))
            findClass(ClassId(packageName, className, isLocal = false))
        }
    }

    private fun reportUnsafeCall(
        expression: KtCallExpression,
        message: String
    ) {
        report(Finding(Entity.from(expression), message))
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
