/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules

import com.datadog.tools.detekt.ext.fqReceiverTypeName
import com.datadog.tools.detekt.ext.fqTypeName
import com.datadog.tools.detekt.ext.isExplicit
import dev.detekt.api.Config
import dev.detekt.api.RequiresAnalysisApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitInvokeCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * An abstract Detekt rule resolving function calls.
 *
 * This class resolves the types involved (receiver/parent class, function name, argument types)
 * and delegates the handling of the call to the child implementation.
 *
 * @param ruleSetConfig the detekt ruleSet configuration
 * @param description the description of the rule
 * @param simplifyLocalTypes if a call in class `com.example.foo.A` uses type `com.example.foo.B`,
 * refer to the latter as `B`
 * @param treatGenericAsSuper if a call uses a generic (e.g. <T: java.io.Closeable>),
 * replaces T by the super type.
 * @param includeTypeArguments includes the type argument in the signature
 * (e.g. if false, the type `List<String>` would only be reported as `List`)
 */
abstract class AbstractCallExpressionRule(
    ruleSetConfig: Config,
    description: String,
    private val simplifyLocalTypes: Boolean = false,
    private val treatGenericAsSuper: Boolean = true,
    private val includeTypeArguments: Boolean = true
) : AbstractTypedRule(ruleSetConfig, description), RequiresAnalysisApi {

    /**
     * A representation for a function with resolved types.
     * @param call the call signature
     * @param containerFqName the fully qualified named of the container (class or package)
     * @param functionName the function name without prefix
     * @param containingPackage the package where the function is declared
     * @param arguments the list of argument types
     */
    data class ResolvedFunCall(
        val call: String,
        val containerFqName: String,
        val functionName: String,
        val containingPackage: String,
        val arguments: List<String?>
    )

    // region Rule

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val resolvedFunctionCall = analyze(expression) { resolveFunctionCall(expression) } ?: return

        visitResolvedFunctionCall(expression, resolvedFunctionCall)
    }

    // endregion

    // region Internal

    @Suppress("ReturnCount")
    private fun KaSession.resolveFunctionCall(expression: KtCallExpression): ResolvedFunCall? {
        val call = expression.resolveToCall()?.singleFunctionCallOrNull()
        if (call == null) {
            println("Cannot resolve call for ${expression.text}. Is classpath complete?")
            return null
        }

        val returnType = expression.expressionType
            ?.let { fqTypeName(it, treatGenericAsSuper, includeTypeArguments) }
            ?: return null

        val receiverType = resolveReceiverType(call)
        val symbol = call.symbol
        val callContainingPackage = symbol.callableId?.packageName?.asString().orEmpty()

        val (containerFqName, functionName) = if (symbol is KaConstructorSymbol) {
            returnType to "constructor"
        } else {
            val calleeName = if (call is KaImplicitInvokeCall) {
                // Invoking a function value (`messageBuilder()` for a `() -> String` parameter) says
                // nothing about third party API surface: the callee is whatever the caller supplied,
                // and the lambda's body is analysed where it is declared. Name it after `invoke` rather
                // than after the variable holding it, so callers can filter it out.
                INVOKE_FUN_NAME
            } else {
                expression.calleeExpression?.text ?: "UNKNOWNFUN"
            }
            // Statics and companion members have no value receiver, but are named after the qualifier
            // written at the call site, i.e. their declaring class. Top level functions have no
            // declaring class and are named after their package instead.
            val container = receiverType
                ?: symbol.callableId?.classId?.asFqNameString()
                ?: callContainingPackage
            container to calleeName
        }

        val arguments = resolveParameterTypes(call, containerFqName)

        return ResolvedFunCall(
            call = "$containerFqName.$functionName(${arguments.joinToString(", ")})",
            containerFqName = containerFqName,
            functionName = functionName,
            containingPackage = callContainingPackage,
            arguments = arguments
        )
    }

    /**
     * Resolves the type of the call's receiver, preferring the receiver explicitly written at the
     * call site over an implicit one, so that a call on a subclass is reported on the subclass.
     */
    private fun KaSession.resolveReceiverType(call: KaFunctionCall<*>): String? {
        val receivers = listOfNotNull(call.dispatchReceiver, call.extensionReceiver)
        return (receivers.filter { it.isExplicit() } + receivers.filterNot { it.isExplicit() })
            .asSequence()
            .map { fqReceiverTypeName(it, treatGenericAsSuper, includeTypeArguments) }
            // A type qualifier written at the call site (the `Thread` of `Thread.currentThread()`) is
            // reported as an explicit receiver typed `Unit`, because it references a type rather than
            // a value. It carries no receiver type; such calls are named after their declaring class.
            .firstOrNull { it != UNIT_TYPE }
    }

    private fun KaSession.resolveParameterTypes(
        call: KaFunctionCall<*>,
        containerFqName: String
    ): List<String> {
        // the signature's parameters have their generics substituted with the types used at the call
        // site, e.g. `listOf(window)` resolves to `listOf(android.view.Window)`
        return call.signature.valueParameters.map {
            parameterType(renderParameterType(it), containerFqName)
        }
    }

    /**
     * Renders the type of a single parameter. A `vararg` parameter is declared as an array, and is
     * reported as such, even though the Analysis API exposes its element type instead.
     */
    private fun KaSession.renderParameterType(
        parameter: KaVariableSignature<KaValueParameterSymbol>
    ): String {
        val rendered = fqTypeName(parameter.returnType, treatGenericAsSuper, includeTypeArguments)
        if (!parameter.symbol.isVararg) return rendered

        // Only a parameter *declared* with a primitive element type is a specialised array. A generic
        // one stays a boxing `Array` even when the call site substitutes a primitive, so the declared
        // type decides, e.g. `byteArrayOf(vararg Byte)` is a ByteArray but `setOf(vararg T)` is not.
        val declaredElement = fqTypeName(
            parameter.symbol.returnType,
            treatGenericAsSuper = false,
            includeTypeArguments = false
        )
        return primitiveArrays[declaredElement]
            ?: if (includeTypeArguments) "$ARRAY_TYPE<$rendered>" else ARRAY_TYPE
    }

    private fun parameterType(fullType: String, containerFqName: String): String {
        val (nonNullType, suffix) = if (fullType.endsWith('?')) {
            fullType.substringBeforeLast('?') to "?"
        } else {
            fullType to ""
        }
        return if (simplifyLocalTypes) {
            val containerMatch = containerFqNameRegex.matchEntire(containerFqName)
            val typeMatch = containerFqNameRegex.matchEntire(nonNullType)
            if (typeMatch != null && containerMatch != null) {
                val typePackageName = typeMatch.groupValues[1]
                val containerPackageName = containerMatch.groupValues[1]
                if (nonNullType.startsWith(containerFqName)) {
                    // type is a child of the container class, use the simple name
                    nonNullType.substringAfterLast('.') + suffix
                } else if (typePackageName == containerPackageName) {
                    // type is in the same package, only use the full local name
                    nonNullType.substring(typePackageName.length + 1) + suffix
                } else {
                    fullType
                }
            } else {
                fullType
            }
        } else {
            fullType
        }
    }

    // endregion

    // region AbstractCallExpressionRule

    /**
     * Callback when visiting a function call with resolved types.
     *
     * @param expression the visited expression
     * @param resolvedCall the description of the call
     */
    protected abstract fun visitResolvedFunctionCall(
        expression: KtCallExpression,
        resolvedCall: ResolvedFunCall
    )

    // endregion

    companion object {
        private const val UNIT_TYPE = "kotlin.Unit"
        private const val INVOKE_FUN_NAME = "invoke"
        private const val ARRAY_TYPE = "kotlin.Array"
        private val containerFqNameRegex = Regex("^([a-z0-9-]+(\\.[a-z0-9-]+)*)((\\.[A-Z][a-zA-Z0-9-]+)*)$")

        private val primitiveArrays = mapOf(
            "kotlin.Boolean" to "kotlin.BooleanArray",
            "kotlin.Byte" to "kotlin.ByteArray",
            "kotlin.Char" to "kotlin.CharArray",
            "kotlin.Double" to "kotlin.DoubleArray",
            "kotlin.Float" to "kotlin.FloatArray",
            "kotlin.Int" to "kotlin.IntArray",
            "kotlin.Long" to "kotlin.LongArray",
            "kotlin.Short" to "kotlin.ShortArray"
        )
    }
}
