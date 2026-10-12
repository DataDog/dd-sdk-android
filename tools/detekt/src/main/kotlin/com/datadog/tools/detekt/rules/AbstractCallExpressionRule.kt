/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules

import com.datadog.tools.detekt.ext.fqTypeName
import com.datadog.tools.detekt.ext.receiverTypeName
import dev.detekt.api.Config
import dev.detekt.api.RequiresAnalysisApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.util.OperatorNameConventions

/**
 * An abstract Detekt rule resolving function calls.
 *
 * This class resolves the types involved (receiver/parent class, function name, argument types)
 * and delegates the handling of the call to the child implementation.
 *
 * @param config the detekt rule configuration
 * @param description the description of the rule
 * @param simplifyLocalTypes if a call in class `com.example.foo.A` uses type `com.example.foo.B`,
 * refer to the latter as `B`
 * @param treatGenericAsSuper if a call uses a generic (e.g. <T: java.io.Closeable>),
 * replaces T by the super type.
 * @param includeTypeArguments includes the type argument in the signature
 * (e.g. if false, the type `List<String>` would only be reported as `List`)
 */
abstract class AbstractCallExpressionRule(
    config: Config,
    description: String,
    private val simplifyLocalTypes: Boolean = false,
    private val treatGenericAsSuper: Boolean = true,
    private val includeTypeArguments: Boolean = true
) : AbstractTypedRule(config, description), RequiresAnalysisApi {

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

        val resolvedFunctionCall = analyze(expression) { resolveFunctionCall(expression) }
        if (resolvedFunctionCall != null) {
            visitResolvedFunctionCall(expression, resolvedFunctionCall)
        }
    }

    @Suppress("ReturnCount")
    private fun KaSession.resolveFunctionCall(expression: KtCallExpression): ResolvedFunCall? {
        val call = expression.resolveToCall()?.successfulFunctionCallOrNull()
        if (call == null) {
            println("Cannot resolve call for ${expression.text}. Is classpath complete?")
            return null
        }
        val expressionType = expression.expressionType ?: return null
        val returnType = fqTypeName(expressionType, treatGenericAsSuper, includeTypeArguments)

        // Calling a value with a function type (e.g. `callback()`) is an implicit call to its `invoke` function
        val isImplicitInvoke = (call as? KaSimpleFunctionCall)?.isImplicitInvoke == true
        val receiverType = if (isImplicitInvoke) {
            call.partiallyAppliedSymbol.dispatchReceiver?.type
                ?.let {
                    fqTypeName(
                        it.lowerBoundIfFlexible().withNullability(false),
                        treatGenericAsSuper,
                        includeTypeArguments
                    )
                }
        } else {
            receiverTypeName(expression, call, treatGenericAsSuper, includeTypeArguments)
        }

        val symbol = call.partiallyAppliedSymbol.signature.symbol
        // Calls to a typealias constructor (e.g. `HashMap()`) are reported as functions in the typealias' package
        val typeAlias = (expressionType as? KaClassType)?.abbreviation
        val containingPackage = if (symbol is KaConstructorSymbol) {
            (typeAlias?.classId ?: symbol.containingClassId)?.packageFqName?.asString().orEmpty()
        } else {
            symbol.callableId?.packageName?.asString().orEmpty()
        }
        val (containerFqName, functionName) = if (symbol is KaConstructorSymbol && typeAlias == null) {
            returnType to "constructor"
        } else {
            val calleeName = if (isImplicitInvoke) {
                OperatorNameConventions.INVOKE.asString()
            } else {
                expression.calleeExpression?.node?.text ?: "UNKNOWNFUN"
            }
            (receiverType ?: containingPackage) to calleeName
        }

        val arguments = resolveParameterTypes(call, containerFqName)
        return ResolvedFunCall(
            call = "$containerFqName.$functionName(${arguments.joinToString(", ")})",
            containerFqName = containerFqName,
            functionName = functionName,
            containingPackage = containingPackage,
            arguments = arguments
        )
    }

    private fun KaSession.resolveParameterTypes(
        call: KaFunctionCall<*>,
        containerFqName: String
    ): List<String> {
        return call.partiallyAppliedSymbol.signature.valueParameters
            .map { parameterType(it, containerFqName) }
    }

    private fun KaSession.parameterType(
        parameter: KaVariableSignature<KaValueParameterSymbol>,
        containerFqName: String
    ): String {
        val fullType = if (parameter.symbol.isVararg) {
            varargTypeName(declaredElementType = parameter.symbol.returnType, elementType = parameter.returnType)
        } else {
            fqTypeName(parameter.returnType, treatGenericAsSuper, includeTypeArguments)
        }
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

    /**
     * The type of a vararg parameter is its element type, wrap it in the matching array type.
     * Only parameters declared with a primitive type use a primitive array (e.g. `vararg Int` is an `IntArray`),
     * a generic parameter is always an `Array` (e.g. `vararg T` is an `Array<T>`, even when `T` is `Int`).
     */
    private fun KaSession.varargTypeName(declaredElementType: KaType, elementType: KaType): String {
        val declaredClassId = (declaredElementType.lowerBoundIfFlexible() as? KaClassType)?.classId
        if (declaredClassId == StandardClassIds.Array ||
            declaredClassId in StandardClassIds.primitiveArrayTypeByElementType.values
        ) {
            // already an array type
            return fqTypeName(elementType, treatGenericAsSuper, includeTypeArguments)
        }
        val primitiveArrayClassId = StandardClassIds.primitiveArrayTypeByElementType[declaredClassId]
        val arrayTypeName = if (primitiveArrayClassId != null && !declaredElementType.isMarkedNullable) {
            primitiveArrayClassId.asSingleFqName().asString()
        } else if (includeTypeArguments) {
            val elementTypeName = fqTypeName(elementType, treatGenericAsSuper)
            "${StandardClassIds.Array.asSingleFqName()}<$elementTypeName>"
        } else {
            StandardClassIds.Array.asSingleFqName().asString()
        }
        // A Java vararg is a platform array, which can be null
        return if (declaredElementType is KaFlexibleType) "$arrayTypeName?" else arrayTypeName
    }

    companion object {
        private val containerFqNameRegex = Regex("^([a-z0-9-]+(\\.[a-z0-9-]+)*)((\\.[A-Z][a-zA-Z0-9-]+)*)$")
    }
}
