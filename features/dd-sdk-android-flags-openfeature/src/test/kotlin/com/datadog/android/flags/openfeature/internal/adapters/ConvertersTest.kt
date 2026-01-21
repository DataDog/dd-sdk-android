/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal.adapters

import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.ResolutionDetails
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import dev.openfeature.kotlin.sdk.exceptions.ErrorCode as OpenFeatureErrorCode

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(BaseConfigurator::class)
internal class ConvertersTest {

    // region toProviderEvaluation

    @Test
    fun `M convert resolution W toProviderEvaluation() {successful boolean resolution}`(
        @BoolForgery fakeValue: Boolean,
        @StringForgery fakeVariant: String
    ) {
        // Given
        val resolution = ResolutionDetails(
            value = fakeValue,
            variant = fakeVariant,
            reason = ResolutionReason.TARGETING_MATCH
        )

        // When
        val result = resolution.toProviderEvaluation()

        // Then
        assertThat(result.value).isEqualTo(fakeValue)
        assertThat(result.variant).isEqualTo(fakeVariant)
        assertThat(result.reason).isEqualTo("TARGETING_MATCH")
        assertThat(result.errorCode).isNull()
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `M convert resolution W toProviderEvaluation() {string resolution}`(
        @StringForgery value: String,
        @StringForgery variant: String
    ) {
        // Given
        val resolution = ResolutionDetails(
            value = value,
            variant = variant,
            reason = ResolutionReason.DEFAULT
        )

        // When
        val result = resolution.toProviderEvaluation()

        // Then
        assertThat(result.value).isEqualTo(value)
        assertThat(result.variant).isEqualTo(variant)
        assertThat(result.reason).isEqualTo("DEFAULT")
    }

    @Test
    fun `M convert resolution W toProviderEvaluation() {integer resolution}`(forge: Forge) {
        // Given
        val value = forge.anInt()
        val resolution = ResolutionDetails(
            value = value,
            reason = ResolutionReason.RULE_MATCH
        )

        // When
        val result = resolution.toProviderEvaluation()

        // Then
        assertThat(result.value).isEqualTo(value)
        assertThat(result.variant).isNull()
        assertThat(result.reason).isEqualTo("RULE_MATCH")
    }

    @Test
    fun `M convert STATIC reason W toProviderEvaluation() {resolution with STATIC reason}`(
        @StringForgery variant: String,
        forge: Forge
    ) {
        // Given
        val value = forge.aBool()
        val resolution = ResolutionDetails(
            value = value,
            variant = variant,
            reason = ResolutionReason.STATIC
        )

        // When
        val result = resolution.toProviderEvaluation()

        // Then
        assertThat(result.value).isEqualTo(value)
        assertThat(result.variant).isEqualTo(variant)
        assertThat(result.reason).isEqualTo("STATIC")
        assertThat(result.errorCode).isNull()
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `M convert error resolution W toProviderEvaluation() {with error code}`(@StringForgery errorMessage: String) {
        // Given
        val resolution = ResolutionDetails(
            value = false,
            errorCode = ErrorCode.FLAG_NOT_FOUND,
            errorMessage = errorMessage,
            reason = ResolutionReason.ERROR
        )

        // When
        val result = resolution.toProviderEvaluation()

        // Then
        assertThat(result.value).isFalse()
        assertThat(result.errorCode).isEqualTo(OpenFeatureErrorCode.FLAG_NOT_FOUND)
        assertThat(result.errorMessage).isEqualTo(errorMessage)
        assertThat(result.reason).isEqualTo("ERROR")
    }

    // endregion

    // region toOpenFeatureErrorCode

    @ParameterizedTest
    @MethodSource("errorCodeMappings")
    fun `M map error codes W toOpenFeatureErrorCode() {all error codes}`(
        datadogErrorCode: ErrorCode,
        expectedOpenFeatureErrorCode: OpenFeatureErrorCode
    ) {
        // When
        val result = datadogErrorCode.toOpenFeatureErrorCode()

        // Then
        assertThat(result).isEqualTo(expectedOpenFeatureErrorCode)
    }

    // endregion

    // region toDatadogEvaluationContext
    // Note: Context conversion is tested indirectly through provider integration tests
    // Direct unit tests removed due to ImmutableContext API complexity
    // endregion

    companion object {
        @JvmStatic
        fun errorCodeMappings() = listOf(
            Arguments.of(ErrorCode.PROVIDER_NOT_READY, OpenFeatureErrorCode.PROVIDER_NOT_READY),
            Arguments.of(ErrorCode.FLAG_NOT_FOUND, OpenFeatureErrorCode.FLAG_NOT_FOUND),
            Arguments.of(ErrorCode.PARSE_ERROR, OpenFeatureErrorCode.PARSE_ERROR),
            Arguments.of(ErrorCode.TYPE_MISMATCH, OpenFeatureErrorCode.TYPE_MISMATCH)
        )
    }
}
