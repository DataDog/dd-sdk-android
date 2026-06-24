/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import com.datadog.android.internal.lint.HotMethod
import com.datadog.tools.detekt.rules.AbstractCallExpressionRule
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

/**
 * Detects heap allocations and O(N) collection operations inside methods annotated with `@HotMethod`.
 *
 * Hot methods are called on every frame or touch event. Flagged patterns:
 * - Constructor calls (`ArrayList()`, `StringBuilder()`, any `PascalCase()`)
 * - Anonymous object expressions (`object : Runnable { ... }`)
 * - Lambda / closure literals `{ }` that create a function object
 * - String templates `"$variable"` that allocate a `StringBuilder`
 * - Collection/builder factory functions (`mutableListOf`, `buildString`, etc.)
 * - O(N) collection operations (`find`, `forEach`, `filter`, `map`, `indexOf`, etc.)
 *
 * All of the above allocate on every invocation, creating GC pressure and risking UI jank.
 * Use index-based for-loops, pre-allocated fields, and pre-computed data instead.
 *
 * Entries in `forbiddenCalls` and `forbiddenFactoryFunctions` support three formats:
 * - `forEach`                         — method name only, any receiver
 * - `List.forEach`                    — any type whose FQN ends with `List`
 * - `kotlin.collections.List.forEach` — exact FQN match
 *
 * `allowedInlineFunctions` lists callee names whose lambda arguments are inlined by the
 * compiler and therefore do not allocate. Add any project-specific inline functions here.
 *
 * Configure all lists in detekt_expensive_methods.yml under `datadog > HotMethodIllegalCall`.
 *
 * @active
 */
