/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.assertj.VitalAppLaunchPropertiesAssert.Companion.assertThat
import com.datadog.android.rum.assertj.VitalEventAssert
import com.datadog.android.rum.assertj.VitalEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumVitalEventHelper
import com.datadog.android.rum.internal.domain.scope.toVitalSchemaType
import com.datadog.android.rum.internal.domain.scope.toVitalStartupType
import com.datadog.android.rum.internal.toVital
import com.datadog.android.rum.internal.utils.buildDDTagsString
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.model.VitalEvent
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.lang.ref.WeakReference
import java.util.stream.Stream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumSessionScopeStartupManagerTest {
    private lateinit var manager: RumSessionScopeStartupManagerImpl

    @Mock
    private lateinit var mockSdkCore: InternalSdkCore

    @Mock
    private lateinit var mockRumAppStartupTelemetryReporter: RumAppStartupTelemetryReporter

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeParentContext: RumContext

    @Forgery
    lateinit var fakeTimeInfo: TimeInfo

    @Mock
    lateinit var mockBatteryInfoProvider: InfoProvider<BatteryInfo>

    @Mock
    lateinit var mockDisplayInfoProvider: InfoProvider<DisplayInfo>

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeBatteryInfo: BatteryInfo

    @Forgery
    lateinit var fakeDisplayInfo: DisplayInfo

    lateinit var fakeParentAttributes: Map<String, Any?>

    private var fakeRumSessionType: RumSessionType? = null

    private var fakeVitalSource: VitalEvent.VitalEventSource? = null

    @FloatForgery(min = 0f, max = 100f)
    private var fakeSampleRate: Float = 0f

    private lateinit var rumVitalEventHelper: RumVitalEventHelper

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeParentAttributes = forge.exhaustiveAttributes()

        fakeRumSessionType = forge.aNullable { aValueFrom(RumSessionType::class.java) }

        whenever(mockBatteryInfoProvider.getState()) doReturn fakeBatteryInfo
        whenever(mockDisplayInfoProvider.getState()) doReturn fakeDisplayInfo

        rumVitalEventHelper = RumVitalEventHelper(
            rumSessionTypeOverride = fakeRumSessionType,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider,
            sampleRate = fakeSampleRate,
            internalLogger = mockInternalLogger
        )

        whenever(mockSdkCore.time) doReturn (fakeTimeInfo)
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        val isValidSource = forge.aBool()

        val fakeSource = if (isValidSource) {
            forge.anElementFrom(
                ViewEvent.ViewEventSource.values().map { it.toJson().asString }
            )
        } else {
            forge.anAlphabeticalString()
        }

        fakeVitalSource = if (isValidSource) {
            VitalEvent.VitalEventSource.fromJson(fakeSource)
        } else {
            null
        }

        fakeDatadogContext = fakeDatadogContext.copy(
            source = fakeSource
        )

        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockWriter.write(eq(mockEventBatchWriter), any(), eq(EventType.DEFAULT))) doReturn true

        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = null,
            syntheticsResultId = null
        )

        manager = RumSessionScopeStartupManagerImpl(
            rumVitalEventHelper = rumVitalEventHelper,
            sdkCore = mockSdkCore,
            rumAppStartupTelemetryReporter = mockRumAppStartupTelemetryReporter
        )
    }

    @Test
    fun `M not cause any interactions W onAppStartEvent`() {
        manager.onAppStartEvent(mock())
        verifyNoInteractions(mockRumAppStartupTelemetryReporter)
    }

    @ParameterizedTest
    @MethodSource("testScenarios")
    fun `M write telemetry and TTID Vital event W onTTIDEvent`(
        scenario: RumStartupScenario,
        forge: Forge
    ) {
        // Given
        val info = RumTTIDInfo(
            scenario = scenario,
            durationNs = forge.aLong(min = 0, max = 10000)
        )

        val event = RumRawEvent.AppStartTTIDEvent(
            info = info
        )

        // When
        manager.onAppStartEvent(mock())

        manager.onTTIDEvent(
            event = event,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = fakeParentContext,
            customAttributes = fakeParentAttributes
        )

        // Then
        verify(mockRumAppStartupTelemetryReporter).reportTTID(info, 0)

        argumentCaptor<VitalEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue).apply {
                hasDate(scenario.initialTime.timestamp + fakeTimeInfo.serverTimeOffsetMs)
                hasApplicationId(fakeParentContext.applicationId)
                containsExactlyContextAttributes(fakeParentAttributes)
                hasStartReason(fakeParentContext.sessionStartReason)
                hasSampleRate(fakeSampleRate)
                hasNoSyntheticsTest()
                hasSessionId(fakeParentContext.sessionId)
                hasSessionType(fakeRumSessionType?.toVital() ?: VitalEvent.VitalEventSessionType.USER)
                hasNoSessionReplay()
                hasNullView()
                hasSource(fakeVitalSource)
                hasAccountInfo(fakeDatadogContext.accountInfo)
                hasUserInfo(fakeDatadogContext.userInfo)
                hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toVitalSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )
                hasConnectivityInfo(fakeDatadogContext.networkInfo)
                hasVersion(fakeDatadogContext.version)
                hasServiceName(fakeDatadogContext.service)
                hasDDTags(buildDDTagsString(fakeDatadogContext))
            }

            val vital = lastValue.vital
            check(vital is VitalEvent.Vital.AppLaunchProperties)

            assertThat(vital).apply {
                hasName(null)
                hasDescription(null)
                hasAppLaunchMetric(VitalEvent.AppLaunchMetric.TTID)
                hasDuration(info.durationNs)
                hasStartupType(scenario.toVitalStartupType())
                hasPrewarmed(null)
                hasSavedInstanceStateBundle(scenario.hasSavedInstanceStateBundle)
            }
        }

        verifyNoMoreInteractions(mockWriter, mockRumAppStartupTelemetryReporter)
    }

    @ParameterizedTest
    @MethodSource("testScenariosPairs")
    fun `M write telemetry twice and TTID Vital event once W onTTIDEvent { called 2 times per session }`(
        scenario1: RumStartupScenario,
        scenario2: RumStartupScenario,
        forge: Forge
    ) {
        // Given
        val info1 = RumTTIDInfo(
            scenario = scenario1,
            durationNs = forge.aLong(min = 0, max = 10000)
        )

        val event1 = RumRawEvent.AppStartTTIDEvent(
            info = info1
        )

        val info2 = RumTTIDInfo(
            scenario = scenario2,
            durationNs = forge.aLong(min = 0, max = 10000)
        )

        val event2 = RumRawEvent.AppStartTTIDEvent(
            info = info2
        )

        // When
        manager.onAppStartEvent(mock())

        manager.onTTIDEvent(
            event = event1,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = fakeParentContext,
            customAttributes = fakeParentAttributes
        )

        manager.onAppStartEvent(mock())

        manager.onTTIDEvent(
            event = event2,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = fakeParentContext,
            customAttributes = fakeParentAttributes
        )

        // Then
        inOrder(mockWriter, mockRumAppStartupTelemetryReporter) {
            verify(mockRumAppStartupTelemetryReporter).reportTTID(eq(info1), eq(0))
            verify(mockWriter).write(eq(mockEventBatchWriter), any(), eq(EventType.DEFAULT))
            verify(mockRumAppStartupTelemetryReporter).reportTTID(eq(info2), eq(1))
            verifyNoMoreInteractions()
        }
    }

    @ParameterizedTest
    @MethodSource("testScenarios")
    fun `M write TTFD Vital event W onTTFDEvent`(
        scenario: RumStartupScenario
    ) {
        // Given
        val ttfdEvent = RumRawEvent.AppStartTTFDEvent()

        // When
        manager.onAppStartEvent(RumRawEvent.AppStartEvent(scenario = scenario))

        manager.onTTFDEvent(
            event = ttfdEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = fakeParentContext,
            customAttributes = fakeParentAttributes
        )

        // Then
        argumentCaptor<VitalEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            VitalEventAssert.assertThat(lastValue).apply {
                hasDate(scenario.initialTime.timestamp + fakeTimeInfo.serverTimeOffsetMs)
                hasApplicationId(fakeParentContext.applicationId)
                containsExactlyContextAttributes(fakeParentAttributes)
                hasStartReason(fakeParentContext.sessionStartReason)
                hasSampleRate(fakeSampleRate)
                hasNoSyntheticsTest()
                hasSessionId(fakeParentContext.sessionId)
                hasSessionType(fakeRumSessionType?.toVital() ?: VitalEvent.VitalEventSessionType.USER)
                hasNoSessionReplay()
                hasNullView()
                hasSource(fakeVitalSource)
                hasAccountInfo(fakeDatadogContext.accountInfo)
                hasUserInfo(fakeDatadogContext.userInfo)
                hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toVitalSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )
                hasConnectivityInfo(fakeDatadogContext.networkInfo)
                hasVersion(fakeDatadogContext.version)
                hasServiceName(fakeDatadogContext.service)
                hasDDTags(buildDDTagsString(fakeDatadogContext))
            }

            val vital = lastValue.vital
            check(vital is VitalEvent.Vital.AppLaunchProperties)

            assertThat(vital).apply {
                hasName(null)
                hasDescription(null)
                hasAppLaunchMetric(VitalEvent.AppLaunchMetric.TTFD)
                hasDuration(ttfdEvent.eventTime.nanoTime - scenario.initialTime.nanoTime)
                hasStartupType(scenario.toVitalStartupType())
                hasPrewarmed(null)
                hasSavedInstanceStateBundle(scenario.hasSavedInstanceStateBundle)
            }
        }

        verifyNoMoreInteractions(mockWriter)
        verifyNoInteractions(mockRumAppStartupTelemetryReporter)
    }

    @ParameterizedTest
    @MethodSource("testScenariosPairs")
    fun `M write TTFD Vital event only once W onTTFDEvent { called 2 times per session }`(
        scenario1: RumStartupScenario,
        scenario2: RumStartupScenario
    ) {
        // Given
        val appStartEvent1 = RumRawEvent.AppStartEvent(scenario = scenario1)
        val appStartEvent2 = RumRawEvent.AppStartEvent(scenario = scenario2)
        val ttfdEvent1 = RumRawEvent.AppStartTTFDEvent()
        val ttfdEvent2 = RumRawEvent.AppStartTTFDEvent()

        // When
        manager.onAppStartEvent(appStartEvent1)

        manager.onTTFDEvent(
            event = ttfdEvent1,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = fakeParentContext,
            customAttributes = fakeParentAttributes
        )

        manager.onAppStartEvent(appStartEvent2)

        manager.onTTFDEvent(
            event = ttfdEvent2,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = fakeParentContext,
            customAttributes = fakeParentAttributes
        )

        // Then
        verify(mockWriter).write(eq(mockEventBatchWriter), any(), eq(EventType.DEFAULT))
        verifyNoMoreInteractions(mockWriter)
    }

    @ParameterizedTest
    @MethodSource("testScenarios")
    fun `M log W onTTFDEvent { if called before onAppStartEvent}`() {
        manager.onTTFDEvent(
            event = RumRawEvent.AppStartTTFDEvent(),
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = fakeParentContext,
            customAttributes = fakeParentAttributes
        )
        verifyNoInteractions(mockWriter)

        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.USER,
            message = RumSessionScopeStartupManagerImpl.REPORT_APP_FULLY_DISPLAYED_CALLED_TOO_EARLY_MESSAGE,
            throwable = null,
            onlyOnce = false,
            additionalProperties = null
        )
        verifyNoMoreInteractions(mockInternalLogger)
    }

    companion object {
        @JvmStatic
        fun testScenarios(): List<RumStartupScenario> {
            val forge = Forge().apply {
                Configurator().configure(this)
            }

            val weakActivity = WeakReference(mock<Activity>())

            return forge.testRumStartupScenarios(weakActivity)
        }

        @JvmStatic
        fun testScenariosPairs(): Stream<Arguments> {
            val forge = Forge().apply {
                Configurator().configure(this)
            }

            val weakActivity = WeakReference(mock<Activity>())

            val scenarios = forge.testRumStartupScenarios(weakActivity)

            return scenarios
                .flatMap { scenario1 ->
                    scenarios.map { scenario2 ->
                        Arguments.of(scenario1, scenario2)
                    }
                }
                .stream()
        }
    }
}
