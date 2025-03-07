/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(value = ForgeConfigurator::class)
internal class SessionReplayInternalProxyTest {

    private lateinit var testedBuilder: SessionReplayConfiguration.Builder

    private lateinit var testedProxy: _SessionReplayInternalProxy

    @Mock
    lateinit var mockInternalCallback: SessionReplayInternalCallback

    @FloatForgery
    var fakeSampleRate: Float = 0f

    @Test
    fun `M return the same builder W setInternalCallback`() {
        // Given
        testedBuilder = SessionReplayConfiguration.Builder(fakeSampleRate)
        testedProxy = _SessionReplayInternalProxy(testedBuilder)

        // When
        val result = testedProxy.setInternalCallback(mockInternalCallback)
        val sessionReplayConfiguration = result.build()

        // Then
        assertThat(result).isEqualTo(testedBuilder)
        assertThat(sessionReplayConfiguration.internalCallback).isEqualTo(mockInternalCallback)
    }
}
