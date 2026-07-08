/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import com.datadog.android.sessionreplay.IMAGE_DIMEN_CONSIDERED_PII_IN_DP
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PixelCaptureEligibilityTest {

    private val fakeSmallBounds = GlobalBounds(
        x = 0L,
        y = 0L,
        width = (IMAGE_DIMEN_CONSIDERED_PII_IN_DP - 1).toLong(),
        height = (IMAGE_DIMEN_CONSIDERED_PII_IN_DP - 1).toLong()
    )

    private val fakeLargeBounds = GlobalBounds(
        x = 0L,
        y = 0L,
        width = IMAGE_DIMEN_CONSIDERED_PII_IN_DP.toLong(),
        height = IMAGE_DIMEN_CONSIDERED_PII_IN_DP.toLong()
    )

    @ParameterizedTest
    @EnumSource(
        value = TextAndInputPrivacy::class,
        names = ["MASK_SENSITIVE_INPUTS"],
        mode = EnumSource.Mode.EXCLUDE
    )
    fun `M return false W isEligible() {textAndInputPrivacy stricter than MASK_SENSITIVE_INPUTS}`(
        fakeTextAndInputPrivacy: TextAndInputPrivacy
    ) {
        // When
        val result = PixelCaptureEligibility.isEligible(
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            boundsDp = fakeSmallBounds
        )

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W isEligible() {imagePrivacy is MASK_ALL}`() {
        // When
        val result = PixelCaptureEligibility.isEligible(
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            imagePrivacy = ImagePrivacy.MASK_ALL,
            boundsDp = fakeSmallBounds
        )

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return true W isEligible() {imagePrivacy is MASK_NONE, any size}`() {
        // When
        val result = PixelCaptureEligibility.isEligible(
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            boundsDp = fakeLargeBounds
        )

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return true W isEligible() {imagePrivacy is MASK_LARGE_ONLY, bounds below threshold}`() {
        // When
        val result = PixelCaptureEligibility.isEligible(
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            boundsDp = fakeSmallBounds
        )

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W isEligible() {imagePrivacy is MASK_LARGE_ONLY, bounds at or above threshold}`() {
        // When
        val result = PixelCaptureEligibility.isEligible(
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            boundsDp = fakeLargeBounds
        )

        // Then
        assertThat(result).isFalse()
    }
}
