/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk.rule.thirdparty

import com.datadog.tools.detekt.rules.sdk.rule.thirdparty.DetektConfigParser.Companion.NON_NULL_WILDCARD
import com.datadog.tools.detekt.rules.sdk.rule.thirdparty.DetektConfigParser.Companion.parseEntry

/**
 * Parses raw YAML config strings into [SignatureRule] instances.
 *
 * Two entry formats are supported:
 * - Safe calls: `"java.io.File.readText(kotlin.String)"` - no exception list
 * - Throwing calls: `"java.io.File.readText(kotlin.String):java.io.IOException,kotlin.Exception"` -
 *   method signature followed by `:` and a comma-separated list of thrown exception types
 *
 * In safe-call config, concrete nullable slots, e.g. `kotlin.String?`, match both nullable and
 * non-nullable resolved calls for the same type. Throwing-call config keeps nullable concrete
 * slots exact. Bare wildcard slots (`*` for non-nullable, `?` for any type) are resolved by
 * [parseEntry] into regex patterns and recorded as [SignatureRule.WildcardSlot] entries.
 */
internal class DetektConfigParser {

    fun parseSafeCalls(entries: List<String>): List<SignatureRule> =
        entries.map { parseEntry(it, nullableConcreteMatchesNonNullable = true) }

    fun parseThrowingCalls(entries: List<String>): List<SignatureRule> = entries.map {
        val parts = it.split(COLON_SEPARATOR)
        val exceptions = parts.getOrNull(1)
            ?.split(COMMA_SEPARATOR)
            ?.map { exception -> exception.trim() }
            ?: error("ERROR WITH KNOWN THROWING CALL: $it")

        parseEntry(
            source = parts.first(),
            exceptions = exceptions,
            nullableConcreteMatchesNonNullable = false
        )
    }

    internal companion object {
        private const val COLON_SEPARATOR = ':'
        private const val COMMA_SEPARATOR = ","
        private const val NON_NULL_WILDCARD = "*"
        private const val NULLABLE_WILDCARD = "?"

        /**
         * Matches any non-nullable type at a wildcard position.
         * e.g. `kotlin.String` matches, `kotlin.String?` does not.
         */
        private const val NON_NULL_WILDCARD_PATTERN = "[^,)?]+"

        /**
         * Matches any type at a wildcard position.
         * e.g. both `kotlin.String` and `kotlin.String?` match.
         */
        private const val NULLABLE_WILDCARD_PATTERN = "[^,)]+"

        /**
         * Parses a config entry string into a [SignatureRule].
         *
         * The [source] string is split into a method part and comma-separated param slots. Each slot
         * becomes either a literal (regex-escaped), a nullable literal pattern, or a wildcard pattern.
         * Wildcard positions are recorded in `wildcardSlots` so wildcard rules can be separated from
         * exact signatures.
         *
         * Example - exact signature, no wildcards:
         * ```
         * source     : "java.io.File.readText(kotlin.String)"
         * paramSlots : ["kotlin.String"]
         * wildcardSlots: []
         * regex      : ^java\.io\.File\.readText\(kotlin\.String\)$
         * ```
         *
         * Example - [NON_NULL_WILDCARD] (`*`) matches any non-nullable type at that position:
         * ```
         * source     : "java.io.File.listFiles(*)"
         * paramSlots : ["*"]
         * wildcardSlots: [WildcardSlot(index=0, symbol="*")]
         * regex      : ^java\.io\.File\.listFiles\([^,)?]+\)$
         * ```
         *
         * Example - mixed wildcards:
         * ```
         * source     : "kotlin.collections.MutableMap.put(*, ?)"
         * paramSlots : ["*", "?"]
         * wildcardSlots: [WildcardSlot(index=0, symbol="*"), WildcardSlot(index=1, symbol="?")]
         * regex      : ^kotlin\.collections\.MutableMap\.put\([^,)?]+, [^,)]+\)$
         * ```
         */
        internal fun parseEntry(
            source: String,
            exceptions: List<String> = emptyList(),
            nullableConcreteMatchesNonNullable: Boolean = true
        ): SignatureRule {
            val paramSlots = source.substringAfter('(')
                .dropLast(1)
                .takeIf { it.isNotEmpty() }?.split(",")?.map { it.trim() }
                .orEmpty()

            val paramsPattern = buildParamsPattern(paramSlots, nullableConcreteMatchesNonNullable)
            val wildcardSlots = parseWildcardSlots(paramSlots)
            return SignatureRule(
                source,
                Regex("^${Regex.escape(source.substringBefore('(') + '(')}$paramsPattern\\)$"),
                paramSlots,
                wildcardSlots,
                nullableConcreteMatchesNonNullable,
                exceptions
            )
        }

        private fun buildParamsPattern(
            paramSlots: List<String>,
            nullableConcreteMatchesNonNullable: Boolean
        ): String =
            paramSlots.joinToString(", ") { param ->
                when (param) {
                    NON_NULL_WILDCARD -> NON_NULL_WILDCARD_PATTERN
                    NULLABLE_WILDCARD -> NULLABLE_WILDCARD_PATTERN
                    else -> param.toConcreteParamPattern(nullableConcreteMatchesNonNullable)
                }
            }

        private fun String.toConcreteParamPattern(nullableConcreteMatchesNonNullable: Boolean): String =
            if (nullableConcreteMatchesNonNullable && endsWith(NULLABLE_WILDCARD)) {
                Regex.escape(removeSuffix(NULLABLE_WILDCARD)) + "\\??"
            } else {
                Regex.escape(this)
            }

        private fun parseWildcardSlots(paramSlots: List<String>): List<SignatureRule.WildcardSlot> =
            paramSlots.mapIndexedNotNull { index, param ->
                when (param) {
                    NON_NULL_WILDCARD -> SignatureRule.WildcardSlot(index, NON_NULL_WILDCARD)
                    NULLABLE_WILDCARD -> SignatureRule.WildcardSlot(index, NULLABLE_WILDCARD)
                    else -> null
                }
            }
    }
}
