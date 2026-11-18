/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EffectivePrivacyTest {

    @Mock
    lateinit var mockView: View

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var fakeInherited: EffectivePrivacy

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeInherited = EffectivePrivacy(forge.getForgery(), forge.getForgery())
    }

    @Test
    fun `M return the inherited privacy W resolveEffectivePrivacy() { no override tags }`() {
        // When
        val result = resolveEffectivePrivacy(mockView, fakeInherited, mockInternalLogger)

        // Then
        assertThat(result).isSameAs(fakeInherited)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M apply the image override W resolveEffectivePrivacy() { image privacy tag }`(forge: Forge) {
        // Given
        val fakeOverride = forge.aValueFrom(ImagePrivacy::class.java, exclude = listOf(fakeInherited.imagePrivacy))
        whenever(mockView.getTag(R.id.datadog_image_privacy)).thenReturn(fakeOverride.toString())

        // When
        val result = resolveEffectivePrivacy(mockView, fakeInherited, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(EffectivePrivacy(fakeOverride, fakeInherited.textAndInputPrivacy))
    }

    @Test
    fun `M apply the text override W resolveEffectivePrivacy() { text and input privacy tag }`(forge: Forge) {
        // Given
        val fakeOverride = forge.aValueFrom(
            TextAndInputPrivacy::class.java,
            exclude = listOf(fakeInherited.textAndInputPrivacy)
        )
        whenever(mockView.getTag(R.id.datadog_text_and_input_privacy)).thenReturn(fakeOverride.toString())

        // When
        val result = resolveEffectivePrivacy(mockView, fakeInherited, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(EffectivePrivacy(fakeInherited.imagePrivacy, fakeOverride))
    }

    @Test
    fun `M apply both overrides W resolveEffectivePrivacy() { both privacy tags }`(forge: Forge) {
        // Given
        val fakeImageOverride = forge.aValueFrom(ImagePrivacy::class.java, exclude = listOf(fakeInherited.imagePrivacy))
        val fakeTextOverride = forge.aValueFrom(
            TextAndInputPrivacy::class.java,
            exclude = listOf(fakeInherited.textAndInputPrivacy)
        )
        whenever(mockView.getTag(R.id.datadog_image_privacy)).thenReturn(fakeImageOverride.toString())
        whenever(mockView.getTag(R.id.datadog_text_and_input_privacy)).thenReturn(fakeTextOverride.toString())

        // When
        val result = resolveEffectivePrivacy(mockView, fakeInherited, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(EffectivePrivacy(fakeImageOverride, fakeTextOverride))
    }

    @Test
    fun `M keep the inherited privacy W resolveEffectivePrivacy() { non-string tags }`(
        @IntForgery fakeImageTag: Int,
        @IntForgery fakeTextTag: Int
    ) {
        // Given
        whenever(mockView.getTag(R.id.datadog_image_privacy)).thenReturn(fakeImageTag)
        whenever(mockView.getTag(R.id.datadog_text_and_input_privacy)).thenReturn(fakeTextTag)

        // When
        val result = resolveEffectivePrivacy(mockView, fakeInherited, mockInternalLogger)

        // Then
        assertThat(result).isSameAs(fakeInherited)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M keep the inherited privacy and log W resolveEffectivePrivacy() { unrecognized tag value }`(
        @StringForgery(regex = "[a-z]{8}") fakeInvalidTag: String
    ) {
        // Given
        whenever(mockView.getTag(R.id.datadog_image_privacy)).thenReturn(fakeInvalidTag)

        // When
        val result = resolveEffectivePrivacy(mockView, fakeInherited, mockInternalLogger)

        // Then
        assertThat(result).isSameAs(fakeInherited)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            "Invalid privacy level",
            throwableClass = IllegalArgumentException::class.java,
            onlyOnce = true
        )
    }
}
