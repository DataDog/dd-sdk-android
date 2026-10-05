/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.remote

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RemoteConfigLifecycleCallbackTest {

    @Mock
    lateinit var mockRemoteConfigService: RemoteConfigService

    private lateinit var testedCallback: RemoteConfigLifecycleCallback

    @BeforeEach
    fun `set up`() {
        testedCallback = RemoteConfigLifecycleCallback(mockRemoteConfigService)
    }

    @Test
    fun `M call syncWithRemote W onStarted() { previously backgrounded }`() {
        // Given
        testedCallback.onStopped()

        // When
        testedCallback.onStarted()

        // Then
        verify(mockRemoteConfigService).syncWithRemote()
    }

    @Test
    fun `M not call syncWithRemote W onStarted() { not previously backgrounded }`() {
        // When — cold start, onStopped never called
        testedCallback.onStarted()

        // Then
        verifyNoInteractions(mockRemoteConfigService)
    }

    @Test
    fun `M do nothing W onResumed()`() {
        // When
        testedCallback.onResumed()

        // Then
        verifyNoInteractions(mockRemoteConfigService)
    }

    @Test
    fun `M do nothing W onStopped()`() {
        // When
        testedCallback.onStopped()

        // Then
        verifyNoInteractions(mockRemoteConfigService)
    }

    @Test
    fun `M do nothing W onPaused()`() {
        // When
        testedCallback.onPaused()

        // Then
        verifyNoInteractions(mockRemoteConfigService)
    }

    @Test
    fun `M call syncWithRemote on each foreground W multiple background-foreground cycles`() {
        // When — simulate two full background/foreground cycles
        testedCallback.onStopped()
        testedCallback.onStarted()

        testedCallback.onStopped()
        testedCallback.onStarted()

        // Then — syncWithRemote called exactly once per foreground return
        verify(mockRemoteConfigService, times(2)).syncWithRemote()
    }
}
