/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.api.InternalLogger
import com.datadog.android.event.EventMapper
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.model.VitalEvent
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.forge.aRumEvent
import com.datadog.android.rum.utils.verifyLog
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.telemetry.model.TelemetryUsageEvent
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
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale

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
    lateinit var mockLongTaskEventMapper: EventMapper<LongTaskEvent>

    @Mock
    lateinit var mockVitalEventMapper: EventMapper<VitalEvent>

    @Mock
    lateinit var mockTelemetryConfigurationMapper: EventMapper<TelemetryConfigurationEvent>

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        whenever(mockViewEventMapper.map(any())).thenAnswer { it.arguments[0] }

        testedRumEventMapper = RumEventMapper(
            actionEventMapper = mockActionEventMapper,
            viewEventMapper = mockViewEventMapper,
            resourceEventMapper = mockResourceEventMapper,
            errorEventMapper = mockErrorEventMapper,
            longTaskEventMapper = mockLongTaskEventMapper,
            vitalEventMapper = mockVitalEventMapper,
            telemetryConfigurationMapper = mockTelemetryConfigurationMapper,
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `M map the bundled event W map { ViewEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<ViewEvent>()
        whenever(mockViewEventMapper.map(fakeRumEvent)).thenReturn(fakeRumEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull
        assertThat(mappedRumEvent).isEqualTo(fakeRumEvent)
    }

    @Test
    fun `M map the bundled event W map { ResourceEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<ResourceEvent>()
        whenever(mockResourceEventMapper.map(fakeRumEvent)).thenReturn(fakeRumEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull
        assertThat(mappedRumEvent).isEqualTo(fakeRumEvent)
    }

    @Test
    fun `M map the bundled event W map { ErrorEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<ErrorEvent>()
        whenever(mockErrorEventMapper.map(fakeRumEvent)).thenReturn(fakeRumEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull
        assertThat(mappedRumEvent).isEqualTo(fakeRumEvent)
    }

    @Test
    fun `M map the bundled event W map { fatal ErrorEvent }`(forge: Forge) {
        // GIVEN
        val fakeErrorEvent = forge.getForgery<ErrorEvent>()
        val fakeCrashEvent = fakeErrorEvent.copy(
            error = fakeErrorEvent.error.copy(isCrash = true)
        )
        whenever(mockErrorEventMapper.map(fakeCrashEvent)).thenReturn(fakeCrashEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeCrashEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull
        assertThat(mappedRumEvent).isEqualTo(fakeCrashEvent)
    }

    @Test
    fun `M map the bundled event W map { ActionEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<ActionEvent>()
        whenever(mockActionEventMapper.map(fakeRumEvent)).thenReturn(fakeRumEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull
        assertThat(mappedRumEvent).isEqualTo(fakeRumEvent)
    }

    @Test
    fun `M map the bundled event W map { LongTaskEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<LongTaskEvent>()
        whenever(mockLongTaskEventMapper.map(fakeRumEvent)).thenReturn(fakeRumEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull
        assertThat(mappedRumEvent).isEqualTo(fakeRumEvent)
    }

    @Test
    fun `M map the bundled event W map { VitalEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<VitalEvent>()
        whenever(mockVitalEventMapper.map(fakeRumEvent)).thenReturn(fakeRumEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull
        assertThat(mappedRumEvent).isEqualTo(fakeRumEvent)
    }

    @Test
    fun `M return the original event W map { no internal mapper used }`(forge: Forge) {
        // GIVEN
        testedRumEventMapper = RumEventMapper(
            internalLogger = mockInternalLogger
        )
        val fakeRumEvent = forge.aRumEvent()

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNotNull
        assertThat(mappedRumEvent).isEqualTo(fakeRumEvent)
    }

    @Test
    fun `M return the original event W map { bundled event unknown }`() {
        // GIVEN
        testedRumEventMapper = RumEventMapper(
            internalLogger = mockInternalLogger
        )
        val fakeRumEvent = Any()

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            RumEventMapper.NO_EVENT_MAPPER_ASSIGNED_WARNING_MESSAGE
                .format(Locale.US, fakeRumEvent.javaClass.simpleName)
        )
        assertThat(mappedRumEvent).isEqualTo(fakeRumEvent)
    }

    @Test
    fun `M return the original event W map { TelemetryUsageEvent }`(
        @Forgery telemetryUsageEvent: TelemetryUsageEvent
    ) {
        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(telemetryUsageEvent)

        // THEN
        verifyNoInteractions(mockInternalLogger)
        assertThat(mappedRumEvent).isSameAs(telemetryUsageEvent)
    }

    @Test
    fun `M return the original event W map { TelemetryDebugEvent }`(
        @Forgery telemetryDebugEvent: TelemetryDebugEvent
    ) {
        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(telemetryDebugEvent)

        // THEN
        verifyNoInteractions(mockInternalLogger)
        assertThat(mappedRumEvent).isSameAs(telemetryDebugEvent)
    }

    @Test
    fun `M return the original event W map { TelemetryErrorEvent }`(
        @Forgery telemetryErrorEvent: TelemetryErrorEvent
    ) {
        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(telemetryErrorEvent)

        // THEN
        verifyNoInteractions(mockInternalLogger)
        assertThat(mappedRumEvent).isSameAs(telemetryErrorEvent)
    }

    @Test
    fun `M return the bundled event W map { TelemetryConfigurationEvent }`(
        @Forgery telemetryConfigurationEvent: TelemetryConfigurationEvent
    ) {
        // GIVEN
        whenever(mockTelemetryConfigurationMapper.map(telemetryConfigurationEvent))
            .thenReturn(telemetryConfigurationEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(telemetryConfigurationEvent)

        // THEN
        verifyNoInteractions(mockInternalLogger)
        verify(mockTelemetryConfigurationMapper).map(telemetryConfigurationEvent)
        assertThat(mappedRumEvent).isSameAs(telemetryConfigurationEvent)
    }

    @Test
    fun `M use the original event W map returns null object { ViewEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<ViewEvent>()
        whenever(mockViewEventMapper.map(fakeRumEvent))
            .thenReturn(null)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isEqualTo(fakeRumEvent)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            RumEventMapper.VIEW_EVENT_NULL_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)
        )
    }

    @Test
    fun `M return null event W map returns null object { ResourceEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<ResourceEvent>()
        whenever(mockResourceEventMapper.map(fakeRumEvent))
            .thenReturn(null)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            RumEventMapper.EVENT_NULL_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `M return null event W map returns null object { ErrorEvent }`(forge: Forge) {
        // GIVEN
        val fakeErrorEvent = forge.getForgery<ErrorEvent>()
        val fakeNoCrashEvent = fakeErrorEvent.copy(
            error = fakeErrorEvent.error.copy(isCrash = false)
        )
        whenever(mockErrorEventMapper.map(fakeNoCrashEvent))
            .thenReturn(null)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeNoCrashEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            RumEventMapper.EVENT_NULL_WARNING_MESSAGE.format(Locale.US, fakeNoCrashEvent)

        )
    }

    @Test
    fun `M return event W map returns null object { fatal ErrorEvent }`(forge: Forge) {
        // GIVEN
        val fakeErrorEvent = forge.getForgery<ErrorEvent>()
        val fakeCrashEvent = fakeErrorEvent.copy(
            error = fakeErrorEvent.error.copy(isCrash = true)
        )
        whenever(mockErrorEventMapper.map(fakeCrashEvent))
            .thenReturn(null)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeCrashEvent)

        // THEN
        assertThat(mappedRumEvent)
            .isSameAs(fakeCrashEvent)
            .isEqualTo(fakeCrashEvent)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumEventMapper.NO_DROPPING_FATAL_ERRORS_WARNING_MESSAGE
        )
    }

    @Test
    fun `M return null event W map returns null object { ActionEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<ActionEvent>()
        whenever(mockActionEventMapper.map(fakeRumEvent))
            .thenReturn(null)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            RumEventMapper.EVENT_NULL_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)
        )
    }

    @Test
    fun `M return null event W map returns null object { LongTaskEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<LongTaskEvent>()
        whenever(mockLongTaskEventMapper.map(fakeRumEvent))
            .thenReturn(null)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            RumEventMapper.EVENT_NULL_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `M return null event W map returns null object { VitalEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<VitalEvent>()
        whenever(mockVitalEventMapper.map(fakeRumEvent))
            .thenReturn(null)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            RumEventMapper.EVENT_NULL_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `M use the original event W map returns different object { ViewEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<ViewEvent>()
        whenever(mockViewEventMapper.map(fakeRumEvent))
            .thenReturn(forge.getForgery())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isSameAs(fakeRumEvent)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            RumEventMapper.VIEW_EVENT_NULL_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)
        )
    }

    @Test
    fun `M return null event W map returns different object { ResourceEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<ResourceEvent>()
        whenever(mockResourceEventMapper.map(fakeRumEvent))
            .thenReturn(forge.getForgery())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumEventMapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `M return null event W map returns different object { ErrorEvent }`(
        @Forgery fakeRumEvent: ErrorEvent,
        @Forgery fakeMappedRumEvent: ErrorEvent
    ) {
        // GIVEN
        val fakeNoCrashEvent = fakeRumEvent.copy(
            error = fakeRumEvent.error.copy(isCrash = false)
        )
        whenever(mockErrorEventMapper.map(fakeNoCrashEvent))
            .thenReturn(fakeMappedRumEvent)

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeNoCrashEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumEventMapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE
                .format(Locale.US, fakeNoCrashEvent)
        )
    }

    @Test
    fun `M return null event W map returns different object { ActionEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<ActionEvent>()
        whenever(mockActionEventMapper.map(fakeRumEvent))
            .thenReturn(forge.getForgery())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumEventMapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `M return null event W map returns different object { LongTaskEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<LongTaskEvent>()
        whenever(mockLongTaskEventMapper.map(fakeRumEvent))
            .thenReturn(forge.getForgery())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumEventMapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `M return null event W map returns different object { VitalEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<VitalEvent>()
        whenever(mockVitalEventMapper.map(fakeRumEvent))
            .thenReturn(forge.getForgery())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumEventMapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `M use the original event W map returns a copy { ViewEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<ViewEvent>()
        whenever(mockViewEventMapper.map(fakeRumEvent))
            .thenReturn(fakeRumEvent.copy())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isSameAs(fakeRumEvent)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            RumEventMapper.VIEW_EVENT_NULL_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)
        )
    }

    @Test
    fun `M return null event W map returns a copy { ResourceEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<ResourceEvent>()
        whenever(mockResourceEventMapper.map(fakeRumEvent))
            .thenReturn(fakeRumEvent.copy())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumEventMapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `M return null event W map returns a copy { not a crash ErrorEvent }`(
        @Forgery fakeRumEvent: ErrorEvent
    ) {
        // GIVEN
        val fakeNoCrashEvent = fakeRumEvent.copy(
            error = fakeRumEvent.error.copy(isCrash = false)
        )
        whenever(mockErrorEventMapper.map(fakeNoCrashEvent))
            .thenReturn(fakeNoCrashEvent.copy())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeNoCrashEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumEventMapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE
                .format(Locale.US, fakeNoCrashEvent)
        )
    }

    @Test
    fun `M return null event W map returns a copy { ActionEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<ActionEvent>()
        whenever(mockActionEventMapper.map(fakeRumEvent))
            .thenReturn(fakeRumEvent.copy())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumEventMapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `M return null event W map returns a copy { LongTaskEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<LongTaskEvent>()
        whenever(mockLongTaskEventMapper.map(fakeRumEvent))
            .thenReturn(fakeRumEvent.copy())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumEventMapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `M return null event W map returns a copy { VitalEvent }`(forge: Forge) {
        // GIVEN
        val fakeRumEvent = forge.getForgery<VitalEvent>()
        whenever(mockVitalEventMapper.map(fakeRumEvent))
            .thenReturn(fakeRumEvent.copy())

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumEventMapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }
}
