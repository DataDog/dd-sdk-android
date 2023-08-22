/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.internal.utils.TimeProvider
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class RumContextDataHandlerTest {
    @Mock
    lateinit var mockRumContextProvider: RumContextProvider

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Forgery
    lateinit var fakeRumContext: SessionReplayRumContext

    private lateinit var testedHandler: RumContextDataHandler
    private val invalidRumContext = SessionReplayRumContext()

    @BeforeEach
    fun `set up`() {
        whenever(mockRumContextProvider.getRumContext()).thenReturn(fakeRumContext)

        testedHandler = RumContextDataHandler(
            mockRumContextProvider,
            mockTimeProvider
        )
    }

    @Test
    fun `M return RumContextData W createRumContextData { valid rum context }`(forge: Forge) {
        // Given
        val fakeTime = forge.aLong()

        whenever(mockTimeProvider.getDeviceTimestamp()).thenReturn(fakeTime)

        // When
        val rumContextData = testedHandler.createRumContextData()

        // Then
        assertThat(rumContextData?.timestamp).isEqualTo(fakeTime)
        assertThat(rumContextData?.newRumContext).isEqualTo(fakeRumContext)
    }

    @Test
    fun `M update prevRumContext W createRumContextData { valid context }`() {
        // When
        testedHandler.createRumContextData()
        val rumContextData = testedHandler.createRumContextData()

        // Then
        assertThat(rumContextData?.prevRumContext).isEqualTo(fakeRumContext)
    }

    @Test
    fun `M return null W createRumContextData { invalid context }`() {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(invalidRumContext)

        // When
        val rumContextData = testedHandler.createRumContextData()

        // Then
        assertThat(rumContextData).isNull()
    }

    @Test
    fun `M not update prevRumContext W createRumContextData { invalid context }`() {
        // Given

        // overwrite prevRumContext with a valid context
        testedHandler.createRumContextData()

        whenever(mockRumContextProvider.getRumContext()).thenReturn(invalidRumContext)

        // pass an invalid context - prevRumContext should not be overwritten
        testedHandler.createRumContextData()

        whenever(mockRumContextProvider.getRumContext()).thenReturn(fakeRumContext)

        // When
        val rumContextData = testedHandler.createRumContextData()

        // Then
        assertThat(rumContextData?.prevRumContext).isEqualTo(fakeRumContext)
    }
}
