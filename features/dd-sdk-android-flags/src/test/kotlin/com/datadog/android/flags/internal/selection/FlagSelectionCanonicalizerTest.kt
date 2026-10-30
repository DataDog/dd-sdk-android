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
        val selection = FlagSelection(
            tags = listOf("${UNICODE_WHITE_SPACE}mobile$UNICODE_WHITE_SPACE"),
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
    fun `M keep interior Unicode White_Space W canonicalize()`() {
        assertThat(UNICODE_WHITE_SPACE).hasSize(25)
        UNICODE_WHITE_SPACE.forEach { whiteSpace ->
            // Given
            val selection = tags("${whiteSpace}a${whiteSpace}b$whiteSpace")

            // When
            val result = FlagSelectionCanonicalizer.canonicalize(selection)

            // Then
            assertThat(result)
                .describedAs("U+%04X", whiteSpace.code)
                .isEqualTo(selected(listOf("a${whiteSpace}b"), emptyList()))
        }
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

        // https://www.unicode.org/Public/UCD/latest/ucd/PropList.txt lists 25 White_Space code points.
        private const val UNICODE_WHITE_SPACE = "\t\n\u000B\u000C\r \u0085\u00A0\u1680" +
            "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000"

        private fun numbered(prefix: String, count: Int) = List(count) { "$prefix%02d".format(Locale.US, it + 1) }

        private fun selected(tags: List<String>, keyPatterns: List<String>) =
            FlagSelectionResult.Selected(CanonicalFlagSelection(tags, keyPatterns))

        private fun values(count: Int) = List(count) { "value-$it" }

        // Java callers can pass a raw list, so the elements are not checked to be strings.
        @Suppress("UNCHECKED_CAST")
        private fun uncheckedStrings(vararg values: Any?) = values.toList() as List<String?>

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

        // Every case with an `sdk` expectation in DataDog/ffe-system-test-data@e8777633
        // flag-selection/selection-vectors.json, in file order and under the same name. An empty
        // expected selection is no selection on Android.
        @JvmStatic
        fun sharedVectorSelections() = listOf(
            Arguments.of(
                "no parameters selects every flag",
                FlagSelection(emptyList(), emptyList()),
                FlagSelectionResult.None
            ),
            Arguments.of(
                "tag selects flags having any of the tags",
                FlagSelection(listOf("team:search", "mobile"), emptyList()),
                selected(listOf("mobile", "team:search"), emptyList())
            ),
            Arguments.of(
                "tags and key patterns are combined with OR",
                FlagSelection(listOf("mobile"), listOf("checkout.*")),
                selected(listOf("mobile"), listOf("checkout.*"))
            ),
            Arguments.of(
                "key pattern must match the whole key",
                FlagSelection(emptyList(), listOf("checkout")),
                selected(emptyList(), listOf("checkout"))
            ),
            Arguments.of(
                "key pattern without a star selects exactly that key",
                FlagSelection(emptyList(), listOf("checkout.new-flow")),
                selected(emptyList(), listOf("checkout.new-flow"))
            ),
            Arguments.of(
                "star matches an empty run and multiple stars are allowed",
                FlagSelection(emptyList(), listOf("*search*auto*complete*")),
                selected(emptyList(), listOf("*search*auto*complete*"))
            ),
            Arguments.of(
                "leading star",
                FlagSelection(emptyList(), listOf("*-v2")),
                selected(emptyList(), listOf("*-v2"))
            ),
            Arguments.of(
                "lone star matches every key",
                FlagSelection(emptyList(), listOf("*")),
                selected(emptyList(), listOf("*"))
            ),
            Arguments.of(
                "question mark and brackets are literal",
                FlagSelection(emptyList(), listOf("search.ranking-v?", "[a]*", "{a,b}")),
                selected(emptyList(), listOf("[a]*", "search.ranking-v?", "{a,b}"))
            ),
            Arguments.of(
                "braces are literal even when an alternative names a flag",
                FlagSelection(emptyList(), listOf("{mobile.dark-mode,missing}")),
                selected(emptyList(), listOf("{mobile.dark-mode,missing}"))
            ),
            Arguments.of(
                "tags are never globs",
                FlagSelection(listOf("team:*"), emptyList()),
                selected(listOf("team:*"), emptyList())
            ),
            Arguments.of(
                "tags match case-sensitively",
                FlagSelection(listOf("Team:Payments", "MOBILE"), emptyList()),
                selected(listOf("MOBILE", "Team:Payments"), emptyList())
            ),
            Arguments.of(
                "key patterns match case-sensitively",
                FlagSelection(emptyList(), listOf("CHECKOUT.*", "Mobile.Dark-Mode")),
                selected(emptyList(), listOf("CHECKOUT.*", "Mobile.Dark-Mode"))
            ),
            Arguments.of(
                "values are trimmed, empties dropped and duplicates removed",
                FlagSelection(listOf(" mobile ", "mobile", "", "   "), emptyList()),
                selected(listOf("mobile"), emptyList())
            ),
            Arguments.of(
                "lists that are empty after normalization select every flag",
                FlagSelection(listOf("", "   "), listOf(" ")),
                FlagSelectionResult.None
            ),
            Arguments.of(
                "values are trimmed of Unicode white space",
                FlagSelection(listOf("\u00A0mobile\u3000", "\u0085ui\u2028", "\u0009 team:search \u000A"), emptyList()),
                selected(listOf("mobile", "team:search", "ui"), emptyList())
            ),
            Arguments.of(
                "characters outside Unicode White_Space are not trimmed",
                FlagSelection(listOf("\uFEFFmobile", "mobile\u200B"), emptyList()),
                selected(listOf("mobile\u200B", "\uFEFFmobile"), emptyList())
            ),
            Arguments.of(
                "values sort by UTF-8 bytes",
                FlagSelection(listOf(GRINNING_FACE, "\uFF5E", "a", "Z"), emptyList()),
                selected(listOf("Z", "a", "\uFF5E", GRINNING_FACE), emptyList())
            ),
            Arguments.of(
                "partial match serves only the matching flags",
                FlagSelection(listOf("mobile", "missing"), listOf("nothing.*")),
                selected(listOf("missing", "mobile"), listOf("nothing.*"))
            ),
            Arguments.of(
                "31 values in one list are allowed",
                FlagSelection(numbered("t", 31), emptyList()),
                selected(numbered("t", 31), emptyList())
            ),
            Arguments.of(
                "32 values in one list are rejected",
                FlagSelection(emptyList(), numbered("k", 32)),
                FlagSelectionResult.Dropped(FlagSelectionError.TOO_MANY_VALUES)
            ),
            Arguments.of(
                "31 values in each list are allowed",
                FlagSelection(numbered("t", 31), numbered("p", 31)),
                selected(numbered("t", 31), numbered("p", 31))
            ),
            Arguments.of(
                "32 values in one list are rejected even when the other is within the limit",
                FlagSelection(numbered("t", 31), numbered("p", 32)),
                FlagSelectionResult.Dropped(FlagSelectionError.TOO_MANY_VALUES)
            ),
            Arguments.of(
                "duplicates do not count toward the limit",
                FlagSelection(numbered("t", 31) + listOf("t01", " t02 "), emptyList()),
                selected(numbered("t", 31), emptyList())
            ),
            Arguments.of(
                "key pattern of 200 bytes is allowed",
                FlagSelection(emptyList(), listOf("*" + "x".repeat(199))),
                selected(emptyList(), listOf("*" + "x".repeat(199)))
            ),
            Arguments.of(
                "key pattern of 201 bytes is invalid",
                FlagSelection(emptyList(), listOf("*" + "x".repeat(200))),
                FlagSelectionResult.Dropped(FlagSelectionError.INVALID_KEY_PATTERN)
            ),
            Arguments.of(
                "key pattern length is measured after trimming",
                FlagSelection(emptyList(), listOf("  *" + "x".repeat(199) + "  ")),
                selected(emptyList(), listOf("*" + "x".repeat(199)))
            ),
            Arguments.of(
                "key pattern length counts UTF-8 bytes: 100 two-byte characters are allowed",
                FlagSelection(emptyList(), listOf("\u00E9".repeat(100))),
                selected(emptyList(), listOf("\u00E9".repeat(100)))
            ),
            Arguments.of(
                "key pattern length counts UTF-8 bytes: 201 bytes are invalid",
                FlagSelection(emptyList(), listOf("x" + "\u00E9".repeat(100))),
                FlagSelectionResult.Dropped(FlagSelectionError.INVALID_KEY_PATTERN)
            ),
            Arguments.of(
                "tag of 200 bytes is allowed",
                FlagSelection(listOf("a".repeat(200)), emptyList()),
                selected(listOf("a".repeat(200)), emptyList())
            ),
            Arguments.of(
                "tag of 201 bytes is invalid",
                FlagSelection(listOf("a".repeat(201)), emptyList()),
                FlagSelectionResult.Dropped(FlagSelectionError.INVALID_TAG)
            ),
            Arguments.of(
                "tag length is measured after trimming",
                FlagSelection(listOf("  " + "a".repeat(200) + "  "), emptyList()),
                selected(listOf("a".repeat(200)), emptyList())
            ),
            Arguments.of(
                "tag length counts UTF-8 bytes: 100 two-byte characters are allowed",
                FlagSelection(listOf("\u00E9".repeat(100)), emptyList()),
                selected(listOf("\u00E9".repeat(100)), emptyList())
            ),
            Arguments.of(
                "tag length counts UTF-8 bytes: 201 bytes are invalid",
                FlagSelection(listOf("x" + "\u00E9".repeat(100)), emptyList()),
                FlagSelectionResult.Dropped(FlagSelectionError.INVALID_TAG)
            ),
            Arguments.of(
                "a long tag invalidates the selection even when other tags match",
                FlagSelection(listOf("mobile", "a".repeat(201)), emptyList()),
                FlagSelectionResult.Dropped(FlagSelectionError.INVALID_TAG)
            ),
            Arguments.of(
                "too_many_values takes precedence over invalid_key_pattern",
                FlagSelection(numbered("t", 32), listOf("*" + "x".repeat(200))),
                FlagSelectionResult.Dropped(FlagSelectionError.TOO_MANY_VALUES)
            ),
            Arguments.of(
                "too_many_values takes precedence over invalid_tag",
                FlagSelection(numbered("t", 31) + "a".repeat(201), emptyList()),
                FlagSelectionResult.Dropped(FlagSelectionError.TOO_MANY_VALUES)
            ),
            Arguments.of(
                "invalid_key_pattern takes precedence over invalid_tag",
                FlagSelection(listOf("a".repeat(201)), listOf("a".repeat(201))),
                FlagSelectionResult.Dropped(FlagSelectionError.INVALID_KEY_PATTERN)
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
                "non-string tag from an unchecked cast",
                FlagSelection(tags = uncheckedStrings("mobile", 1), keyPatterns = emptyList()),
                FlagSelectionError.MALFORMED_SELECTION
            ),
            Arguments.of(
                "non-string key pattern from an unchecked cast",
                FlagSelection(tags = emptyList(), keyPatterns = uncheckedStrings(true)),
                FlagSelectionError.MALFORMED_SELECTION
            ),
            Arguments.of(
                "malformed before too many values",
                FlagSelection(values(32) + null, emptyList()),
                FlagSelectionError.MALFORMED_SELECTION
            ),
            Arguments.of(
                "malformed before invalid key pattern",
                FlagSelection(listOf("a\uD83D"), listOf("a".repeat(201))),
                FlagSelectionError.MALFORMED_SELECTION
            ),
            Arguments.of(
                "malformed before invalid tag",
                FlagSelection(listOf("a".repeat(201)), listOf(null)),
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
