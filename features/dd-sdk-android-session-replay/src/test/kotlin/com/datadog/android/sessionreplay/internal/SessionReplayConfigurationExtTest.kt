/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.core.internal.remote.model.RemoteConfiguration
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SessionReplayConfigurationExtTest {

    private lateinit var testedConfiguration: SessionReplayConfiguration

    @BeforeEach
    fun setUp(forge: Forge) {
        testedConfiguration = forge.getForgery()
    }

    // region null RC

    @Test
    fun `M return config unchanged W applyRemoteConfiguration { null RC }`() {
        // When
        val result = testedConfiguration.applyRemoteConfiguration(null)

        // Then
        assertThat(result).isEqualTo(testedConfiguration)
    }

    @Test
    fun `M return config unchanged W applyRemoteConfiguration { RC with null sessionReplay }`() {
        // Given
        val fakeRc = RemoteConfiguration(sessionReplay = null)

        // When
        val result = testedConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result).isEqualTo(testedConfiguration)
    }

    // endregion

    // region sampleRate

    @Test
    fun `M override sampleRate W applyRemoteConfiguration { RC provides sampleRate }`(
        @FloatForgery(min = 0f, max = 100f) fakeSampleRate: Float
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            sessionReplay = RemoteConfiguration.SessionReplay(sampleRate = fakeSampleRate)
        )

        // When
        val result = testedConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.sampleRate).isEqualTo(fakeSampleRate)
    }

    @Test
    fun `M keep existing sampleRate W applyRemoteConfiguration { RC omits sampleRate }`() {
        // Given
        val fakeRc = RemoteConfiguration(
            sessionReplay = RemoteConfiguration.SessionReplay(sampleRate = null)
        )
        val expectedSampleRate = testedConfiguration.sampleRate

        // When
        val result = testedConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.sampleRate).isEqualTo(expectedSampleRate)
    }

    // endregion

    // region startRecordingImmediately

    @Test
    fun `M override startRecordingImmediately W applyRemoteConfiguration { RC provides true }`() {
        // Given
        val baseline = testedConfiguration.copy(startRecordingImmediately = false)
        val fakeRc = RemoteConfiguration(
            sessionReplay = RemoteConfiguration.SessionReplay(startRecordingImmediately = true)
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.startRecordingImmediately).isTrue()
    }

    @Test
    fun `M override startRecordingImmediately W applyRemoteConfiguration { RC provides false }`() {
        // Given
        val baseline = testedConfiguration.copy(startRecordingImmediately = true)
        val fakeRc = RemoteConfiguration(
            sessionReplay = RemoteConfiguration.SessionReplay(startRecordingImmediately = false)
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.startRecordingImmediately).isFalse()
    }

    @Test
    fun `M keep existing startRecordingImmediately W applyRemoteConfiguration { RC omits it }`() {
        // Given
        val fakeRc = RemoteConfiguration(
            sessionReplay = RemoteConfiguration.SessionReplay(startRecordingImmediately = null)
        )
        val expectedValue = testedConfiguration.startRecordingImmediately

        // When
        val result = testedConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.startRecordingImmediately).isEqualTo(expectedValue)
    }

    // endregion

    // region textAndInputPrivacy

    @Test
    fun `M override textAndInputPrivacy W applyRemoteConfiguration { RC MASK_SENSITIVE_INPUTS }`() {
        // Given
        val baseline = testedConfiguration.copy(textAndInputPrivacy = TextAndInputPrivacy.MASK_ALL)
        val fakeRc = RemoteConfiguration(
            sessionReplay = RemoteConfiguration.SessionReplay(
                textAndInputPrivacy = RemoteConfiguration.TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.textAndInputPrivacy).isEqualTo(TextAndInputPrivacy.MASK_SENSITIVE_INPUTS)
    }

    @Test
    fun `M override textAndInputPrivacy W applyRemoteConfiguration { RC MASK_ALL_INPUTS }`() {
        // Given
        val baseline = testedConfiguration.copy(textAndInputPrivacy = TextAndInputPrivacy.MASK_ALL)
        val fakeRc = RemoteConfiguration(
            sessionReplay = RemoteConfiguration.SessionReplay(
                textAndInputPrivacy = RemoteConfiguration.TextAndInputPrivacy.MASK_ALL_INPUTS
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.textAndInputPrivacy).isEqualTo(TextAndInputPrivacy.MASK_ALL_INPUTS)
    }

    @Test
    fun `M override textAndInputPrivacy W applyRemoteConfiguration { RC MASK_ALL }`() {
        // Given
        val baseline = testedConfiguration.copy(
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
        )
        val fakeRc = RemoteConfiguration(
            sessionReplay = RemoteConfiguration.SessionReplay(
                textAndInputPrivacy = RemoteConfiguration.TextAndInputPrivacy.MASK_ALL
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.textAndInputPrivacy).isEqualTo(TextAndInputPrivacy.MASK_ALL)
    }

    @Test
    fun `M keep existing textAndInputPrivacy W applyRemoteConfiguration { RC omits it }`() {
        // Given
        val fakeRc = RemoteConfiguration(
            sessionReplay = RemoteConfiguration.SessionReplay(textAndInputPrivacy = null)
        )
        val expectedValue = testedConfiguration.textAndInputPrivacy

        // When
        val result = testedConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.textAndInputPrivacy).isEqualTo(expectedValue)
    }

    // endregion

    // region imagePrivacy

    @Test
    fun `M override imagePrivacy W applyRemoteConfiguration { RC MASK_NONE }`() {
        // Given
        val baseline = testedConfiguration.copy(imagePrivacy = ImagePrivacy.MASK_ALL)
        val fakeRc = RemoteConfiguration(
            sessionReplay = RemoteConfiguration.SessionReplay(
                imagePrivacy = RemoteConfiguration.ImagePrivacy.MASK_NONE
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.imagePrivacy).isEqualTo(ImagePrivacy.MASK_NONE)
    }

    @Test
    fun `M override imagePrivacy W applyRemoteConfiguration { RC MASK_LARGE_ONLY }`() {
        // Given
        val baseline = testedConfiguration.copy(imagePrivacy = ImagePrivacy.MASK_ALL)
        val fakeRc = RemoteConfiguration(
            sessionReplay = RemoteConfiguration.SessionReplay(
                imagePrivacy = RemoteConfiguration.ImagePrivacy.MASK_LARGE_ONLY
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.imagePrivacy).isEqualTo(ImagePrivacy.MASK_LARGE_ONLY)
    }

    @Test
    fun `M override imagePrivacy W applyRemoteConfiguration { RC MASK_ALL }`() {
        // Given
        val baseline = testedConfiguration.copy(imagePrivacy = ImagePrivacy.MASK_NONE)
        val fakeRc = RemoteConfiguration(
            sessionReplay = RemoteConfiguration.SessionReplay(
                imagePrivacy = RemoteConfiguration.ImagePrivacy.MASK_ALL
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.imagePrivacy).isEqualTo(ImagePrivacy.MASK_ALL)
    }

    @Test
    fun `M keep existing imagePrivacy W applyRemoteConfiguration { RC omits it }`() {
        // Given
        val fakeRc = RemoteConfiguration(
            sessionReplay = RemoteConfiguration.SessionReplay(imagePrivacy = null)
        )
        val expectedValue = testedConfiguration.imagePrivacy

        // When
        val result = testedConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.imagePrivacy).isEqualTo(expectedValue)
    }

    // endregion

    // region touchPrivacy

    @Test
    fun `M override touchPrivacy W applyRemoteConfiguration { RC SHOW }`() {
        // Given
        val baseline = testedConfiguration.copy(touchPrivacy = TouchPrivacy.HIDE)
        val fakeRc = RemoteConfiguration(
            sessionReplay = RemoteConfiguration.SessionReplay(
                touchPrivacy = RemoteConfiguration.TouchPrivacy.SHOW
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.touchPrivacy).isEqualTo(TouchPrivacy.SHOW)
    }

    @Test
    fun `M override touchPrivacy W applyRemoteConfiguration { RC HIDE }`() {
        // Given
        val baseline = testedConfiguration.copy(touchPrivacy = TouchPrivacy.SHOW)
        val fakeRc = RemoteConfiguration(
            sessionReplay = RemoteConfiguration.SessionReplay(
                touchPrivacy = RemoteConfiguration.TouchPrivacy.HIDE
            )
        )

        // When
        val result = baseline.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.touchPrivacy).isEqualTo(TouchPrivacy.HIDE)
    }

    @Test
    fun `M keep existing touchPrivacy W applyRemoteConfiguration { RC omits it }`() {
        // Given
        val fakeRc = RemoteConfiguration(
            sessionReplay = RemoteConfiguration.SessionReplay(touchPrivacy = null)
        )
        val expectedValue = testedConfiguration.touchPrivacy

        // When
        val result = testedConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.touchPrivacy).isEqualTo(expectedValue)
    }

    // endregion
}
