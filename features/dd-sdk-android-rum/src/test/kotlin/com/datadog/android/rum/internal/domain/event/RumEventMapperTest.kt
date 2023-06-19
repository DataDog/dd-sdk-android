/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.event.EventMapper
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.StorageEvent
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.forge.aRumEvent
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.v2.api.InternalLogger
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
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
    lateinit var mockTelemetryConfigurationMapper: EventMapper<TelemetryConfigurationEvent>

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        whenever(mockViewEventMapper.map(any())).thenAnswer { it.arguments[0] }

        testedRumEventMapper = RumEventMapper(
            sdkCore = rumMonitor.mockSdkCore,
            actionEventMapper = mockActionEventMapper,
            viewEventMapper = mockViewEventMapper,
            resourceEventMapper = mockResourceEventMapper,
            errorEventMapper = mockErrorEventMapper,
            longTaskEventMapper = mockLongTaskEventMapper,
            telemetryConfigurationMapper = mockTelemetryConfigurationMapper,
            internalLogger = mockInternalLogger
        )
        testedRumEventMapper.sdkCore = rumMonitor.mockSdkCore
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
    fun `M return the original event W map { no internal mapper used }`(forge: Forge) {
        // GIVEN
        testedRumEventMapper = RumEventMapper(
            sdkCore = rumMonitor.mockSdkCore,
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
            sdkCore = rumMonitor.mockSdkCore,
            internalLogger = mockInternalLogger
        )
        val fakeRumEvent = Any()

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        verify(mockInternalLogger).log(
            InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            RumEventMapper.NO_EVENT_MAPPER_ASSIGNED_WARNING_MESSAGE
                .format(Locale.US, fakeRumEvent.javaClass.simpleName)
        )
        assertThat(mappedRumEvent).isEqualTo(fakeRumEvent)
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
        verify(mockInternalLogger).log(
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
        verify(mockInternalLogger).log(
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
        verify(mockInternalLogger).log(
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
        verify(mockInternalLogger).log(
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
        verify(mockInternalLogger).log(
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
        verify(mockInternalLogger).log(
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
        verify(mockInternalLogger).log(
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
        verify(mockInternalLogger).log(
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
        verify(mockInternalLogger).log(
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
        verify(mockInternalLogger).log(
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
        verify(mockInternalLogger).log(
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
        verify(mockInternalLogger).log(
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
        verify(mockInternalLogger).log(
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
        verify(mockInternalLogger).log(
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
        verify(mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumEventMapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, fakeRumEvent)

        )
    }

    @Test
    fun `𝕄 warn the RUM Monitor 𝕎 map() {action dropped}`(
        @Forgery fakeRumEvent: ActionEvent
    ) {
        // Given
        whenever(mockActionEventMapper.map(fakeRumEvent)) doReturn null

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(
                fakeRumEvent.view.id,
                StorageEvent.Action(
                    frustrationCount = fakeRumEvent.action.frustration?.type?.size ?: 0
                )
            )
    }

    @Test
    fun `𝕄 warn the RUM Monitor 𝕎 map() {resource dropped}`(
        @Forgery fakeRumEvent: ResourceEvent
    ) {
        // Given
        whenever(mockResourceEventMapper.map(fakeRumEvent)) doReturn null

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(fakeRumEvent.view.id, StorageEvent.Resource)
    }

    @Test
    fun `𝕄 warn the RUM Monitor 𝕎 map() {error dropped}`(
        @Forgery fakeRumEvent: ErrorEvent
    ) {
        // Given
        val fakeNoCrashEvent = fakeRumEvent.copy(
            error = fakeRumEvent.error.copy(isCrash = false)
        )
        whenever(mockErrorEventMapper.map(fakeNoCrashEvent)) doReturn null

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeNoCrashEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(fakeNoCrashEvent.view.id, StorageEvent.Error)
    }

    @Test
    fun `𝕄 warn the RUM Monitor 𝕎 map() {longTask dropped}`(
        @Forgery longTaskEvent: LongTaskEvent
    ) {
        // Given
        val fakeRumEvent = longTaskEvent.copy(
            longTask = LongTaskEvent.LongTask(
                id = longTaskEvent.longTask.id,
                duration = longTaskEvent.longTask.duration,
                isFrozenFrame = false
            )
        )

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(longTaskEvent.view.id, StorageEvent.LongTask)
    }

    @Test
    fun `𝕄 warn the RUM Monitor 𝕎 map() {frozenFrame dropped}`(
        @Forgery longTaskEvent: LongTaskEvent
    ) {
        // Given
        val fakeRumEvent = longTaskEvent.copy(
            longTask = LongTaskEvent.LongTask(
                id = longTaskEvent.longTask.id,
                duration = longTaskEvent.longTask.duration,
                isFrozenFrame = true
            )
        )

        // WHEN
        val mappedRumEvent = testedRumEventMapper.map(fakeRumEvent)

        // THEN
        assertThat(mappedRumEvent).isNull()
        verify(rumMonitor.mockInstance as AdvancedRumMonitor).eventDropped(
            longTaskEvent.view.id,
            StorageEvent.FrozenFrame
        )
    }

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
