/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.selection

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.Locale

internal class FlagSelectionCanonicalizerTest {

    @Test
    fun `M return None W canonicalize() { no selection }`() {
        // When / Then
        assertThat(FlagSelectionCanonicalizer.canonicalize(null)).isEqualTo(FlagSelectionResult.None)
    }

    @Test
    fun `M return None W canonicalize() { lists empty after canonicalization }`() {
        // Given
        val selection = FlagSelection(tags = listOf("", " \t"), keyPatterns = listOf("\u3000"))

        // When / Then
        assertThat(FlagSelectionCanonicalizer.canonicalize(selection)).isEqualTo(FlagSelectionResult.None)
    }

    @Test
    fun `M return None W canonicalize() { both lists null }`() {
        // Given
        val selection = FlagSelection(tags = null, keyPatterns = null)

        // When / Then
        assertThat(FlagSelectionCanonicalizer.canonicalize(selection)).isEqualTo(FlagSelectionResult.None)
    }

    @Test
    fun `M treat a null list as empty W canonicalize() { one list null }`() {
        // Given
        val tagsOnly = FlagSelection(tags = listOf("mobile"), keyPatterns = null)
        val keyPatternsOnly = FlagSelection(tags = null, keyPatterns = listOf("checkout-*"))

        // When
        val tagsOnlyResult = FlagSelectionCanonicalizer.canonicalize(tagsOnly)
        val keyPatternsOnlyResult = FlagSelectionCanonicalizer.canonicalize(keyPatternsOnly)

        // Then
        assertThat(tagsOnlyResult).isEqualTo(
            FlagSelectionResult.Selected(CanonicalFlagSelection(tags = listOf("mobile"), keyPatterns = emptyList()))
        )
        assertThat(keyPatternsOnlyResult).isEqualTo(
            FlagSelectionResult.Selected(CanonicalFlagSelection(tags = emptyList(), keyPatterns = listOf("checkout-*")))
        )
    }

    @Test
    fun `M trim the Unicode White_Space set W canonicalize()`() {
        // Given
        val whiteSpace = "\t\n\u000B\u000C\r \u0085\u00A0\u1680" +
            "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000"
        val selection = FlagSelection(
            tags = listOf("${whiteSpace}mobile$whiteSpace"),
            keyPatterns = listOf("\uFEFFcheckout\uFEFF", "\u001Cpayment\u001C")
        )

        // When
        val result = FlagSelectionCanonicalizer.canonicalize(selection)

        // Then
        assertThat(result).isEqualTo(
            FlagSelectionResult.Selected(
                CanonicalFlagSelection(
                    tags = listOf("mobile"),
                    keyPatterns = listOf("\u001Cpayment\u001C", "\uFEFFcheckout\uFEFF")
                )
            )
        )
    }

    @Test
    fun `M drop empty values and dedupe after trimming W canonicalize()`() {
        // Given
        val selection = FlagSelection(
            tags = listOf("web", " mobile", "", "mobile ", "web"),
            keyPatterns = emptyList()
        )

        // When
        val result = FlagSelectionCanonicalizer.canonicalize(selection)

        // Then
        assertThat(result).isEqualTo(
            FlagSelectionResult.Selected(
                CanonicalFlagSelection(tags = listOf("mobile", "web"), keyPatterns = emptyList())
            )
        )
    }