@RequiresTypeResolution
class HotMethodIllegalCall(
    config: Config = Config.empty
) : AbstractCallExpressionRule(config, includeTypeArguments = false) {

    override val issue: Issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "Heap allocations and O(N) collection operations are forbidden inside @HotMethod functions. " +
            "They run on every frame or touch event and cause GC pressure. " +
            "Use index-based for-loops, pre-allocated fields, and pre-computed data instead.",
        Debt.TWENTY_MINS
    )

    private val forbiddenCalls: List<String> by config(defaultValue = emptyList())
    private val forbiddenFactoryFunctions: List<String> by config(defaultValue = emptyList())
    private val allowedInlineFunctions: List<String> by config(defaultValue = emptyList())

    private val forbiddenCallsSet: Set<String> by lazy { forbiddenCalls.toSet() }
    private val forbiddenFactoryFunctionsSet: Set<String> by lazy { forbiddenFactoryFunctions.toSet() }
    private val allowedInlineFunctionsSet: Set<String> by lazy { allowedInlineFunctions.toSet() }

    // Each named function pushes its @HotMethod exclude set (null = not hot).
    // Lambdas inherit the enclosing named-function context.
    private val functionDepthStack = ArrayDeque<Set<String>?>()

    private val insideHotMethod: Boolean
        get() = functionDepthStack.lastOrNull() != null

    // Returns true if ANY of the candidate strings is in the current exclude set.
    // Pass the category constant first, then specific names, so a category exclusion
    // catches all variants (e.g. CHECK_CONSTRUCTOR catches any constructor call).
    private fun isExcluded(vararg candidates: String): Boolean {
        val excluded = functionDepthStack.lastOrNull() ?: return false
        return candidates.any { it in excluded }
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        val hotAnnotation = function.annotationEntries.find {
            it.shortName?.asString() == HOT_METHOD_ANNOTATION
        }
        functionDepthStack.addLast(hotAnnotation?.extractExcludedChecks())
        super.visitNamedFunction(function)
        functionDepthStack.removeLast()
    }

    private fun KtAnnotationEntry.extractExcludedChecks(): Set<String> {
        val excludeArg = valueArguments.find { it.getArgumentName()?.asName?.asString() == "exclude" }
            ?: return emptySet()
        val expr = excludeArg.getArgumentExpression() ?: return emptySet()
        return (expr as? KtCollectionLiteralExpression)
            ?.getInnerExpressions()
            ?.filterIsInstance<KtStringTemplateExpression>()
            ?.flatMap { it.entries.filterIsInstance<KtLiteralStringTemplateEntry>() }
            ?.map { it.text }
            ?.toSet()
            ?: emptySet()
    }

    // Constructor calls (PascalCase) are detected via PSI — the type-resolved path treats some
    // Java constructors differently and may not emit functionName=="constructor" reliably.
    override fun visitCallExpression(expression: KtCallExpression) {
        if (insideHotMethod) {
            val calleeName = expression.calleeExpression?.text ?: ""
            if (calleeName.isNotEmpty() && calleeName[0].isUpperCase()) {
                if (!isExcluded(HotMethod.CHECK_CONSTRUCTOR, calleeName)) {
                    report(
                        CodeSmell(
                            issue,
                            Entity.from(expression),
                            "Constructor call `$calleeName()` inside a @HotMethod allocates a new object on every " +
                                "invocation. Move the allocation to a field or pre-allocate outside the hot path."
                        )
                    )
                    return
                }
            }
        }
        super.visitCallExpression(expression)
    }

    override fun visitObjectLiteralExpression(expression: KtObjectLiteralExpression) {
        super.visitObjectLiteralExpression(expression)
        if (!insideHotMethod || isExcluded(HotMethod.CHECK_ANONYMOUS_OBJECT)) return
        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "Anonymous object created inside a @HotMethod allocates a new instance on every " +
                    "invocation. Extract it to a field or a top-level object."
            )
        )
    }

    override fun visitLambdaExpression(expression: KtLambdaExpression) {
        super.visitLambdaExpression(expression)
        if (!insideHotMethod || isExcluded(HotMethod.CHECK_LAMBDA)) return
        if (expression.isInlinedLambda()) return
        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "Lambda literal inside a @HotMethod creates a function object on every invocation. " +
                    "Extract it to a pre-allocated field. If it is passed to a known `inline` function," +
                    " add that function to `allowedInlineFunctions` in the config."
            )
        )
    }

    /**
     * Returns true when the lambda does not result in an object allocation:
     * 1. Its containing call is in `allowedInlineFunctions` (manual bypass), or
     * 2. Its containing call resolves to a Kotlin `inline` function (compiler inlines the body).
     */
    private fun KtLambdaExpression.isInlinedLambda(): Boolean {
        val containingCall = findContainingCallExpression() ?: return false
        val calleeName = containingCall.calleeExpression?.text
        val isAllowlisted = calleeName != null && calleeName in allowedInlineFunctionsSet
        val isTypedInline = bindingContext != BindingContext.EMPTY &&
            (
                containingCall.getResolvedCall(bindingContext)
                    ?.candidateDescriptor as? FunctionDescriptor
                )?.isInline == true
        return isAllowlisted || isTypedInline
    }

    private fun KtLambdaExpression.findContainingCallExpression(): KtCallExpression? {
        // Trailing lambda: KtLambdaExpression -> KtLambdaArgument -> KtCallExpression
        val viaTrailing = parent?.parent as? KtCallExpression
        if (viaTrailing != null) return viaTrailing
        // In-parens lambda: KtLambdaExpression -> KtValueArgument -> KtValueArgumentList -> KtCallExpression
        val viaParens = parent?.parent?.parent as? KtCallExpression
        return viaParens
    }

    override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
        super.visitStringTemplateExpression(expression)
        if (!insideHotMethod || isExcluded(HotMethod.CHECK_STRING_TEMPLATE)) return
        val hasInterpolation = expression.entries.any { it !is KtLiteralStringTemplateEntry }
        if (!hasInterpolation) return
        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "String template with interpolation inside a @HotMethod allocates a `StringBuilder` on every " +
                    "invocation. Pre-compute the string outside the hot path or use a pre-allocated buffer."
            )
        )
    }

    override fun visitResolvedFunctionCall(
        expression: KtCallExpression,
        resolvedCall: ResolvedFunCall
    ) {
        if (!insideHotMethod) return

        val containerFqName = resolvedCall.containerFqName
        val functionName = resolvedCall.functionName

        if (matchesForbiddenEntry(containerFqName, functionName, forbiddenFactoryFunctionsSet) &&
            !isExcluded(HotMethod.CHECK_FACTORY, functionName, "$containerFqName.$functionName")
        ) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "`$containerFqName.$functionName()` allocates a new collection or builder on every " +
                        "invocation. Inside a @HotMethod this causes GC pressure. " +
                        "Pre-allocate outside the hot path and reuse the instance."
                )
            )
            return
        }

        if (matchesForbiddenEntry(containerFqName, functionName, forbiddenCallsSet) &&
            !isExcluded(HotMethod.CHECK_COLLECTION_OPS, functionName, "$containerFqName.$functionName")
        ) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "`$containerFqName.$functionName()` is an O(N) operation. Inside a @HotMethod it runs on every " +
                        "frame or touch event. Use an index-based for-loop or pre-compute outside the hot path."
                )
            )
        }
    }

    /**
     * Matches `containerFqName.functionName` against an entry from the forbidden set.
     *
     * An entry can be:
     * - `forEach`                        — method name only, matches any receiver
     * - `List.forEach`                   — matches any container whose FQN ends with `List`
     * - `kotlin.collections.List.forEach` — exact FQN match
     */
    private fun matchesForbiddenEntry(
        containerFqName: String,
        functionName: String,
        entries: Set<String>
    ): Boolean {
        val qualifiedName = "$containerFqName.$functionName"
        return entries.any { entry ->
            if ('.' !in entry) {
                functionName == entry
            } else {
                qualifiedName == entry || qualifiedName.endsWith(".$entry")
            }
        }
    }

    private companion object {
        private const val HOT_METHOD_ANNOTATION = "HotMethod"
    }
}
