/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import com.datadog.tools.detekt.ext.fqReceiverTypeName
import com.datadog.tools.detekt.rules.AbstractTypedRule
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.config
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * A rule to ensure thread safety is ensured.
 * This rule is based on Android's thread information annotations (e.g.: `@MainThread`) to prevent
 * any method call that would cause an operation to be ran on the wrong thread group.
 * @active
 */
class ThreadSafety(
    ruleSetConfig: Config = Config.empty
) : AbstractTypedRule(ruleSetConfig, "This rule reports when a method is called from the wrong thread."),
    RequiresAnalysisApi {

    private val workerThreadSwitchingCalls: List<String> by config(defaultValue = emptyList())
    private val mainThreadSwitchingCalls: List<String> by config(defaultValue = emptyList())

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

    /**
     * The information resolved for a call, gathered in a single analysis session.
     *
     * @param calleeGroup the thread group the called function is annotated with
     * @param fullNames the fully qualified names the call can be referred to
     */
    private data class ResolvedThreadCall(
        val calleeGroup: ThreadGroup,
        val fullNames: Set<String>
    )

    private val parentFunGroupStack: MutableList<ThreadGroup> = mutableListOf(ThreadGroup.UNKNOWN)

    // region Rule

    override fun visitNamedFunction(function: KtNamedFunction) {
        val parentFunGroup: ThreadGroup = function.annotationEntries.mapNotNull {
            it.shortName?.asString()?.toMethodGroup()
        }.firstOrNull() ?: ThreadGroup.UNKNOWN
        parentFunGroupStack.add(0, parentFunGroup)

        super.visitNamedFunction(function)

        parentFunGroupStack.removeAt(0)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        var wrapCallWith: ThreadGroup? = null
        val resolvedCall = analyze(expression) { resolveThreadCall(expression) }

        if (resolvedCall != null) {
            checkCallExpression(expression, resolvedCall.calleeGroup)

            wrapCallWith = if (workerThreadSwitchingCalls.any { resolvedCall.fullNames.contains(it) }) {
                ThreadGroup.WORKER
            } else if (mainThreadSwitchingCalls.any { resolvedCall.fullNames.contains(it) }) {
                ThreadGroup.MAIN
            } else {
                null
            }
        } else {
            val callee = expression.calleeExpression
            if (callee is KtNameReferenceExpression) {
                val calleeFullType = callee.getReferencedName().resolveFullType()
                if (calleeFullType == "java.lang.Runnable") {
                    wrapCallWith = ThreadGroup.WORKER
                }
            }
            println("Unresolved call expression $expression !!! ")
        }

        if (wrapCallWith != null) {
            parentFunGroupStack.add(0, wrapCallWith)
            super.visitCallExpression(expression)
            parentFunGroupStack.removeAt(0)
        } else {
            super.visitCallExpression(expression)
        }
    }

    // endregion

    // region Internal

    private fun KaSession.resolveThreadCall(expression: KtCallExpression): ResolvedThreadCall? {
        val call = expression.resolveToCall()?.singleFunctionCallOrNull() ?: return null
        return ResolvedThreadCall(
            calleeGroup = extractMethodGroup(call.symbol),
            fullNames = resolveCallFullNames(call)
        )
    }

    private fun extractMethodGroup(symbol: KaAnnotated): ThreadGroup {
        return symbol.annotations.firstNotNullOfOrNull { annotation ->
            val typeName = annotation.classId?.shortClassName?.asString()
            if (typeName == null) {
                println("\nUNABLE to get annotation name for $annotation")
            }
            typeName?.toMethodGroup()
        } ?: ThreadGroup.UNKNOWN
    }

    private fun String.toMethodGroup(): ThreadGroup? {
        return ThreadGroup.entries
            .firstOrNull { it.className == this }
    }

    private fun checkCallExpression(expression: KtCallExpression, calleeGroup: ThreadGroup) {
        val parentFunGroup = parentFunGroupStack.first()

        val allowedCall = ThreadGroup.allowedCalls[parentFunGroup]?.contains(calleeGroup)

        if (allowedCall != true) {
            report(
                Finding(
                    Entity.from(expression),
                    "Calling a ${calleeGroup.asAnnotation()} annotated fun " +
                        "from a ${parentFunGroup.asAnnotation()} annotated fun " +
                        "could lead to unexpected behavior and must be avoided."
                )
            )
        }
    }

    private fun KaSession.resolveCallFullNames(call: KaFunctionCall<*>): Set<String> {
        val callableId = call.symbol.callableId ?: return emptySet()
        val resolvedName = callableId.asSingleFqName().asString()

        val dispatchReceiver = call.dispatchReceiver ?: return setOf(resolvedName)
        val hostType = dispatchReceiver.type
        val hostTypeName = fqReceiverTypeName(dispatchReceiver, includeTypeArguments = false)

        // `callableId` names the class that *declares* the function, but a call is usually configured
        // under the type it is made on — `android.widget.LinearLayout.post` is declared on
        // `android.view.View`. Report both spellings so either one matches.
        val hostTypeCallName = "$hostTypeName.${callableId.callableName.asString()}"

        // when the host type is a type alias, also report the call under the aliased name
        val aliasedName = hostType.abbreviation
            ?.classId
            ?.asFqNameString()
            ?.let { resolvedName.replace(hostTypeName, it) }

        return listOfNotNull(resolvedName, hostTypeCallName, aliasedName).toSet()
    }

    // endregion
}
