/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.view.View
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.domain.SessionReplayRequestFactory
import com.datadog.android.sessionreplay.internal.recorder.mapper.MapperTypeWrapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import fr.xgouchet.elmyr.Forge
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
internal class SessionReplayBuilderTest {

    private var testedBuilder: SessionReplayFeature.Builder = SessionReplayFeature.Builder()

    @Mock
    lateinit var mockExtensionSupport: ExtensionSupport
    lateinit var fakeCustomMappers: Map<SessionReplayPrivacy, Map<Class<*>, WireframeMapper<View, *>>>
    lateinit var fakeExpectedAllowAllCustomMappers: List<MapperTypeWrapper>
    lateinit var fakeExpectedMaskAllCustomMappers: List<MapperTypeWrapper>
    lateinit var fakeAllowAllCustomMappers: Map<Class<*>, WireframeMapper<View, *>>
    lateinit var fakeMaskAllCustomMappers: Map<Class<*>, WireframeMapper<View, *>>

    @BeforeEach
    fun `set up`() {
        fakeExpectedAllowAllCustomMappers = listOf(MapperTypeWrapper(Any::class.java, mock()))
        fakeExpectedMaskAllCustomMappers = listOf(MapperTypeWrapper(Any::class.java, mock()))
        fakeAllowAllCustomMappers = fakeExpectedAllowAllCustomMappers.associate {
            it.type to it.mapper
        }
        fakeMaskAllCustomMappers = fakeExpectedMaskAllCustomMappers.associate { it.type to it.mapper }
        fakeCustomMappers = mapOf(
            SessionReplayPrivacy.ALLOW_ALL to fakeAllowAllCustomMappers,
            SessionReplayPrivacy.MASK_ALL to fakeMaskAllCustomMappers
        )
        whenever(mockExtensionSupport.getCustomViewMappers()).thenReturn(fakeCustomMappers)
    }

    @Test
    fun `𝕄 use sensible defaults 𝕎 build()`() {
        // When
        val feature = testedBuilder.build()

        // Then
        assertThat(feature.requestFactory).isInstanceOf(SessionReplayRequestFactory::class.java)
        assertThat((feature.requestFactory as SessionReplayRequestFactory).customEndpointUrl)
            .isNull()
        assertThat(feature.privacy).isEqualTo(SessionReplayPrivacy.MASK_ALL)
    }

    @Test
    fun `𝕄 build config with custom site 𝕎 useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com") sessionReplayUrl: String
    ) {
        // When
        val feature = testedBuilder.useCustomEndpoint(sessionReplayUrl).build()

        // Then
        assertThat((feature.requestFactory as SessionReplayRequestFactory).customEndpointUrl)
            .isEqualTo(sessionReplayUrl)
    }

    @Test
    fun `𝕄 use the given privacy rule 𝕎 setSessionReplayPrivacy`(
        @Forgery fakePrivacy: SessionReplayPrivacy
    ) {
        // When
        val feature = testedBuilder.setPrivacy(fakePrivacy).build()

        // Then
        assertThat(feature.privacy).isEqualTo(fakePrivacy)
    }

    @Test
    fun `M resolve the correct custom Mappers W customMappers { ALLOW_ALL }`() {
        // Given
        testedBuilder = testedBuilder
            .setPrivacy(SessionReplayPrivacy.ALLOW_ALL)
            .addExtensionSupport(mockExtensionSupport)

        // Then
        assertThat(testedBuilder.customMappers())
            .isEqualTo(fakeExpectedAllowAllCustomMappers)
    }

    @Test
    fun `M resolve the correct custom Mappers W customMappers { MASK_ALL }`() {
        // Given
        testedBuilder = testedBuilder
            .setPrivacy(SessionReplayPrivacy.MASK_ALL)
            .addExtensionSupport(mockExtensionSupport)

        // Then
        assertThat(testedBuilder.customMappers())
            .isEqualTo(fakeExpectedMaskAllCustomMappers)
    }

    @Test
    fun `M return empty map W customMappers { no mappers provided }`(forge: Forge) {
        // Given
        val fakePrivacy = forge.aValueFrom(SessionReplayPrivacy::class.java)
        whenever(mockExtensionSupport.getCustomViewMappers()).thenReturn(emptyMap())
        testedBuilder = testedBuilder
            .setPrivacy(fakePrivacy)
            .addExtensionSupport(mockExtensionSupport)

        // Then
        assertThat(testedBuilder.customMappers()).isEmpty()
    }
}
