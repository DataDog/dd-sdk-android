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
    fun `M map the bundled event W map { internal mapper returns NOT NULL }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent: RumEvent = forge.getForgery()
        val bundledEvent = fakeRumEvent.event
        when (bundledEvent) {
            is ViewEvent -> whenever(mockViewEventMapper.map(bundledEvent)).thenReturn(bundledEvent)
            is ErrorEvent -> whenever(mockErrorEventMapper.map(bundledEvent)).thenReturn(
                bundledEvent
            )
            is ActionEvent -> whenever(mockActionEventMapper.map(bundledEvent)).thenReturn(
                bundledEvent
            )
            is ResourceEvent -> whenever(mockResourceEventMapper.map(bundledEvent)).thenReturn(
                bundledEvent
            )
        }

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull()
        assertThat(mappedRumEvent?.event).isEqualTo(bundledEvent)
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
            Log.WARN,
            "RumEventMapper: either the returned mapped " +
                "object was null or was not the same instance as the original object." +
                " This event will be dropped: [$fakeRumEvent]"
        )
    }

    @Test
    fun `M return null event W map { internal mapper returns a different object }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent: RumEvent = forge.getForgery()
        when (val bundledEvent = fakeRumEvent.event) {
            is ViewEvent ->
                whenever(mockViewEventMapper.map(bundledEvent))
                    .thenReturn(forge.getForgery())
            is ErrorEvent ->
                whenever(mockErrorEventMapper.map(bundledEvent))
                    .thenReturn(forge.getForgery())
            is ActionEvent ->
                whenever(mockActionEventMapper.map(bundledEvent))
                    .thenReturn(forge.getForgery())
            is ResourceEvent ->
                whenever(mockResourceEventMapper.map(bundledEvent))
                    .thenReturn(forge.getForgery())
        }

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(mockLogHandler).handleLog(
            Log.WARN,
            "RumEventMapper: either the returned mapped " +
                "object was null or was not the same instance as the original object." +
                " This event will be dropped: [$fakeRumEvent]"
        )
    }
}
