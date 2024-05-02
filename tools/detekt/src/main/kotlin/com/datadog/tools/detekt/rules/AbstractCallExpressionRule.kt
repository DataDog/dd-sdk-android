/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules

import com.datadog.tools.detekt.ext.fqTypeName
import com.datadog.tools.detekt.ext.type
import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.containingPackage
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getCall
import org.jetbrains.kotlin.resolve.calls.util.getImplicitReceiverValue
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType

/**
 * An abstract Detekt rule resolving function calls.
 *
 * This class resolves the types involved (receiver/parent class, function name, argument types)
 * and delegates the handling of the call to the child implementation.
 *
 * @param ruleSetConfig the detekt ruleSet configuration
 * @param simplifyLocalTypes if a call in class `com.example.foo.A` uses type `com.example.foo.B`,
 * refer to the latter as `B`
 * @param treatGenericAsSuper if a call uses a generic (e.g. <T: java.io.Closeable>),
 * replaces T by the super type.
 * @param includeTypeArguments includes the type argument in the signature
 * (e.g. if false, the type `List<String>` would only be reported as `List`)
 */
abstract class AbstractCallExpressionRule(
    ruleSetConfig: Config = Config.empty,
    private val simplifyLocalTypes: Boolean = false,
    private val treatGenericAsSuper: Boolean = true,
    private val includeTypeArguments: Boolean = true
) : AbstractTypedRule(ruleSetConfig) {

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

    @Suppress("ReturnCount")
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (bindingContext == BindingContext.EMPTY) {
            return
        }

        val resolvedCall = expression.getResolvedCall(bindingContext)
        if (resolvedCall == null) {
            println("Cannot resolve call for ${expression.getCall(bindingContext)}. Is classpath complete?")
            return
        }
        val call = resolvedCall.call
        val returnType = expression.getType(bindingContext)
            ?.fqTypeName(treatGenericAsSuper, includeTypeArguments)
            ?: return

        val receiverType = listOf(
            call.explicitReceiver,
            call.dispatchReceiver,
            resolvedCall.getImplicitReceiverValue()
        )
            .firstNotNullOfOrNull {
                it?.type(bindingContext, treatGenericAsSuper, includeTypeArguments)
            }

        val callDescriptor = resolvedCall.candidateDescriptor
        val (containerFqName, functionName) = if (callDescriptor is ClassConstructorDescriptor) {
            returnType to "constructor"
        } else {
            val calleeName = call.calleeExpression?.node?.text ?: "UNKNOWNFUN"
            val callContainingPackage = callDescriptor.containingPackage()?.toString().orEmpty()
            if (receiverType == null) {
                callContainingPackage to calleeName
            } else {
                "$receiverType" to calleeName
            }
        }

        val arguments = resolveParameterTypes(resolvedCall, containerFqName)
        val resolvedFunctionCall = ResolvedFunCall(
            call = "$containerFqName.$functionName(${arguments.joinToString(", ")})",
            containerFqName = containerFqName,
            functionName = functionName,
            containingPackage = callDescriptor.containingPackage()?.toString().orEmpty(),
            arguments = arguments
        )

        visitResolvedFunctionCall(expression, resolvedFunctionCall)
    }

    private fun resolveParameterTypes(
        resolvedCall: ResolvedCall<out CallableDescriptor>,
        containerFqName: String
    ): List<String> {
        return resolvedCall.valueArguments
            // Ensure the arguments when named are in original order
            .toSortedMap { p0, p1 -> (p0?.index ?: Int.MAX_VALUE) - (p1?.index ?: -1) }
            .map { it.key.parameterType(containerFqName) }
    }

    private fun ValueParameterDescriptor.parameterType(containerFqName: String): String {
        val fullType = type.fqTypeName(treatGenericAsSuper, includeTypeArguments)
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
        private val containerFqNameRegex = Regex("^([a-z0-9-]+(\\.[a-z0-9-]+)*)((\\.[A-Z][a-zA-Z0-9-]+)*)$")
    }
}
