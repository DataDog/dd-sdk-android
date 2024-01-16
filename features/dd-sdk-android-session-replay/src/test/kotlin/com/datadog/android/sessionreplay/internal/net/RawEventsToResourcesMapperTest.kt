/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.SessionReplayResource
import com.datadog.android.utils.verifyLog
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
import java.io.ObjectInput
import java.io.StreamCorruptedException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class RawEventsToResourcesMapperTest {
    private lateinit var testedMapper: RawEventsToResourcesMapper

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockObjectInput: ObjectInput

    @BeforeEach
    fun `set up`() {
        testedMapper = RawEventsToResourcesMapper(mockInternalLogger)
    }

    @Test
    fun `M throw exception W map() { invalid data }`() {
        // Given
       whenever(mockObjectInput.readObject()).thenThrow(StreamCorruptedException::class.java)

        // When
        testedMapper.map(mockObjectInput)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.TELEMETRY,
            RawEventsToResourcesMapper.UNABLE_TO_DESERIALIZE_RESOURCE_ERROR,
            StreamCorruptedException::class.java
        )
    }

    @Test
    fun `M return sessionReplayResource W map()`(
        @Forgery fakeSessionReplayResource: SessionReplayResource
    ) {
        // Given
        whenever(mockObjectInput.readObject()).thenReturn(fakeSessionReplayResource)

        // When
        val createdResource = testedMapper.map(mockObjectInput)

        // Then
        assertThat(createdResource).isEqualTo(fakeSessionReplayResource)
    }
}
