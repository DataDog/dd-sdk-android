/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.event

import com.datadog.android.core.persistence.Serializer
import com.datadog.android.event.EventMapper
import com.datadog.android.trace.model.SpanEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.opentracing.DDSpan
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SpanMapperSerializerTest {
    lateinit var testedSerializer: SpanMapperSerializer

    @Mock
    lateinit var mockSpanEventMapper: Mapper<DDSpan, SpanEvent>

    @Mock
    lateinit var mockExposedEventMapper: EventMapper<SpanEvent>

    @Mock
    lateinit var mockSerializer: Serializer<SpanEvent>

    @Forgery
    lateinit var fakeDdSpan: DDSpan

    @Mock
    lateinit var mockSpanEvent: SpanEvent

    @StringForgery
    lateinit var fakeSerializedSpanEvent: String

    @BeforeEach
    fun `set up`() {
        whenever(mockSpanEventMapper.map(fakeDdSpan)).thenReturn(mockSpanEvent)
        whenever(mockSerializer.serialize(mockSpanEvent)).thenReturn(fakeSerializedSpanEvent)
        testedSerializer = SpanMapperSerializer(
            mockSpanEventMapper,
            mockExposedEventMapper,
            mockSerializer
        )
    }

    @Test
    fun `M return the serialized equivalent SpanEvent W serialize`() {
        // GIVEN
        whenever(mockExposedEventMapper.map(mockSpanEvent)).thenReturn(mockSpanEvent)

        // WHEN
        val serializedEvent = testedSerializer.serialize(fakeDdSpan)

        // THEN
        assertThat(serializedEvent).isEqualTo(fakeSerializedSpanEvent)
    }

    @Test
    fun `M return null W serialize { event dropped from exposedEventMapper }`() {
        assertThat(testedSerializer.serialize(fakeDdSpan)).isNull()
    }
}
