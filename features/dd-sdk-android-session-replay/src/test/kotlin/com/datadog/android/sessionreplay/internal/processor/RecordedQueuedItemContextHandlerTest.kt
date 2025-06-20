/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.internal.utils.TimeProvider
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class RecordedQueuedItemContextHandlerTest {
    @Mock
    lateinit var mockRumContextProvider: RumContextProvider

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Forgery
    lateinit var fakeRumContext: SessionReplayRumContext

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedHandler: RumContextDataHandler
    private val invalidRumContext = SessionReplayRumContext()

    @BeforeEach
    fun `set up`() {
        whenever(mockRumContextProvider.getRumContext()).thenReturn(fakeRumContext)

        testedHandler = RumContextDataHandler(
            mockRumContextProvider,
            mockTimeProvider,
            mockInternalLogger
        )
    }

    @Test
    fun `M return RumContextData W createRumContextData { valid rum context }`(
        @LongForgery(min = 1) fakeTime: Long
    ) {
        whenever(mockTimeProvider.getDeviceTimestamp()).thenReturn(fakeTime)

        // When
        val rumContextData = testedHandler.createRumContextData()

        // Then
        assertThat(rumContextData?.timestamp).isEqualTo(fakeTime + fakeRumContext.viewTimeOffsetMs)
        assertThat(rumContextData?.newRumContext).isEqualTo(fakeRumContext)
    }

    @Test
    fun `M return null W createRumContextData { invalid context }`() {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(invalidRumContext)
        val expectedLogMessage = RumContextDataHandler.INVALID_RUM_CONTEXT_ERROR_MESSAGE_FORMAT
            .format(Locale.ENGLISH, invalidRumContext.toString())

        // When
        val rumContextData = testedHandler.createRumContextData()

        // Then
        assertThat(rumContextData).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            expectedLogMessage
        )
    }
}
