/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.view.View
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.mapper.MapperTypeWrapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = ForgeConfigurator::class)
internal class SessionReplayConfigurationBuilderTest {

    lateinit var testedBuilder: SessionReplayConfiguration.Builder

    @Mock
    lateinit var mockExtensionSupport: ExtensionSupport
    lateinit var fakeCustomMappers: Map<SessionReplayPrivacy, Map<Class<*>, WireframeMapper<View, *>>>
    lateinit var fakeExpectedAllowCustomMappers: List<MapperTypeWrapper>
    lateinit var fakeExpectedMaskCustomMappers: List<MapperTypeWrapper>
    lateinit var fakeAllowCustomMappers: Map<Class<*>, WireframeMapper<View, *>>
    lateinit var fakeMaskCustomMappers: Map<Class<*>, WireframeMapper<View, *>>

    @FloatForgery
    var fakeSampleRate: Float = 0f

    @BeforeEach
    fun `set up`() {
        fakeExpectedAllowCustomMappers = listOf(MapperTypeWrapper(Any::class.java, mock()))
        fakeExpectedMaskCustomMappers = listOf(MapperTypeWrapper(Any::class.java, mock()))
        fakeAllowCustomMappers = fakeExpectedAllowCustomMappers.associate {
            it.type to it.mapper
        }
        fakeMaskCustomMappers =
            fakeExpectedMaskCustomMappers.associate { it.type to it.mapper }
        fakeCustomMappers = mapOf(
            SessionReplayPrivacy.ALLOW to fakeAllowCustomMappers,
            SessionReplayPrivacy.MASK to fakeMaskCustomMappers
        )
        whenever(mockExtensionSupport.getCustomViewMappers()).thenReturn(fakeCustomMappers)
        testedBuilder = SessionReplayConfiguration.Builder(fakeSampleRate)
    }

    @Test
    fun `𝕄 use sensible defaults 𝕎 build()`() {
        // When
        val sessionReplayConfiguration = testedBuilder.build()

        // Then
        assertThat(sessionReplayConfiguration.customEndpointUrl).isNull()
        assertThat(sessionReplayConfiguration.privacy).isEqualTo(SessionReplayPrivacy.MASK)
        assertThat(sessionReplayConfiguration.customMappers).isEmpty()
        assertThat(sessionReplayConfiguration.customOptionSelectorDetectors).isEmpty()
        assertThat(sessionReplayConfiguration.sampleRate).isEqualTo(fakeSampleRate)
    }

    @Test
    fun `𝕄 build config with custom site 𝕎 useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com") sessionReplayUrl: String
    ) {
        // When
        val sessionReplayConfiguration = testedBuilder
            .useCustomEndpoint(sessionReplayUrl)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.customEndpointUrl)
            .isEqualTo(sessionReplayUrl)
        assertThat(sessionReplayConfiguration.privacy).isEqualTo(SessionReplayPrivacy.MASK)
        assertThat(sessionReplayConfiguration.customMappers).isEmpty()
        assertThat(sessionReplayConfiguration.customOptionSelectorDetectors).isEmpty()
        assertThat(sessionReplayConfiguration.sampleRate).isEqualTo(fakeSampleRate)
    }

    @Test
    fun `𝕄 use the given privacy rule 𝕎 setSessionReplayPrivacy`(
        @Forgery fakePrivacy: SessionReplayPrivacy
    ) {
        // When
        val sessionReplayConfiguration = testedBuilder
            .setPrivacy(fakePrivacy)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.customEndpointUrl).isNull()
        assertThat(sessionReplayConfiguration.privacy).isEqualTo(fakePrivacy)
        assertThat(sessionReplayConfiguration.customMappers).isEmpty()
        assertThat(sessionReplayConfiguration.customOptionSelectorDetectors).isEmpty()
        assertThat(sessionReplayConfiguration.sampleRate).isEqualTo(fakeSampleRate)
    }

    @Test
    fun `M resolve the correct custom Mappers W addExtensionSupport { ALLOW }`() {
        // Given
        val sessionReplayConfiguration = testedBuilder
            .setPrivacy(SessionReplayPrivacy.ALLOW)
            .addExtensionSupport(mockExtensionSupport)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.customMappers)
            .isEqualTo(fakeExpectedAllowCustomMappers)
    }

    @Test
    fun `M resolve the correct custom Mappers W addExtensionSupport { MASK }`() {
        // Given
        val sessionReplayConfiguration = testedBuilder
            .setPrivacy(SessionReplayPrivacy.MASK)
            .addExtensionSupport(mockExtensionSupport)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.customMappers)
            .isEqualTo(fakeExpectedMaskCustomMappers)
    }

    @Test
    fun `M return empty map W addExtensionSupport { no mappers provided }`(forge: Forge) {
        // Given
        val fakePrivacy = forge.aValueFrom(SessionReplayPrivacy::class.java)
        whenever(mockExtensionSupport.getCustomViewMappers()).thenReturn(emptyMap())
        val sessionReplayConfiguration = testedBuilder
            .setPrivacy(fakePrivacy)
            .addExtensionSupport(mockExtensionSupport)
            .build()

        // Then
        assertThat(sessionReplayConfiguration.customMappers).isEmpty()
    }
}