    @Test
    fun `M sort in UTF-8 byte order W canonicalize() { UTF-16 order differs }`() {
        // Given
        val fullwidthTilde = "\uFF5E"
        val grinningFace = "\uD83D\uDE00"
        val selection = FlagSelection(
            tags = listOf(grinningFace, fullwidthTilde, "a"),
            keyPatterns = listOf("b*", "B*", "\u00E9*")
        )

        // When
        val result = FlagSelectionCanonicalizer.canonicalize(selection)

        // Then
        assertThat(result).isEqualTo(
            FlagSelectionResult.Selected(
                CanonicalFlagSelection(
                    tags = listOf("a", fullwidthTilde, grinningFace),
                    keyPatterns = listOf("B*", "b*", "\u00E9*")
                )
            )
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedSelections")
    fun `M keep the selection W canonicalize() { at a limit }`(
        description: String,
        selection: FlagSelection
    ) {
        // When
        val result = FlagSelectionCanonicalizer.canonicalize(selection)

        // Then
        assertThat(result).describedAs(description).isInstanceOf(FlagSelectionResult.Selected::class.java)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("droppedSelections")
    fun `M drop the selection W canonicalize() { invalid selection }`(
        description: String,
        selection: FlagSelection,
        expectedError: FlagSelectionError
    ) {
        // When
        val result = FlagSelectionCanonicalizer.canonicalize(selection)

        // Then
        assertThat(result).describedAs(description).isEqualTo(FlagSelectionResult.Dropped(expectedError))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sharedVectorSelections")
    fun `M match the shared selection vector W canonicalize()`(
        vectorName: String,
        selection: FlagSelection,
        expectedResult: FlagSelectionResult
    ) {
        // When
        val result = FlagSelectionCanonicalizer.canonicalize(selection)

        // Then
        assertThat(result).describedAs(vectorName).isEqualTo(expectedResult)
    }

    companion object {
        private const val GRINNING_FACE = "\uD83D\uDE00"

        private fun numbered(prefix: String, count: Int) = List(count) { "$prefix%02d".format(Locale.US, it + 1) }

        private fun selected(tags: List<String>, keyPatterns: List<String>) =
            FlagSelectionResult.Selected(CanonicalFlagSelection(tags, keyPatterns))

        private fun values(count: Int) = List(count) { "value-$it" }

        private fun tags(vararg tags: String?) = FlagSelection(tags = tags.toList(), keyPatterns = emptyList())

        private fun keyPatterns(vararg keyPatterns: String?) =
            FlagSelection(tags = emptyList(), keyPatterns = keyPatterns.toList())

        @JvmStatic
        fun acceptedSelections() = listOf(
            Arguments.of("31 tags", FlagSelection(values(31), emptyList())),
            Arguments.of("31 key patterns", FlagSelection(emptyList(), values(31))),
            Arguments.of("32 tags with one duplicate", FlagSelection(values(31) + "value-0", emptyList())),
            Arguments.of("200 one-byte characters", tags("a".repeat(200))),
            Arguments.of("100 two-byte characters", tags("\u00E9".repeat(100))),
            Arguments.of("66 three-byte characters and 2 bytes", tags("\u20AC".repeat(66) + "ab")),
            Arguments.of("50 four-byte characters", keyPatterns(GRINNING_FACE.repeat(50))),
            Arguments.of("200 bytes after trimming", keyPatterns("  ${"a".repeat(200)}\u3000"))
        )

        // Cases with `sdk` expectations in DataDog/ffe-system-test-data@e8777633
        // flag-selection/selection-vectors.json that the tests above do not already cover.
        @JvmStatic
        fun sharedVectorSelections() = listOf(
            Arguments.of(
                "leading star",
                keyPatterns("*-v2"),
                selected(emptyList(), listOf("*-v2"))
            ),
            Arguments.of(
                "star matches an empty run and multiple stars are allowed",
                keyPatterns("*search*auto*complete*"),
                selected(emptyList(), listOf("*search*auto*complete*"))
            ),
            Arguments.of(
                "lone star matches every key",
                keyPatterns("*"),
                selected(emptyList(), listOf("*"))
            ),
            Arguments.of(
                "question mark and brackets are literal",
                keyPatterns("search.ranking-v?", "[a]*", "{a,b}"),
                selected(emptyList(), listOf("[a]*", "search.ranking-v?", "{a,b}"))
            ),
            Arguments.of(
                "braces are literal even when an alternative names a flag",
                keyPatterns("{mobile.dark-mode,missing}"),
                selected(emptyList(), listOf("{mobile.dark-mode,missing}"))
            ),
            Arguments.of(
                "tags are never globs",
                tags("team:*"),
                selected(listOf("team:*"), emptyList())
            ),
            Arguments.of(
                "tags match case-sensitively",
                tags("Team:Payments", "MOBILE"),
                selected(listOf("MOBILE", "Team:Payments"), emptyList())
            ),
            Arguments.of(
                "characters outside Unicode White_Space are not trimmed",
                tags("\uFEFFmobile", "mobile\u200B"),
                selected(listOf("mobile\u200B", "\uFEFFmobile"), emptyList())
            ),
            Arguments.of(
                "values sort by UTF-8 bytes",
                tags(GRINNING_FACE, "\uFF5E", "a", "Z"),
                selected(listOf("Z", "a", "\uFF5E", GRINNING_FACE), emptyList())
            ),
            Arguments.of(
                "31 values in each list are allowed",
                FlagSelection(numbered("t", 31), numbered("p", 31)),
                selected(numbered("t", 31), numbered("p", 31))
            ),
            Arguments.of(
                "duplicates do not count toward the limit",
                FlagSelection(numbered("t", 31) + "t01" + " t02 ", emptyList()),
                selected(numbered("t", 31), emptyList())
            ),
            Arguments.of(
                "key pattern length counts UTF-8 bytes: 100 two-byte characters are allowed",
                keyPatterns("\u00E9".repeat(100)),
                selected(emptyList(), listOf("\u00E9".repeat(100)))
            ),
            Arguments.of(
                "tag length is measured after trimming",
                tags("  ${"a".repeat(200)}  "),
                selected(listOf("a".repeat(200)), emptyList())
            ),
            Arguments.of(
                "32 values in one list are rejected even when the other is within the limit",
                FlagSelection(numbered("t", 31), numbered("p", 32)),
                FlagSelectionResult.Dropped(FlagSelectionError.TOO_MANY_VALUES)
            ),
            Arguments.of(
                "key pattern length counts UTF-8 bytes: 201 bytes are invalid",
                keyPatterns("x" + "\u00E9".repeat(100)),
                FlagSelectionResult.Dropped(FlagSelectionError.INVALID_KEY_PATTERN)
            ),
            Arguments.of(
                "a long tag invalidates the selection even when other tags match",
                tags("mobile", "a".repeat(201)),
                FlagSelectionResult.Dropped(FlagSelectionError.INVALID_TAG)
            ),
            Arguments.of(
                "too_many_values takes precedence over invalid_tag",
                FlagSelection(numbered("t", 31) + "a".repeat(201), emptyList()),
                FlagSelectionResult.Dropped(FlagSelectionError.TOO_MANY_VALUES)
            )
        )

        @JvmStatic
        fun droppedSelections() = listOf(
            Arguments.of("32 tags", FlagSelection(values(32), emptyList()), FlagSelectionError.TOO_MANY_VALUES),
            Arguments.of(
                "32 key patterns",
                FlagSelection(emptyList(), values(32)),
                FlagSelectionError.TOO_MANY_VALUES
            ),
            Arguments.of("201-byte tag", tags("a".repeat(201)), FlagSelectionError.INVALID_TAG),
            Arguments.of(
                "201-byte tag with two-byte characters",
                tags("\u00E9".repeat(100) + "a"),
                FlagSelectionError.INVALID_TAG
            ),
            Arguments.of(
                "201-byte tag with three-byte characters",
                tags("\u20AC".repeat(67)),
                FlagSelectionError.INVALID_TAG
            ),
            Arguments.of(
                "201-byte key pattern with four-byte characters",
                keyPatterns(GRINNING_FACE.repeat(50) + "*"),
                FlagSelectionError.INVALID_KEY_PATTERN
            ),
            Arguments.of("lone high surrogate", tags("a\uD83D"), FlagSelectionError.MALFORMED_SELECTION),
            Arguments.of("lone low surrogate", keyPatterns("\uDE00a"), FlagSelectionError.MALFORMED_SELECTION),
            Arguments.of(
                "reversed surrogate pair",
                tags("\uDE00\uD83D"),
                FlagSelectionError.MALFORMED_SELECTION
            ),
            Arguments.of("null tag", tags("mobile", null), FlagSelectionError.MALFORMED_SELECTION),
            Arguments.of("null key pattern", keyPatterns(null), FlagSelectionError.MALFORMED_SELECTION),
            Arguments.of(
                "malformed before too many values",
                FlagSelection(values(32) + null, emptyList()),
                FlagSelectionError.MALFORMED_SELECTION
            ),
            Arguments.of(
                "too many values before invalid key pattern",
                FlagSelection(values(32), listOf("a".repeat(201))),
                FlagSelectionError.TOO_MANY_VALUES
            ),
            Arguments.of(
                "invalid key pattern before invalid tag",
                FlagSelection(listOf("a".repeat(201)), listOf("a".repeat(201))),
                FlagSelectionError.INVALID_KEY_PATTERN
            )
        )
    }
}
