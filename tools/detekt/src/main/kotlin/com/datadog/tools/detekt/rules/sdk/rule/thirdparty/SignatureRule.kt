/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk.rule.thirdparty

import com.datadog.tools.detekt.rules.sdk.rule.thirdparty.CodeParser.KtMethodParameter

/**
 * A single matchable rule for a method signature from a detekt config entry.
 *
 * Handles exact entries, entries with nullable concrete slots, and entries with `*` / `?`
 * wildcards. A concrete nullable slot, e.g. `kotlin.String?`, matches both nullable and
 * non-nullable resolved calls for the same concrete type. Bare wildcard slots are recorded in
 * [wildcardSlots] and are only valid against generic formal parameters.
 */
internal class SignatureRule(
    val source: String,
    val regex: Regex,
    private val paramSlots: List<String>,
    private val wildcardSlots: List<WildcardSlot>,
    private val nullableConcreteMatchesNonNullable: Boolean,
    val exceptions: List<String> = emptyList()
) {

    private val methodPart: String = source.substringBefore('(')
    internal val overlapKey: OverlapKey = OverlapKey(methodPart, paramSlots.size)
    internal val memberReference: MemberReference = MemberReference(
        className = methodPart.substringBeforeLast('.', missingDelimiterValue = ""),
        memberName = methodPart.substringAfterLast('.'),
        parameterCount = paramSlots.size
    )

    /** True when this rule can be matched with a plain map lookup. */
    internal val canUseExactLookup: Boolean =
        wildcardSlots.isEmpty() && (!nullableConcreteMatchesNonNullable || paramSlots.none { it.isConcreteNullable() })

    /** Returns true if this rule's pattern matches the given fully-qualified [signature] string. */
    fun matches(signature: String): Boolean =
        if (canUseExactLookup) source == signature else regex.matches(signature)

    /**
     * Validates that every bare wildcard slot in this rule targets a generic type parameter at the
     * call site described by [ktMethodParameters].
     */
    fun validate(ktMethodParameters: List<KtMethodParameter>) {
        wildcardSlots.forEach { slot ->
            val formalParam = ktMethodParameters.getOrNull(slot.index) ?: return@forEach
            if (!formalParam.isGeneric && !formalParam.isAnyType()) {
                error(
                    "Wildcard '${slot.symbol}' in pattern '$source' targets non-generic " +
                        "parameter '${formalParam.name}: ${formalParam.type}'. " +
                        "Wildcards are only valid for generic, Any, or java.lang.Object parameters."
                )
            }
        }
    }

    /**
     * Returns true if there exists at least one resolved call signature that BOTH this pattern
     * and [other] would match. Wildcards are intersected per-slot: `*` matches non-nullable
     * literals only, `?` matches any literal (nullable or non-nullable), so `?` subsumes `*`.
     */
    fun intersects(other: SignatureRule): Boolean =
        methodPart == other.methodPart &&
            paramSlots.size == other.paramSlots.size &&
            paramSlots.zip(other.paramSlots).all { (a, b) ->
                slotsMatches(
                    a,
                    b,
                    aNullableMatchesNonNullable = nullableConcreteMatchesNonNullable,
                    bNullableMatchesNonNullable = other.nullableConcreteMatchesNonNullable
                )
            }

    internal data class WildcardSlot(val index: Int, val symbol: String)

    internal data class OverlapKey(val methodPart: String, val paramCount: Int)

    internal data class MemberReference(val className: String, val memberName: String, val parameterCount: Int)

    private companion object {

        private fun slotsMatches(
            a: String,
            b: String,
            aNullableMatchesNonNullable: Boolean,
            bNullableMatchesNonNullable: Boolean
        ): Boolean {
            val aWild = a == NON_NULL_WILDCARD || a == NULLABLE_WILDCARD
            val bWild = b == NON_NULL_WILDCARD || b == NULLABLE_WILDCARD
            return when {
                aWild && bWild -> true
                aWild -> b.matchesWildcard(wildcard = a)
                bWild -> a.matchesWildcard(wildcard = b)
                a == b -> true
                a.baseType() != b.baseType() -> false
                a.isConcreteNullable() -> aNullableMatchesNonNullable
                b.isConcreteNullable() -> bNullableMatchesNonNullable
                else -> false
            }
        }

        private fun String.matchesWildcard(
            wildcard: String
        ) = wildcard != NON_NULL_WILDCARD || !endsWith("?")

        private fun String.baseType(): String = removeSuffix("?")

        private fun String.isConcreteNullable(): Boolean =
            this != NULLABLE_WILDCARD && endsWith("?")

        private fun KtMethodParameter.isAnyType(): Boolean {
            // Kotlin can render Java Object/Any parameters in several forms:
            // - `?` marks a nullable type, e.g. `kotlin.Any?`.
            // - `!` marks a Java platform type, e.g. `java.lang.Object!`.
            // - `..` separates flexible bounds, e.g. `(Any..Any?)`.
            val normalizedType = type
                .substringAfterLast("(")
                .substringBefore("..")
                .removeSuffix("?")
                .removeSuffix("!")

            return normalizedType in ANY_PARAMETER_TYPES
        }

        private const val NON_NULL_WILDCARD = "*"
        private const val NULLABLE_WILDCARD = "?"
        private val ANY_PARAMETER_TYPES = setOf("Any", "kotlin.Any", "Object", "java.lang.Object")
    }
}
