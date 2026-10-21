/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import com.datadog.android.flags.model.EvaluationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

internal class EvaluationPrivacyTest {
    @Test
    fun `M hash canonical UTF8 bytes W targetingKey()`() {
        // When / Then
        assertThat(EvaluationPrivacy.targetingKey("jane.doe@datadoghq.com", false))
            .isEqualTo("sha256_b4698f9b6d186781fa8dc59e533578fa2d8379a46b1cf6db85cda6aa9c99e51b")
        assertThat(EvaluationPrivacy.targetingKey("é", false))
            .isEqualTo("sha256_4a99557e4033c3539de2eb65472017cad5f9557f7a0625a09f1c3f6e2ba69c4c")
        assertThat(EvaluationPrivacy.targetingKey("e\u0301", false))
            .isNotEqualTo(EvaluationPrivacy.targetingKey("é", false))
        assertThat(EvaluationPrivacy.targetingKey("😀", false))
            .isEqualTo("sha256_f0443a342c5ef54783a111b51ba56c938e474c32324d90c3a60c9c8e3a37e2d9")
    }

    @ParameterizedTest
    @NullAndEmptySource
    fun `M preserve empty and omit missing W targetingKey()`(fakeKey: String?) {
        // When / Then
        assertThat(EvaluationPrivacy.targetingKey(fakeKey, false)).isEqualTo(fakeKey)
    }

    @Test
    fun `M omit malformed UTF16 W targetingKey()`() {
        // Given
        val fakeKeys = listOf("\uD800", "\uDC00", "a\uD800b", "\uD800\uD800")

        // When / Then
        fakeKeys.forEach { assertThat(EvaluationPrivacy.targetingKey(it, false)).isNull() }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "PROVIDER_NOT_READY", "PROVIDER_FATAL", "FLAG_NOT_FOUND", "PARSE_ERROR",
            "TYPE_MISMATCH", "TARGETING_KEY_MISSING", "INVALID_CONTEXT", "GENERAL"
        ]
    )
    fun `M allow stable codes W errorCode()`(fakeCode: String) {
        // When / Then
        assertThat(EvaluationPrivacy.errorCode(fakeCode, "private exception canary")).isEqualTo(fakeCode)
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["private exception canary", "flag_not_found", "UNKNOWN"])
    fun `M replace unknown failures W errorCode()`(fakeCode: String?) {
        // When / Then
        assertThat(EvaluationPrivacy.errorCode(fakeCode, "private exception canary")).isEqualTo("GENERAL")
    }

    @Test
    fun `M omit absent errors W errorCode()`() {
        // When / Then
        assertThat(EvaluationPrivacy.errorCode(null, null)).isNull()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M retain sanitized error identity W record() + drain()`(fakeConsent: Boolean) {
        // Given
        val testedAggregator = EvaluationAggregator(100)
        val fakeAttributes = mutableMapOf("email" to "private context canary")
        val fakeContext = EvaluationContext("jane.doe@datadoghq.com", fakeAttributes)

        // When
        listOf("unknown error one", "unknown error two").forEach {
            testedAggregator.record(1, "flag", fakeContext, null, null, null, null, it, it, fakeConsent)
        }
        fakeAttributes["email"] = "mutated private context canary"
        val result = testedAggregator.drain()

        // Then
        assertThat(result).hasSize(1)
        assertThat(result.single().count).isEqualTo(2)
        assertThat(result.single().errorMessage).isEqualTo("GENERAL")
        assertThat(result.single().aggregationKey.errorCode).isEqualTo("GENERAL")
        assertThat(result.toString()).doesNotContain("private context canary", "unknown error")
    }

    @Test
    fun `M separate consent even with identical emitted keys W record() + drain()`() {
        // Given
        val testedAggregator = EvaluationAggregator(100)
        val fakeContext = EvaluationContext.EMPTY

        // When
        listOf(false, true).forEach {
            testedAggregator.record(1, "flag", fakeContext, null, null, "variant", null, null, null, it)
        }
        val result = testedAggregator.drain()

        // Then
        assertThat(result).hasSize(2)
        assertThat(result.map { it.aggregationKey.observeFullEvaluationData }).containsExactly(false, true)
        assertThat(result.map { it.count }).containsOnly(1)
    }
}
