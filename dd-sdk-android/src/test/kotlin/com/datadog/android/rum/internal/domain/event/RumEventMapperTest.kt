/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import android.util.Log
import com.datadog.android.event.EventMapper
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockSdkLogHandler
import com.datadog.android.utils.restoreSdkLogHandler
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
@ForgeConfiguration(Configurator::class)
internal class RumEventMapperTest {
    lateinit var testedRumEventMapper: RumEventMapper

    @Mock
    lateinit var mockResourceEventMapper: EventMapper<ResourceEvent>

    @Mock
    lateinit var mockActionEventMapper: EventMapper<ActionEvent>

    @Mock
    lateinit var mockErrorEventMapper: EventMapper<ErrorEvent>

    @Mock
    lateinit var mockViewEventMapper: EventMapper<ViewEvent>

    @Mock
    lateinit var mockLogHandler: LogHandler

    lateinit var originalLogHandler: LogHandler

    @BeforeEach
    fun `set up`() {
        originalLogHandler = mockSdkLogHandler(mockLogHandler)
        testedRumEventMapper = RumEventMapper(
            actionEventMapper = mockActionEventMapper,
            viewEventMapper = mockViewEventMapper,
            resourceEventMapper = mockResourceEventMapper,
            errorEventMapper = mockErrorEventMapper
        )
    }

    @AfterEach
    fun `tear down`() {
        restoreSdkLogHandler(originalLogHandler)
    }

    @Test
    fun `M map the bundled event W map { ViewEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ViewEvent>()
        val fakeRumEvent: RumEvent =
            forge.getForgery<RumEvent>().copy(event = fakeBundledEvent)
        whenever(mockViewEventMapper.map(fakeBundledEvent)).thenReturn(fakeBundledEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull()
        assertThat(mappedRumEvent?.event).isEqualTo(fakeBundledEvent)
    }

    @Test
    fun `M map the bundled event W map { ResourceEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ResourceEvent>()
        val fakeRumEvent: RumEvent =
            forge.getForgery<RumEvent>().copy(event = fakeBundledEvent)
        whenever(mockResourceEventMapper.map(fakeBundledEvent)).thenReturn(fakeBundledEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull()
        assertThat(mappedRumEvent?.event).isEqualTo(fakeBundledEvent)
    }

    @Test
    fun `M map the bundled event W map { ErrorEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ErrorEvent>()
        val fakeRumEvent: RumEvent =
            forge.getForgery<RumEvent>().copy(event = fakeBundledEvent)
        whenever(mockErrorEventMapper.map(fakeBundledEvent)).thenReturn(fakeBundledEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull()
        assertThat(mappedRumEvent?.event).isEqualTo(fakeBundledEvent)
    }

    @Test
    fun `M map the bundled event W map { ActionEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ActionEvent>()
        val fakeRumEvent: RumEvent =
            forge.getForgery<RumEvent>().copy(event = fakeBundledEvent)
        whenever(mockActionEventMapper.map(fakeBundledEvent)).thenReturn(fakeBundledEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull()
        assertThat(mappedRumEvent?.event).isEqualTo(fakeBundledEvent)
    }

    @Test
    fun `M return the original event W map { no internal mapper used }`(forge: Forge) {
        // GIVEN
        testedRumEventMapper = RumEventMapper()
        val fakeRumEvent: RumEvent = forge.getForgery()
        val bundledEvent = fakeRumEvent.event

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull()
        assertThat(mappedRumEvent?.event).isEqualTo(bundledEvent)
    }

    @Test
    fun `M return the original event W map { bundled event unknown }`(forge: Forge) {
        // GIVEN
        testedRumEventMapper = RumEventMapper()
        val fakeRumEvent: RumEvent = forge.getForgery()
        val fakeRumEventCopy = fakeRumEvent.copy(event = Any())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEventCopy)

        // THEN
        verify(mockLogHandler).handleLog(
            Log.WARN,
            "RumEventMapper: there was no EventMapper assigned for" +
                " RUM event type: [${fakeRumEventCopy.event.javaClass.simpleName}]"
        )
        assertThat(mappedRumEvent).isEqualTo(fakeRumEventCopy)
    }

    @Test
    fun `M return null event W map { internal mapper returns NULL }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent: RumEvent = forge.getForgery()

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockLogHandler).handleLog(
            Log.INFO,
            "RumEventMapper: the returned mapped object was null." +
                "This event will be dropped: [$fakeRumEvent]"
        )
    }

    @Test
    fun `M return null event W map returns different object { ViewEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ViewEvent>()
        val fakeRumEvent: RumEvent =
            forge.getForgery<RumEvent>().copy(event = fakeBundledEvent)
        whenever(mockViewEventMapper.map(fakeBundledEvent))
            .thenReturn(forge.getForgery())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockLogHandler).handleLog(
            Log.WARN,
            "RumEventMapper: the returned mapped object was not the " +
                "same instance as the original object. This event will be dropped: [$fakeRumEvent]"
        )
    }

    @Test
    fun `M return null event W map returns different object { ResourceEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ResourceEvent>()
        val fakeRumEvent: RumEvent =
            forge.getForgery<RumEvent>().copy(event = fakeBundledEvent)
        whenever(mockResourceEventMapper.map(fakeBundledEvent))
            .thenReturn(forge.getForgery())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockLogHandler).handleLog(
            Log.WARN,
            "RumEventMapper: the returned mapped object was not the " +
                "same instance as the original object. This event will be dropped: [$fakeRumEvent]"
        )
    }

    @Test
    fun `M return null event W map returns different object { ErrorEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ErrorEvent>()
        val fakeRumEvent: RumEvent =
            forge.getForgery<RumEvent>().copy(event = fakeBundledEvent)
        whenever(mockErrorEventMapper.map(fakeBundledEvent))
            .thenReturn(forge.getForgery())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockLogHandler).handleLog(
            Log.WARN,
            "RumEventMapper: the returned mapped object was not the " +
                "same instance as the original object. This event will be dropped: [$fakeRumEvent]"
        )
    }

    @Test
    fun `M return null event W map returns different object { ActionEvent }`(forge: Forge) {
        // GIVEN
        val fakeBundledEvent = forge.getForgery<ActionEvent>()
        val fakeRumEvent: RumEvent =
            forge.getForgery<RumEvent>().copy(event = fakeBundledEvent)
        whenever(mockActionEventMapper.map(fakeBundledEvent))
            .thenReturn(forge.getForgery())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockLogHandler).handleLog(
            Log.WARN,
            "RumEventMapper: the returned mapped object was not the " +
                "same instance as the original object. This event will be dropped: [$fakeRumEvent]"
        )
    }
}
