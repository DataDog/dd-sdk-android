/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.unboundInternalLogger
import com.datadog.android.lint.InternalApi
import java.util.Locale

/**
 * Utility class to validate wildcard host patterns.
 *
 * A host pattern may contain at most one `*` wildcard and may only use the characters `a-z`, `0-9`,
 * `.`, `-` and `*`. Plain host names (without a wildcard) are valid patterns too. This is the
 * counterpart of [HostsSanitizer] for entries that are allowed to carry a wildcard.
 */
class HostPatternValidator {

    /**
     * Validates the given host patterns, returning the valid ones lowercased and in their original
     * order. Entries containing characters outside `[a-z0-9.*-]` or more than one `*` wildcard are
     * dropped and a warning is logged.
     *
     * @param patterns Host patterns to validate.
     * @param feature SDK feature requesting the validation.
     */
    @InternalApi
    fun validate(
        patterns: List<String>,
        feature: String
    ): List<String> {
        return patterns.mapNotNull { validatePattern(it, feature) }
    }

    private fun validatePattern(pattern: String, feature: String): String? {
        val lowercased = pattern.lowercase(Locale.US)
        return when {
            lowercased.any { it !in ALLOWED_CHARACTERS } -> {
                unboundInternalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { ERROR_INVALID_CHARACTERS.format(Locale.US, pattern, feature) }
                )
                null
            }
            lowercased.count { it == WILDCARD } > 1 -> {
                unboundInternalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { ERROR_MULTIPLE_WILDCARDS.format(Locale.US, pattern, feature) }
                )
                null
            }
            else -> lowercased
        }
    }

    internal companion object {
        private const val WILDCARD = '*'

        private const val ALLOWED_CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789.-*"

        internal const val ERROR_INVALID_CHARACTERS: String =
            "You are using a malformed host pattern \"%s\" to setup %s tracking. It will be dropped. " +
                "A host pattern may only contain lowercase letters, digits, '.', '-' and a single '*' wildcard."

        internal const val ERROR_MULTIPLE_WILDCARDS: String =
            "You are using a host pattern \"%s\" with more than one wildcard to setup %s tracking. " +
                "It will be dropped. A host pattern may contain at most one '*' wildcard."
    }
}
