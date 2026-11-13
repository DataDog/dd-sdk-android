/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.network.rickandmorty.fallback

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.rum.RumMonitor
import com.datadog.android.utils.verifyLog
import dagger.Lazy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class RumRickAndMortyFallbackListenerTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    private lateinit var testedListener: RumRickAndMortyFallbackListener

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        testedListener = RumRickAndMortyFallbackListener(Lazy { mockSdkCore }) { sdkCore ->
            check(sdkCore === mockSdkCore) { "Unexpected SDK core" }
            mockRumMonitor
        }
    }

    @Test
    fun `M add RUM attribute W onFallbackActivated()`() {
        // When
        testedListener.onFallbackActivated()

        // Then
        verify(mockRumMonitor).addAttribute("benchmark.rick_and_morty_fallback", true)
    }

    @Test
    fun `M log warning once W onFallbackActivated()`() {
        // When
        testedListener.onFallbackActivated()

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumRickAndMortyFallbackListener.FALLBACK_ACTIVATED_MESSAGE,
            onlyOnce = true
        )
    }

    @Test
    fun `M log warning with throwable W onFallbackFailed()`() {
        // Given
        val fakeError = IOException("local")

        // When
        testedListener.onFallbackFailed(fakeError)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumRickAndMortyFallbackListener.FALLBACK_FAILED_MESSAGE,
            throwable = fakeError,
            onlyOnce = true
        )
    }
}
