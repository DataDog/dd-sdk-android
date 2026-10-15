/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.ext

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaIntersectionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * The explicit receiver of a call (e.g. `foo` in `foo.bar()`).
 */
internal sealed class ExplicitReceiver {

    abstract val typeName: String?

    /**
     * A class, object or package used as a receiver (e.g. `String` in `String.format()`).
     */
    data class Qualifier(override val typeName: String) : ExplicitReceiver()

    /**
     * An expression used as a receiver (e.g. `"%s"` in `"%s".format()`).
     */
    data class Expression(val expression: KtExpression, override val typeName: String?) : ExplicitReceiver()
}

internal fun KtCallExpression.explicitReceiverExpression(): KtExpression? {
    val qualifiedExpression = parent as? KtQualifiedExpression ?: return null
    return if (qualifiedExpression.selectorExpression == this) qualifiedExpression.receiverExpression else null
}

/**
 * @param calleeSymbol the called function, used to pick the relevant type when the receiver has several
 * (e.g. a smart cast or `super`)
 */
internal fun KaSession.explicitReceiver(
    expression: KtCallExpression,
    calleeSymbol: KaCallableSymbol,
    treatGenericAsSuper: Boolean,
    includeTypeArguments: Boolean
): ExplicitReceiver? {
    val receiverExpression = expression.explicitReceiverExpression() ?: return null
    val qualifierName = qualifierName(receiverExpression)
    return if (qualifierName != null) {
        ExplicitReceiver.Qualifier(qualifierName)
    } else {
        val candidateTypes = if (receiverExpression is KtSuperExpression) {
            receiverExpression.getStrictParentOfType<KtClassOrObject>()?.classSymbol?.superTypes.orEmpty()
        } else {
            receiverExpression.expressionType?.let { type ->
                (type as? KaIntersectionType)?.conjuncts ?: listOf(type)
            }.orEmpty()
        }
        // Convert all types to nonNullable
        val typeName = selectReceiverType(candidateTypes, calleeSymbol)
            ?.lowerBoundIfFlexible()
            ?.withNullability(false)
            ?.let { fqTypeName(it, treatGenericAsSuper, includeTypeArguments) }
        ExplicitReceiver.Expression(receiverExpression, typeName)
    }
}

/**
 * @return the type of the receiver of the call, either explicit or implicit (e.g. `this`)
 */
internal fun KaSession.receiverTypeName(
    expression: KtCallExpression,
    call: KaCallableMemberCall<*, *>,
    treatGenericAsSuper: Boolean,
    includeTypeArguments: Boolean
): String? {
    val calleeSymbol = call.partiallyAppliedSymbol.signature.symbol
    val explicitReceiver = explicitReceiver(expression, calleeSymbol, treatGenericAsSuper, includeTypeArguments)
    if (explicitReceiver != null) {
        return explicitReceiver.typeName
    }

    if ((calleeSymbol as? KaNamedFunctionSymbol)?.isStatic == true) {
        // A static member called without qualifier (e.g. statically imported or inherited) belongs to its class
        return calleeSymbol.callableId?.classId?.asSingleFqName()?.asString()
    }

    val symbol = call.partiallyAppliedSymbol
    val implicitReceiver = listOf(symbol.extensionReceiver, symbol.dispatchReceiver)
        .map { if (it is KaSmartCastedReceiverValue) it.original else it }
        .firstOrNull { it is KaImplicitReceiverValue }
    return implicitReceiver?.type?.fqClassName()
}

/**
 * Picks the type declaring the called function among the possible types of a receiver,
 * e.g. `Future` for `task.get()` when `task` is a `Runnable` smart cast to a `Future`.
 */
private fun KaSession.selectReceiverType(candidateTypes: List<KaType>, calleeSymbol: KaCallableSymbol): KaType? {
    if (candidateTypes.size <= 1) return candidateTypes.firstOrNull()
    val ownerClassId = calleeSymbol.callableId?.classId
        ?: (calleeSymbol.receiverParameter?.returnType?.lowerBoundIfFlexible() as? KaClassType)?.classId
    val classTypes = candidateTypes.filterIsInstance<KaClassType>()
    return classTypes.firstOrNull { type ->
        ownerClassId != null &&
            (listOf(type) + type.allSupertypes).any { (it as? KaClassType)?.classId == ownerClassId }
    } ?: classTypes.firstOrNull() ?: candidateTypes.first()
}

private fun KaSession.qualifierName(receiverExpression: KtExpression): String? {
    val nameExpression = when (receiverExpression) {
        is KtNameReferenceExpression -> receiverExpression
        is KtDotQualifiedExpression -> receiverExpression.selectorExpression as? KtNameReferenceExpression
        else -> null
    } ?: return null

    val reference = nameExpression.references.filterIsInstance<KtReference>().firstOrNull() ?: return null
    return when (val symbol = reference.resolveToSymbol()) {
        is KaClassLikeSymbol -> {
            val classId = symbol.classId
            // `String.format()` references the companion object implicitly, report the class itself
            val qualifierClassId = if (reference.isImplicitReferenceToCompanion()) classId?.outerClassId else classId
            qualifierClassId?.asSingleFqName()?.asString()
        }

        is KaPackageSymbol -> symbol.fqName.asString()
        else -> null
    }
}
