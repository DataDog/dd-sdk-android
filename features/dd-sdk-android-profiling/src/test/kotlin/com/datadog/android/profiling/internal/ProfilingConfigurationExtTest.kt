/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.core.internal.remote.model.RemoteConfiguration
import com.datadog.android.profiling.ExperimentalProfilingApi
import com.datadog.android.profiling.ProfilingConfiguration
import com.datadog.android.profiling.forge.Configurator
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

@OptIn(ExperimentalProfilingApi::class)
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ProfilingConfigurationExtTest {

    private lateinit var testedConfiguration: ProfilingConfiguration

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
    fun `M return config unchanged W applyRemoteConfiguration { RC with null profiling }`() {
        // Given
        val fakeRc = RemoteConfiguration(profiling = null)

        // When
        val result = testedConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result).isEqualTo(testedConfiguration)
    }

    @Test
    fun `M return config unchanged W applyRemoteConfiguration { RC with empty profiling namespace }`() {
        // Given
        val fakeRc = RemoteConfiguration(
            profiling = RemoteConfiguration.Profiling(
                applicationLaunchSampleRate = null,
                continuousSampleRate = null
            )
        )

        // When
        val result = testedConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result).isEqualTo(testedConfiguration)
    }

    // endregion

    // region applicationLaunchSampleRate

    @Test
    fun `M override applicationLaunchSampleRate W applyRemoteConfiguration { RC provides value }`(
        @FloatForgery(min = 0f, max = 100f) fakeSampleRate: Float
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            profiling = RemoteConfiguration.Profiling(applicationLaunchSampleRate = fakeSampleRate)
        )

        // When
        val result = testedConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.applicationLaunchSampleRate).isEqualTo(fakeSampleRate)
    }

    @Test
    fun `M keep existing applicationLaunchSampleRate W applyRemoteConfiguration { RC omits it }`() {
        // Given
        val fakeRc = RemoteConfiguration(
            profiling = RemoteConfiguration.Profiling(applicationLaunchSampleRate = null)
        )
        val expectedValue = testedConfiguration.applicationLaunchSampleRate

        // When
        val result = testedConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.applicationLaunchSampleRate).isEqualTo(expectedValue)
    }

    // endregion

    // region continuousSampleRate

    @Test
    fun `M override continuousSampleRate W applyRemoteConfiguration { RC provides value }`(
        @FloatForgery(min = 0f, max = 100f) fakeSampleRate: Float
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            profiling = RemoteConfiguration.Profiling(continuousSampleRate = fakeSampleRate)
        )

        // When
        val result = testedConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.continuousSampleRate).isEqualTo(fakeSampleRate)
    }

    @Test
    fun `M keep existing continuousSampleRate W applyRemoteConfiguration { RC omits it }`() {
        // Given
        val fakeRc = RemoteConfiguration(
            profiling = RemoteConfiguration.Profiling(continuousSampleRate = null)
        )
        val expectedValue = testedConfiguration.continuousSampleRate

        // When
        val result = testedConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.continuousSampleRate).isEqualTo(expectedValue)
    }

    // endregion

    // region both fields

    @Test
    fun `M override both fields W applyRemoteConfiguration { RC provides both values }`(
        @FloatForgery(min = 0f, max = 100f) fakeAppLaunchRate: Float,
        @FloatForgery(min = 0f, max = 100f) fakeContinuousRate: Float
    ) {
        // Given
        val fakeRc = RemoteConfiguration(
            profiling = RemoteConfiguration.Profiling(
                applicationLaunchSampleRate = fakeAppLaunchRate,
                continuousSampleRate = fakeContinuousRate
            )
        )

        // When
        val result = testedConfiguration.applyRemoteConfiguration(fakeRc)

        // Then
        assertThat(result.applicationLaunchSampleRate).isEqualTo(fakeAppLaunchRate)
        assertThat(result.continuousSampleRate).isEqualTo(fakeContinuousRate)
    }

    // endregion
}
