/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.domain.event

import android.util.Log
import com.datadog.android.event.SpanEventMapper
import com.datadog.android.tracing.model.SpanEvent
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
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
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(Configurator::class)
internal class SpanEventMapperWrapperTest {

    lateinit var testedEventMapper: SpanEventMapperWrapper

    @Mock
    lateinit var mockWrappedEventMapper: SpanEventMapper

    @Mock
    lateinit var mockSpanEvent: SpanEvent

    @BeforeEach
    fun `set up`() {
        testedEventMapper = SpanEventMapperWrapper(mockWrappedEventMapper)
    }

    @Test
    fun `M map and return the SpanEvent W map`() {
        // GIVEN
        whenever(mockWrappedEventMapper.map(mockSpanEvent)).thenReturn(mockSpanEvent)

        // WHEN
        val mappedEvent = testedEventMapper.map(mockSpanEvent)

        // THEN
        verify(mockWrappedEventMapper).map(mockSpanEvent)
        assertThat(mappedEvent).isEqualTo(mockSpanEvent)
    }

    @Test
    fun `M return null if the mapped returned event is not the same instance W map`() {
        // GIVEN
        whenever(mockWrappedEventMapper.map(mockSpanEvent)).thenReturn(mock())

        // WHEN
        val mappedEvent = testedEventMapper.map(mockSpanEvent)

        // THEN
        verify(mockWrappedEventMapper).map(mockSpanEvent)
        assertThat(mappedEvent).isNull()
    }

    @Test
    fun `M log a warning if the mapped returned event is not the same instance W map`() {
        // GIVEN
        whenever(mockWrappedEventMapper.map(mockSpanEvent)).thenReturn(mock())

        // WHEN
        testedEventMapper.map(mockSpanEvent)

        // THEN
        verify(logger.mockDevLogHandler).handleLog(
            Log.WARN,
            SpanEventMapperWrapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(
                Locale.US,
                mockSpanEvent.toString()
            )
        )
    }

    companion object {
        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
