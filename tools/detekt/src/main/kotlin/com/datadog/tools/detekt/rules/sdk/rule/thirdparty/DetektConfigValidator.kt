/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk.rule.thirdparty

/**
 * Validates parsed UnsafeThirdPartyFunctionCall config entries.
 */
internal class DetektConfigValidator(
    private val codeParser: CodeParser = CodeParser()
) {

    fun validate(
        knownSafeCalls: List<SignatureRule>,
        knownThrowingCalls: List<SignatureRule>
    ) {
        val conflicts = buildList {
            addAll(validateNoDuplicationWithinSameFile("knownSafeCalls", knownSafeCalls))
            addAll(validateNoDuplicationWithinSameFile("knownThrowingCalls", knownThrowingCalls))
            addAll(validateSortedWithinClasses("knownSafeCalls", knownSafeCalls))
            addAll(validateSortedWithinClasses("knownThrowingCalls", knownThrowingCalls))
            addAll(validateThrowingCallIsNotInSafeCallsConfig(knownSafeCalls))
            addAll(validateNoConflictsBetweenSafeAndThrowingRules(knownSafeCalls, knownThrowingCalls))
        }
        if (conflicts.isNotEmpty()) {
            error(
                "Found ${conflicts.size} issue(s) in detekt config:\n" +
                    conflicts.joinToString("\n")
            )
        }
    }

    private fun validateSortedWithinClasses(
        configName: String,
        patterns: List<SignatureRule>
    ) = buildList {
        for (i in 1 until patterns.size) {
            val prev = patterns[i - 1]
            val curr = patterns[i]
            val prevClass = prev.source.substringBefore('(').substringBeforeLast('.')
            val currClass = curr.source.substringBefore('(').substringBeforeLast('.')
            if (prevClass == currClass && prev.source > curr.source) {
                add(
                    "  - $configName entries for '$currClass' are not sorted: " +
                        "'${curr.source}' should come before '${prev.source}'"
                )
            }
        }
    }

    private fun validateNoConflictsBetweenSafeAndThrowingRules(
        safePatterns: List<SignatureRule>,
        throwingPatterns: List<SignatureRule>
    ) = buildList {
        val throwingByKey = throwingPatterns.groupBy { it.overlapKey }
        safePatterns.forEach { safe ->
            throwingByKey[safe.overlapKey].orEmpty().forEach { throwing ->
                if (safe.intersects(throwing)) {
                    add(
                        "  - '${safe.source}' (knownSafeCalls) overlaps with " +
                            "'${throwing.source}' (knownThrowingCalls)"
                    )
                }
            }
        }
    }

    private fun validateNoDuplicationWithinSameFile(
        configName: String,
        patterns: List<SignatureRule>
    ) = buildList {
        patterns.groupBy { it.overlapKey }.values.forEach { matchingPatterns ->
            for (i in matchingPatterns.indices) {
                for (j in i + 1 until matchingPatterns.size) {
                    if (matchingPatterns[i].intersects(matchingPatterns[j])) {
                        add(
                            "  - '${matchingPatterns[i].source}' duplicates " +
                                "'${matchingPatterns[j].source}' in $configName"
                        )
                    }
                }
            }
        }
    }

    /**
     * Protects `knownSafeCalls` from methods that declare a checked exception.
     *
     * A method that can throw a checked exception is, by definition, not safe. Listing it under
     * `knownSafeCalls` would silence a genuine unsafe-call warning. This check is independent of
     * `knownThrowingCalls`: it resolves the *actual* JVM method/constructor behind each safe entry
     * via reflection and inspects its declared exception types, so it catches a dangerous method
     * even when it was never added to `knownThrowingCalls` (and regardless of whether the offending
     * entry spelled out an `:Exception` suffix).
     *
     * It is best-effort: entries whose declaring class is not on the rule's classpath (most
     * `android.*`, `androidx.*` and third-party types) or that resolve to Kotlin extension functions
     * cannot be inspected and are silently skipped. Only resolvable JVM members are validated.
     */
    private fun validateThrowingCallIsNotInSafeCallsConfig(
        safePatterns: List<SignatureRule>
    ) = buildList {
        safePatterns.forEach { safe ->
            val memberReference = safe.memberReference
            val checkedExceptions = codeParser.parseDeclaredCheckedExceptions(
                className = memberReference.className,
                memberName = memberReference.memberName,
                parameterCount = memberReference.parameterCount
            )
            if (checkedExceptions.isNotEmpty()) {
                add(
                    "  - '${safe.source}' (knownSafeCalls) resolves to a member declaring checked " +
                        "exception(s) ${checkedExceptions.sorted().joinToString()}; methods that can throw " +
                        "checked exceptions are not safe and must not be listed in knownSafeCalls"
                )
            }
        }
    }
}
