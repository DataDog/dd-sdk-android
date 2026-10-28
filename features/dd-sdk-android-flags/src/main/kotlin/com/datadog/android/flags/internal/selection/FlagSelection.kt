/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.selection

/**
 * The flag selection as configured by the application, before canonicalization.
 * Lists and elements are nullable because Java callers can pass nulls. A null list means the list is absent.
 */
internal data class FlagSelection(
    val tags: List<String?>?,
    val keyPatterns: List<String?>?
)

/**
 * A flag selection that was trimmed, deduplicated, sorted in UTF-8 byte order and validated.
 */
internal data class CanonicalFlagSelection(
    val tags: List<String>,
    val keyPatterns: List<String>
)

/**
 * Reasons a flag selection is dropped. The values are the reason codes that the server and the other Datadog SDKs use.
 */
internal enum class FlagSelectionError(val reason: String, val limit: String) {
    MALFORMED_SELECTION(
        "malformed_selection",
        "tags and key patterns must be non-null strings without unpaired UTF-16 surrogates"
    ),
    TOO_MANY_VALUES(
        "too_many_values",
        "each list accepts at most ${FlagSelectionCanonicalizer.MAX_VALUES} values"
    ),
    INVALID_KEY_PATTERN(
        "invalid_key_pattern",
        "each key pattern is at most ${FlagSelectionCanonicalizer.MAX_KEY_PATTERN_BYTES} UTF-8 bytes"
    ),
    INVALID_TAG(
        "invalid_tag",
        "each tag is at most ${FlagSelectionCanonicalizer.MAX_TAG_BYTES} UTF-8 bytes"
    )
}

/**
 * Sanitizes the `DD-Flag-Selection-Ignored` response header, so a raw header value never reaches logs or telemetry.
 * A known reason is kept, and any other non-empty value becomes [UNKNOWN].
 */
internal object FlagSelectionIgnoredReason {

    internal const val UNKNOWN = "unknown"

    // The longest known reason has 19 characters. The limit bounds the work on an oversized header.
    private const val MAX_LENGTH = 32

    private val KNOWN_REASONS = setOf(
        FlagSelectionError.MALFORMED_SELECTION.reason,
        FlagSelectionError.TOO_MANY_VALUES.reason,
        FlagSelectionError.INVALID_KEY_PATTERN.reason,
        FlagSelectionError.INVALID_TAG.reason,
        "tag_load_failed"
    )

    fun sanitize(value: String): String = if (value.length <= MAX_LENGTH && value in KNOWN_REASONS) value else UNKNOWN
}

internal sealed interface FlagSelectionResult {
    object None : FlagSelectionResult
    data class Selected(val selection: CanonicalFlagSelection) : FlagSelectionResult
    data class Dropped(val error: FlagSelectionError) : FlagSelectionResult
}

/**
 * Canonicalizes and validates a flag selection with the same rules as the server.
 */
internal object FlagSelectionCanonicalizer {

    internal const val MAX_VALUES = 31
    internal const val MAX_TAG_BYTES = 200
    internal const val MAX_KEY_PATTERN_BYTES = 200

    // The Unicode White_Space property, which the server trims.
    // Kotlin's trim() differs: it also trims U+001C..U+001F and does not trim U+0085.
    private const val WHITE_SPACE = "\u0009\u000A\u000B\u000C\u000D\u0020\u0085\u00A0\u1680" +
        "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A" +
        "\u2028\u2029\u202F\u205F\u3000"

    @Suppress("ReturnCount")
    fun canonicalize(selection: FlagSelection?): FlagSelectionResult {
        if (selection == null) return FlagSelectionResult.None
        val rawTags = selection.tags.orEmpty().wellFormedOrNull()
        val rawKeyPatterns = selection.keyPatterns.orEmpty().wellFormedOrNull()
        if (rawTags == null || rawKeyPatterns == null) {
            return FlagSelectionResult.Dropped(FlagSelectionError.MALFORMED_SELECTION)
        }

        val tags = canonicalizeList(rawTags)
        val keyPatterns = canonicalizeList(rawKeyPatterns)

        val error = when {
            tags.size > MAX_VALUES || keyPatterns.size > MAX_VALUES -> FlagSelectionError.TOO_MANY_VALUES
            keyPatterns.any { it.utf8Size() > MAX_KEY_PATTERN_BYTES } -> FlagSelectionError.INVALID_KEY_PATTERN
            tags.any { it.utf8Size() > MAX_TAG_BYTES } -> FlagSelectionError.INVALID_TAG
            else -> null
        }
        return when {
            error != null -> FlagSelectionResult.Dropped(error)
            tags.isEmpty() && keyPatterns.isEmpty() -> FlagSelectionResult.None
            else -> FlagSelectionResult.Selected(CanonicalFlagSelection(tags, keyPatterns))
        }
    }

    private fun canonicalizeList(values: List<String>): List<String> {
        val distinct = values
            .asSequence()
            .map { value -> value.trim { it in WHITE_SPACE } }
            .filter { it.isNotEmpty() }
            .toList()
            .distinct()
        // An over-limit list is dropped as too_many_values, so its order does not matter.
        if (distinct.size > MAX_VALUES) return distinct
        return distinct
            .map { it to it.utf8SortKey() }
            .sortedBy { it.second }
            .map { it.first }
    }

    // Latin-1 maps each byte to one char, so comparing these strings compares unsigned UTF-8 bytes.
    private fun String.utf8SortKey(): String = String(toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)

    private fun String.utf8Size(): Int = toByteArray(Charsets.UTF_8).size

    // A Java caller can pass a raw list, so check each element's type instead of trusting the generic type.
    private fun List<*>.wellFormedOrNull(): List<String>? =
        if (all { it is String && !it.hasUnpairedSurrogate() }) filterIsInstance<String>() else null

    private fun String.hasUnpairedSurrogate(): Boolean {
        var index = 0
        var unpaired = false
        while (index < length && !unpaired) {
            val char = this[index]
            val isPair = char.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()
            unpaired = !isPair && (char.isHighSurrogate() || char.isLowSurrogate())
            index += if (isPair) 2 else 1
        }
        return unpaired
    }
}
