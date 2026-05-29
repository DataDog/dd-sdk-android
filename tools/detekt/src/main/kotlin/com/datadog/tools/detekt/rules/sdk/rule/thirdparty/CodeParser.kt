/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk.rule.thirdparty

import com.datadog.tools.detekt.ext.fqTypeName
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.types.KotlinType
import java.lang.reflect.Executable

/**
 * Extracts structured Kotlin PSI data needed by the rule: formal parameters of a call expression
 * and the caught-exception types declared by a `try`/`catch` block. It also reflectively inspects
 * the declared exceptions to a resolved JVM member. The rule owns all decision logic - this object
 * only translates PSI nodes and JVM members into plain Kotlin values.
 */
internal class CodeParser {

    // Resolved-class memoization: many call signatures share the same declaring class, so we avoid
    // re-running (and re-failing) Class.forName for every one of them.
    private val resolvedClassesCache: MutableMap<String, Class<*>?> = mutableMapOf<String, Class<*>?>()

    /**
     * Returns the formal parameters of the resolved function call as plain [KtMethodParameter] values,
     * sorted by their declaration order. Returns an empty list if the call cannot be resolved.
     *
     * e.g. `fun <T> MutableList.add(index: Int, element: T)` ->
     * ```
     * [
     *   KtMethodParameter(name="index", type="kotlin.Int",  isGeneric=false),
     *   KtMethodParameter(name="element", type="T",         isGeneric=true)
     * ]
     * ```
     *
     * Used by [SignatureRule.validate] to enforce that wildcard slots only target generic
     * type parameters, not concrete types.
     */
    internal fun parseFormalParams(
        expression: KtCallExpression,
        bindingContext: BindingContext
    ): List<KtMethodParameter> = expression.getResolvedCall(bindingContext)
        ?.valueArguments?.keys
        ?.sortedBy { it.index }
        ?.map(::toKtMethodParameter)
        .orEmpty()

    /**
     * Returns the fully-qualified exception type names declared in all `catch` clauses of [expression].
     * e.g. `try { } catch (e: IOException) { }` -> `{"java.io.IOException"}`.
     *
     * Used by the rule to determine whether a known-throwing call's exceptions are already handled.
     */
    internal fun parseCaughtTypes(
        expression: KtTryExpression,
        bindingContext: BindingContext
    ): Set<String> = expression.catchClauses
        .mapNotNull { bindingContext.get(BindingContext.TYPE, it.catchParameter?.typeReference)?.fqTypeName() }
        .toSet()

    /**
     * Returns checked exceptions declared by the JVM method or constructor.
     *
     * Examples:
     * - `java.io.BufferedWriter.write(1 param)` -> `{"java.io.IOException"}`
     * - `java.io.RandomAccessFile.constructor(2 params)` -> `{"java.io.FileNotFoundException"}`
     * - unknown classes, extension functions, or members without checked exceptions -> `emptySet()`
     *
     * A name plus parameter count can match several overloads. This returns exceptions only when
     * every matching overload declares checked exceptions. For example, an overload set containing
     * both a throwing `put(double)` and a non-throwing `put(Object)` returns `emptySet()`.
     */
    internal fun parseDeclaredCheckedExceptions(
        className: String,
        memberName: String,
        parameterCount: Int
    ): Set<String> {
        if (className.isEmpty() || memberName.isEmpty()) return emptySet()

        return resolvedClassesCache.loadClassOrNull(className)
            ?.getMethodsMatchingSignature(memberName, parameterCount)
            ?.extractCheckedExceptionsSet()
            .orEmpty()
    }

    internal data class KtMethodParameter(
        val name: String,
        val type: String,
        val isGeneric: Boolean
    )

    private companion object {
        private const val CONSTRUCTOR_MEMBER = "constructor"

        // Upper bound on dotted segments in a call's class name. Real fully-qualified names stay
        // well under this; exceeding it signals a malformed signature rather than a deep nesting.
        private const val MAX_NESTED_CLASS_DEPTH = 15

        private fun MutableMap<String, Class<*>?>.loadClassOrNull(className: String): Class<*>? = getOrPut(className) {
            className
                .computeClassNameCandidates()
                .firstNotNullOfOrNull {
                    runCatching { Class.forName(it, false, javaClass.classLoader) }.getOrNull()
                }
        }

        private fun Class<*>.getMethodsMatchingSignature(
            memberName: String,
            parameterCount: Int
        ): List<Executable> {
            val members = if (memberName == CONSTRUCTOR_MEMBER) {
                declaredConstructors.filter { it.parameterCount == parameterCount }
            } else {
                methods.filter { it.name == memberName && it.parameterCount == parameterCount }
            }

            // Skip compiler-synthesized bridge methods: a class overriding an interface method
            // (e.g. StringBuilder over Appendable) gets a bridge that carries the *interface's*
            // throws clause, which would otherwise yield false positives.
            return members.filterNot(Executable::isSynthetic)
        }

        private fun List<Executable>.extractCheckedExceptionsSet(): Set<String> {
            val checkedExceptions = map { it.extractCheckedExceptionNames() }

            // A name plus arity can match multiple overloads. If any candidate has no checked
            // exceptions, the configured member is not guaranteed to throw.
            if (checkedExceptions.any(Set<String>::isEmpty)) return emptySet()

            return checkedExceptions.flatten().toSet()
        }

        private fun Executable.extractCheckedExceptionNames(): Set<String> = exceptionTypes
            .filter(::isCheckedException)
            .mapTo(mutableSetOf(), Class<*>::getName)

        /**
         * The candidate JVM class names for this dotted class name, from the name as-is to progressively
         * reinterpreting trailing dotted segments as nested classes, so JVM nested types resolve too:
         * `a.b.Outer.Inner` -> `[a.b.Outer.Inner, a.b.Outer$Inner, a.b$Outer$Inner]`.
         */
        private fun String.computeClassNameCandidates(): List<String> {
            check(this.count { it == '.' } <= MAX_NESTED_CLASS_DEPTH) {
                "Class name '$this' is nested too deeply (limit $MAX_NESTED_CLASS_DEPTH) to resolve reflectively"
            }
            val candidates = mutableListOf(this)
            var current = this
            while (current.contains('.')) {
                current = current.substringBeforeLast('.') + '$' + current.substringAfterLast('.')
                candidates += current
            }
            return candidates
        }

        private fun toKtMethodParameter(valueParameter: ValueParameterDescriptor) = KtMethodParameter(
            name = valueParameter.name.toString(),
            type = valueParameter.type.toString(),
            isGeneric = containsGenericParameter(valueParameter.original.type)
        )

        // Detects both direct generic parameters (`T`) and nested ones (`List<T>`, `Map<String, T>`).
        private fun containsGenericParameter(type: KotlinType): Boolean {
            if (type.constructor.declarationDescriptor is TypeParameterDescriptor) return true
            return type.arguments.any { !it.isStarProjection && containsGenericParameter(it.type) }
        }

        private fun isCheckedException(exceptionClass: Class<*>): Boolean =
            Throwable::class.java.isAssignableFrom(exceptionClass) &&
                !RuntimeException::class.java.isAssignableFrom(exceptionClass) &&
                !Error::class.java.isAssignableFrom(exceptionClass)
    }
}
