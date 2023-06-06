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
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = ForgeConfigurator::class)
internal class SessionReplayConfigurationTest {

    lateinit var testSessionReplayConfiguration: SessionReplayConfiguration

    @StringForgery
    lateinit var fakeEndpoint: String

    @FloatForgery
    var fakeSamplingRate: Float = 0f

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
    fun `M resolve the correct custom Mappers W customMappers { ALLOW_ALL }`() {
        // Given
        testSessionReplayConfiguration = SessionReplayConfiguration(
            fakeEndpoint,
            SessionReplayPrivacy.ALLOW_ALL,
            mockExtensionSupport,
            fakeSamplingRate
        )

        // Then
        assertThat(testSessionReplayConfiguration.customMappers())
            .isEqualTo(fakeExpectedAllowAllCustomMappers)
    }

    @Test
    fun `M resolve the correct custom Mappers W customMappers { MASK_ALL }`() {
        // Given
        testSessionReplayConfiguration = SessionReplayConfiguration(
            fakeEndpoint,
            SessionReplayPrivacy.MASK_ALL,
            mockExtensionSupport,
            fakeSamplingRate
        )

        // Then
        assertThat(testSessionReplayConfiguration.customMappers())
            .isEqualTo(fakeExpectedMaskAllCustomMappers)
    }

    @Test
    fun `M return empty map W customMappers { no mappers provided }`(forge: Forge) {
        // Given
        val fakePrivacy = forge.aValueFrom(SessionReplayPrivacy::class.java)
        whenever(mockExtensionSupport.getCustomViewMappers()).thenReturn(emptyMap())
        testSessionReplayConfiguration = SessionReplayConfiguration(
            fakeEndpoint,
            fakePrivacy,
            mockExtensionSupport,
            fakeSamplingRate
        )

        // Then
        assertThat(testSessionReplayConfiguration.customMappers()).isEmpty()
    }
}
