/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentelemetry.context.propagation

import com.datadog.android.trace.opentelemetry.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentelemetry.api.trace.TraceState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Cases ported from open-telemetry/opentelemetry-java#8914 (W3CTraceContextEncodingTest and
 * W3CTraceContextPropagatorTest). Where upstream throws IllegalArgumentException, decodeHeader
 * returns [TraceState.getDefault] instead.
 */
@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class TraceStateHelperTest {

    // region Valid headers

    @Test
    fun `M decode all members in order W decodeHeader() {members without whitespace}`(forge: Forge) {
        // Given
        val fakeMembers = forge.aTraceStateMemberList(forge.anInt(min = 1, max = MAX_MEMBERS + 1))
        val fakeHeader = fakeMembers.joinToString(",") { "${it.first}=${it.second}" }

        // When
        val result = TraceStateHelper.decodeHeader(fakeHeader)

        // Then
        assertThat(result.asMap()).containsExactlyEntriesOf(fakeMembers.toMap())
        assertThat(TraceStateHelper.encodeHeader(result)).isEqualTo(fakeHeader)
    }

    @Test
    fun `M ignore optional whitespace W decodeHeader() {spaces and tabs around members}`(forge: Forge) {
        // Given
        val (first, second) = forge.aTraceStateMemberList(2)
        val fakeHeader = "${forgeWhitespace(forge)}${first.first}=${first.second}${forgeWhitespace(forge)}," +
            "${forgeWhitespace(forge)}${second.first}=${second.second}${forgeWhitespace(forge)}"

        // When
        val result = TraceStateHelper.decodeHeader(fakeHeader)

        // Then
        assertThat(TraceStateHelper.encodeHeader(result))
            .isEqualTo("${first.first}=${first.second},${second.first}=${second.second}")
    }

    @Test
    fun `M ignore empty members W decodeHeader() {leading, intervening and trailing empty members}`(
        forge: Forge
    ) {
        // Given
        val (first, second) = forge.aTraceStateMemberList(2)
        val fakeHeader = "${forge.anEmptyTraceStateMembers()}${first.first}=${first.second}," +
            "${forgeWhitespace(forge)}${forge.anEmptyTraceStateMembers()}${second.first}=${second.second}" +
            forge.anEmptyTraceStateMembers()

        // When
        val result = TraceStateHelper.decodeHeader(fakeHeader)

        // Then
        assertThat(TraceStateHelper.encodeHeader(result))
            .isEqualTo("${first.first}=${first.second},${second.first}=${second.second}")
    }

    @Test
    fun `M preserve leading spaces in value W decodeHeader()`(forge: Forge) {
        // Given
        val fakeKey = forge.aStringMatching(KEY_REGEX)
        val fakeValue = " ".repeat(forge.anInt(min = 1, max = 4)) + forge.aStringMatching(VALUE_REGEX)
        val fakeHeader = "${forgeWhitespace(forge)}$fakeKey=$fakeValue${forgeWhitespace(forge)}"

        // When
        val result = TraceStateHelper.decodeHeader(fakeHeader)

        // Then
        assertThat(result.get(fakeKey)).isEqualTo(fakeValue)
    }

    @Test
    fun `M decode max members W decodeHeader() {32 members}`(forge: Forge) {
        // Given
        val fakeMembers = forge.aTraceStateMemberList(MAX_MEMBERS)
        val fakeHeader = fakeMembers.joinToString(",") { "${it.first}=${it.second}" }

        // When
        val result = TraceStateHelper.decodeHeader(fakeHeader)

        // Then
        assertThat(result.size()).isEqualTo(MAX_MEMBERS)
    }

    @Test
    fun `M not count empty members W decodeHeader() {32 members interleaved with empty members}`(forge: Forge) {
        // Given
        val fakeMembers = forge.aTraceStateMemberList(MAX_MEMBERS)
        val fakeHeader = fakeMembers.joinToString(",") { "${it.first}=${it.second}" }
        val fakePaddedHeader = ",, " + fakeHeader.replace(",", ", ,") + ",\t,"

        // When
        val result = TraceStateHelper.decodeHeader(fakePaddedHeader)

        // Then
        assertThat(result).isEqualTo(TraceStateHelper.decodeHeader(fakeHeader))
    }

    @Test
    fun `M not limit combined length to 512 W decodeHeader() {256 chars key and value}`(forge: Forge) {
        // Given
        val fakeKey = forge.aStringMatching("[a-z]{256}")
        val fakeValue = forge.aStringMatching("[a-zA-Z0-9]{256}")
        val fakeOtherMember = forge.aTraceStateMemberList(1).first()
        val fakeHeader = "$fakeKey=$fakeValue,${fakeOtherMember.first}=${fakeOtherMember.second}"

        // When
        val result = TraceStateHelper.decodeHeader(fakeHeader)

        // Then
        assertThat(result.size()).isEqualTo(2)
        assertThat(result.get(fakeKey)).isEqualTo(fakeValue)
    }

    // endregion

    // region Empty headers

    @Test
    fun `M return empty W decodeHeader() {empty header}`() {
        // When
        val result = TraceStateHelper.decodeHeader("")

        // Then
        assertThat(result.isEmpty).isTrue()
    }

    @Test
    fun `M return empty W decodeHeader() {whitespace-only header}`(forge: Forge) {
        // When
        val result = TraceStateHelper.decodeHeader(forgeWhitespace(forge))

        // Then
        assertThat(result.isEmpty).isTrue()
    }

    @Test
    fun `M return empty W decodeHeader() {empty members without whitespace}`(forge: Forge) {
        // When
        val result = TraceStateHelper.decodeHeader(",".repeat(forge.anInt(min = 1, max = 10)))

        // Then
        assertThat(result.isEmpty).isTrue()
    }

    @Test
    fun `M return empty W decodeHeader() {empty members with spaces and tabs}`(forge: Forge) {
        // Given
        val fakeHeader = List(forge.anInt(min = 2, max = 10)) { forgeWhitespace(forge) }.joinToString(",")

        // When
        val result = TraceStateHelper.decodeHeader(fakeHeader)

        // Then
        assertThat(result.isEmpty).isTrue()
    }

    // endregion

    // region Invalid members

    @Test
    fun `M return default W decodeHeader() {duplicate key}`(forge: Forge) {
        // Given
        val fakeKey = forge.aStringMatching(KEY_REGEX)
        val fakeHeader = "$fakeKey=${forge.aStringMatching(VALUE_REGEX)},$fakeKey=${forge.aStringMatching(VALUE_REGEX)}"

        // When
        val result = TraceStateHelper.decodeHeader(fakeHeader)

        // Then
        assertThat(result).isEqualTo(TraceState.getDefault())
    }

    @Test
    fun `M return default W decodeHeader() {empty value}`(forge: Forge) {
        // When
        val result = TraceStateHelper.decodeHeader("${forge.aStringMatching(KEY_REGEX)}=")

        // Then
        assertThat(result).isEqualTo(TraceState.getDefault())
    }

    @Test
    fun `M return default W decodeHeader() {empty key}`(forge: Forge) {
        // When
        val result = TraceStateHelper.decodeHeader("=${forge.aStringMatching(VALUE_REGEX)}")

        // Then
        assertThat(result).isEqualTo(TraceState.getDefault())
    }

    @Test
    fun `M return default W decodeHeader() {uppercase key}`(forge: Forge) {
        // When
        val result = TraceStateHelper.decodeHeader(
            "${forge.aStringMatching("[A-Z]{1,8}")}=${forge.aStringMatching(VALUE_REGEX)}"
        )

        // Then
        assertThat(result).isEqualTo(TraceState.getDefault())
    }

    @Test
    fun `M return default W decodeHeader() {equals sign in value}`(forge: Forge) {
        // Given
        val fakeHeader = "${forge.aStringMatching(KEY_REGEX)}=" +
            "${forge.aStringMatching(VALUE_REGEX)}=${forge.aStringMatching(VALUE_REGEX)}"

        // When
        val result = TraceStateHelper.decodeHeader(fakeHeader)

        // Then
        assertThat(result).isEqualTo(TraceState.getDefault())
    }

    @ParameterizedTest
    @ValueSource(chars = ['\t', '\n'])
    fun `M return default W decodeHeader() {control character in value}`(fakeControlChar: Char, forge: Forge) {
        // Given
        val fakeHeader = "${forge.aStringMatching(KEY_REGEX)}=" +
            "${forge.aStringMatching(VALUE_REGEX)}$fakeControlChar${forge.aStringMatching(VALUE_REGEX)}"

        // When
        val result = TraceStateHelper.decodeHeader(fakeHeader)

        // Then
        assertThat(result).isEqualTo(TraceState.getDefault())
    }

    @Test
    fun `M return default W decodeHeader() {member without key-value delimiter}`(forge: Forge) {
        // Given
        val fakeMember = forge.aTraceStateMemberList(1).first()
        val fakeHeader = "${fakeMember.first}=${fakeMember.second},${forge.aStringMatching(KEY_REGEX)}"

        // When
        val result = TraceStateHelper.decodeHeader(fakeHeader)

        // Then
        assertThat(result).isEqualTo(TraceState.getDefault())
    }

    @Test
    fun `M return default W decodeHeader() {too many members}`(forge: Forge) {
        // Given
        val fakeMembers = forge.aTraceStateMemberList(MAX_MEMBERS + 1)
        val fakeHeader = fakeMembers.joinToString(",") { "${it.first}=${it.second}" }

        // When
        val result = TraceStateHelper.decodeHeader(fakeHeader)

        // Then
        assertThat(result).isEqualTo(TraceState.getDefault())
    }

    @Test
    fun `M drop invalid vendor key W decodeHeader() {illegal vendor format}`(forge: Forge) {
        // Given
        val fakeVendor = forge.aStringMatching("[a-z]{1,8}")
        val fakeTenant = forge.aStringMatching("[a-z]{1,8}")
        val fakeInvalidKey = forge.anElementFrom(
            "$fakeTenant@",
            "@$fakeVendor",
            "$fakeTenant@@$fakeVendor",
            "$fakeTenant@$fakeVendor@${forge.aStringMatching("[a-z]{1,8}")}"
        )
        val fakeValidMember = forge.aTraceStateMemberList(1).first()
        val fakeHeader = "$fakeInvalidKey=${forge.aStringMatching(VALUE_REGEX)}," +
            "${fakeValidMember.first}=${fakeValidMember.second}"

        // When
        val result = TraceStateHelper.decodeHeader(fakeHeader)

        // Then
        assertThat(result.get(fakeValidMember.first)).isNull()
    }

    // endregion

    // region Large inputs (CVE-2026-118259)

    @ParameterizedTest
    @ValueSource(chars = [' ', '\t'])
    @Timeout(DECODE_TIMEOUT_SECONDS)
    fun `M decode in linear time W decodeHeader() {long whitespace runs}`(fakeWhitespace: Char, forge: Forge) {
        // Given
        val fakeMember = forge.aTraceStateMemberList(1).first()
        val fakeMemberString = "${fakeMember.first}=${fakeMember.second}"
        val expected = TraceState.builder().put(fakeMember.first, fakeMember.second).build()
        val padding = fakeWhitespace.toString().repeat(LARGE_INPUT_LENGTH)

        // When / Then
        assertThat(TraceStateHelper.decodeHeader("$fakeMemberString${padding}x").isEmpty).isTrue()
        assertThat(TraceStateHelper.decodeHeader("$fakeMemberString$padding")).isEqualTo(expected)
        assertThat(TraceStateHelper.decodeHeader("$padding$fakeMemberString$padding")).isEqualTo(expected)
        assertThat(TraceStateHelper.decodeHeader("$fakeMemberString,$padding")).isEqualTo(expected)
        assertThat(TraceStateHelper.decodeHeader(padding).isEmpty).isTrue()
    }

    @Test
    @Timeout(DECODE_TIMEOUT_SECONDS)
    fun `M decode in linear time W decodeHeader() {many empty members}`(forge: Forge) {
        // Given
        val fakeMember = forge.aTraceStateMemberList(1).first()
        val fakeHeader = ",".repeat(LARGE_INPUT_LENGTH) + "${fakeMember.first}=${fakeMember.second}"

        // When
        val result = TraceStateHelper.decodeHeader(fakeHeader)

        // Then
        assertThat(result).isEqualTo(TraceState.builder().put(fakeMember.first, fakeMember.second).build())
    }

    // endregion

    private fun forgeWhitespace(forge: Forge): String {
        return forge.aStringMatching("[ \t]{1,4}")
    }

    companion object {
        private const val MAX_MEMBERS = 32
        private const val KEY_REGEX = "[a-z][a-z0-9_]{0,15}"
        private const val VALUE_REGEX = "[a-zA-Z0-9]{1,16}"
        private const val LARGE_INPUT_LENGTH = 100_000
        private const val DECODE_TIMEOUT_SECONDS = 5L

        private fun Forge.aTraceStateMemberList(count: Int): List<Pair<String, String>> {
            val keys = mutableSetOf<String>()
            while (keys.size < count) {
                keys.add(aStringMatching(KEY_REGEX))
            }
            return keys.map { it to aStringMatching(VALUE_REGEX) }
        }

        private fun Forge.anEmptyTraceStateMembers(): String {
            return aStringMatching("[ \t]{0,2}(,[ \t]{0,2}){1,3}")
        }
    }
}
