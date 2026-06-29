/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

import com.datadog.android.api.InternalLogger
import com.datadog.android.lint.InternalApi
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/**
 * Utility class to validate wildcard host patterns.
 *
 * A host pattern may contain at most one `*` wildcard and may only use the characters `a-z`, `0-9`,
 * `.`, `-` and `*`. A wildcard may only match subdomains of a registrable domain, so it must sit in
 * a subdomain label and overly broad patterns such as `*`, `*.com`, `*.co.uk` or
 * `*example.com` are rejected while `*.example.com` and `*.example.co.uk` are accepted.
 *
 * This is the counterpart of [HostsSanitizer] for entries that are allowed to carry a wildcard, and
 * it only enforces the wildcard rules above. Wildcard-free entries are merely lowercased and
 * character-checked, not fully validated as host names (blanks, bare TLDs such as `com`, etc. are
 * returned as-is), so callers should route wildcard-free entries through [HostsSanitizer] for proper
 * host-name validation.
 *
 * @param internalLogger the logger used to report dropped patterns to the user.
 */
class HostPatternSanitizer(
    private val internalLogger: InternalLogger
) {

    /**
     * Validates the given host patterns, returning the valid ones lowercased and in their original
     * order. Entries containing characters outside `[a-z0-9.*-]`, more than one `*` wildcard or an
     * overly broad wildcard are dropped and an error is logged.
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
            lowercased.contains(INVALID_CHARACTER) -> {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { ERROR_INVALID_CHARACTERS.format(Locale.US, pattern, feature) }
                )
                null
            }
            lowercased.count { it == WILDCARD } > 1 -> {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { ERROR_MULTIPLE_WILDCARDS.format(Locale.US, pattern, feature) }
                )
                null
            }
            lowercased.contains(WILDCARD) && !targetsSubdomainOfDomain(lowercased) -> {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { ERROR_WILDCARD_NOT_SUBDOMAIN.format(Locale.US, pattern, feature) }
                )
                null
            }
            else -> lowercased
        }
    }

    /**
     * A wildcard may only match subdomains of a registrable domain. The `*` must therefore sit in a
     * subdomain label (immediately followed by a `.`) and the remainder must contain a registrable
     * domain according to the public suffix list. This keeps `*.example.com`, `*.example.co.uk`,
     * `*.foo.example.com` and `preview-*.shopist.io` while rejecting match-all (`*`), public-suffix
     * (`*.com`, `*.co.uk`, `*.github.io`) and cross-domain (`*example.com`, which would also match
     * `evilexample.com`) patterns.
     */
    private fun targetsSubdomainOfDomain(lowercased: String): Boolean {
        val afterWildcard = lowercased.substringAfter(WILDCARD)
        if (!afterWildcard.startsWith(LABEL_SEPARATOR)) {
            return false
        }

        // The remainder must resolve to a registrable domain (eTLD+1). topPrivateDomain() returns
        // null for bare public suffixes such as "com", "co.uk" or "github.io", and for hosts with
        // empty labels, so any depth of subdomain (e.g. "foo.example.com") is accepted.
        val domain = afterWildcard.removePrefix(LABEL_SEPARATOR)
        return "https://$domain".toHttpUrlOrNull()?.topPrivateDomain() != null
    }

    internal companion object {
        private const val WILDCARD = '*'
        private const val LABEL_SEPARATOR = "."

        private val INVALID_CHARACTER = Regex("[^a-z0-9.*-]")

        internal const val ERROR_INVALID_CHARACTERS: String =
            "You are using a malformed host pattern \"%s\" to setup %s tracking. It will be dropped. " +
                "A host pattern may only contain lowercase letters, digits, '.', '-' and a single '*' wildcard."

        internal const val ERROR_MULTIPLE_WILDCARDS: String =
            "You are using a host pattern \"%s\" with more than one wildcard to setup %s tracking. " +
                "It will be dropped. A host pattern may contain at most one '*' wildcard."

        internal const val ERROR_WILDCARD_NOT_SUBDOMAIN: String =
            "You are using a host pattern \"%s\" to setup %s tracking, but a wildcard may only match " +
                "subdomains of a registrable domain. It will be dropped. Patterns such as '*', " +
                "'*.com', '*.co.uk' or '*example.com' are too broad; use a pattern like " +
                "'*.example.com' instead."
    }
}
