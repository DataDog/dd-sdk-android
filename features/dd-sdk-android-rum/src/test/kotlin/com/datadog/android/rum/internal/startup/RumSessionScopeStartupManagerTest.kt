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
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.assertj.VitalAppLaunchEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.VitalAppLaunchPropertiesAssert.Companion.assertThat
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumScopeKey
import com.datadog.android.rum.internal.domain.scope.RumVitalAppLaunchEventHelper
import com.datadog.android.rum.internal.domain.scope.toVitalAppLaunchSchemaType
import com.datadog.android.rum.internal.domain.scope.toVitalAppLaunchStartupType
import com.datadog.android.rum.internal.toVitalAppLaunch
import com.datadog.android.rum.internal.utils.buildDDTagsString
import com.datadog.android.rum.model.RumVitalAppLaunchEvent
import com.datadog.android.rum.model.ViewEvent
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
import org.mockito.kotlin.times
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
    lateinit var rumContext: RumContext

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

    private var fakeVitalSource: RumVitalAppLaunchEvent.RumVitalAppLaunchEventSource? = null

    @FloatForgery(min = 0f, max = 100f)
    private var fakeSampleRate: Float = 0f

    private lateinit var rumVitalAppLaunchEventHelper: RumVitalAppLaunchEventHelper

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeParentAttributes = forge.exhaustiveAttributes()

        fakeRumSessionType = forge.aNullable { aValueFrom(RumSessionType::class.java) }

        whenever(mockBatteryInfoProvider.getState()) doReturn fakeBatteryInfo
        whenever(mockDisplayInfoProvider.getState()) doReturn fakeDisplayInfo

        rumVitalAppLaunchEventHelper = RumVitalAppLaunchEventHelper(
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
            RumVitalAppLaunchEvent.RumVitalAppLaunchEventSource.fromJson(fakeSource)
        } else {
            null
        }

        fakeDatadogContext = fakeDatadogContext.copy(
            source = fakeSource,
            featuresContext = fakeDatadogContext.featuresContext.let {
                if (forge.aBool()) {
                    it.toMutableMap().apply {
                        put(Feature.PROFILING_FEATURE_NAME, mapOf("profiler_is_running" to true))
                    }
                } else {
                    it
                }
            }
        )

        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockWriter.write(eq(mockEventBatchWriter), any(), eq(EventType.DEFAULT))) doReturn true

        val rumScopeKey: RumScopeKey? = forge.aNullable { getForgery() }

        rumContext = rumContext.copy(
            syntheticsTestId = null,
            syntheticsResultId = null,
            viewId = rumScopeKey?.id,
            viewName = rumScopeKey?.name,
            viewUrl = rumScopeKey?.url
        )

        manager = RumSessionScopeStartupManagerImpl(
            rumVitalAppLaunchEventHelper = rumVitalAppLaunchEventHelper,
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
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )

        // Then
        verify(mockRumAppStartupTelemetryReporter).reportTTID(info, 0)

        argumentCaptor<RumVitalAppLaunchEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            verifyTTID(value = lastValue, info = info)
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
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )

        manager.onAppStartEvent(mock())

        manager.onTTIDEvent(
            event = event2,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )

        // Then
        inOrder(mockWriter, mockRumAppStartupTelemetryReporter) {
            verify(mockRumAppStartupTelemetryReporter).reportTTID(eq(info1), eq(0))
            argumentCaptor<RumVitalAppLaunchEvent> {
                verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
                verifyTTID(value = lastValue, info = info1)
            }
            verify(mockRumAppStartupTelemetryReporter).reportTTID(eq(info2), eq(1))
            verifyNoMoreInteractions()
        }
    }

    @ParameterizedTest
    @MethodSource("testScenarios")
    fun `M write TTFD Vital event W onTTFDEvent`(
        scenario: RumStartupScenario,
        forge: Forge
    ) {
        // Given
        val info = RumTTIDInfo(
            scenario = scenario,
            durationNs = forge.aLong(min = 0, max = 10000)
        )

        val ttidEvent = RumRawEvent.AppStartTTIDEvent(info = info)

        val ttfdEvent = RumRawEvent.AppStartTTFDEvent()

        // When
        manager.onAppStartEvent(RumRawEvent.AppStartEvent(scenario = scenario))

        manager.onTTIDEvent(
            event = ttidEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )

        manager.onTTFDEvent(
            event = ttfdEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )

        // Then
        argumentCaptor<RumVitalAppLaunchEvent> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            verifyTTID(value = firstValue, info = info)
            verifyTTFD(
                value = lastValue,
                scenario = scenario,
                durationNs = ttfdEvent.eventTime.nanoTime - scenario.initialTime.nanoTime
            )
        }

        verifyNoMoreInteractions(mockWriter)
    }

    @ParameterizedTest
    @MethodSource("testScenariosPairs")
    fun `M write TTFD Vital event only once W onTTFDEvent { called 2 times per session }`(
        scenario1: RumStartupScenario,
        scenario2: RumStartupScenario,
        forge: Forge
    ) {
        // Given
        val appStartEvent1 = RumRawEvent.AppStartEvent(scenario = scenario1)
        val appStartEvent2 = RumRawEvent.AppStartEvent(scenario = scenario2)

        val info1 = RumTTIDInfo(
            scenario = scenario1,
            durationNs = forge.aLong(min = 0, max = 10000)
        )

        val info2 = RumTTIDInfo(
            scenario = scenario2,
            durationNs = forge.aLong(min = 0, max = 10000)
        )

        val ttidEvent1 = RumRawEvent.AppStartTTIDEvent(info = info1)
        val ttidEvent2 = RumRawEvent.AppStartTTIDEvent(info = info2)

        val ttfdEvent1 = RumRawEvent.AppStartTTFDEvent()
        val ttfdEvent2 = RumRawEvent.AppStartTTFDEvent()

        // When
        manager.onAppStartEvent(appStartEvent1)

        manager.onTTIDEvent(
            event = ttidEvent1,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )

        manager.onTTFDEvent(
            event = ttfdEvent1,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )

        manager.onAppStartEvent(appStartEvent2)

        manager.onTTIDEvent(
            event = ttidEvent2,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )

        manager.onTTFDEvent(
            event = ttfdEvent2,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )

        // Then
        inOrder(mockWriter) {
            argumentCaptor<RumVitalAppLaunchEvent> {
                verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
                verifyTTID(value = firstValue, info = info1)
                verifyTTFD(
                    value = secondValue,
                    scenario = scenario1,
                    durationNs = ttfdEvent1.eventTime.nanoTime - scenario1.initialTime.nanoTime
                )
            }
            verifyNoMoreInteractions()
        }
    }

    @ParameterizedTest
    @MethodSource("testScenarios")
    fun `M log W onTTFDEvent { if called before onAppStartEvent }`() {
        // When
        manager.onTTFDEvent(
            event = RumRawEvent.AppStartTTFDEvent(),
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )

        // Then
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

    @ParameterizedTest
    @MethodSource("testScenarios")
    fun `M log W onTTFDEvent { if called before onTTIDEvent }`(
        scenario: RumStartupScenario
    ) {
        // When
        manager.onAppStartEvent(RumRawEvent.AppStartEvent(scenario = scenario))

        manager.onTTFDEvent(
            event = RumRawEvent.AppStartTTFDEvent(),
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )

        // Then
        verifyNoInteractions(mockWriter)

        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.USER,
            message = RumSessionScopeStartupManagerImpl.REPORT_APP_FULLY_DISPLAYED_CALLED_BEFORE_TTID_MESSAGE,
            throwable = null,
            onlyOnce = false,
            additionalProperties = null
        )

        verifyNoMoreInteractions(mockInternalLogger)
    }

    @ParameterizedTest
    @MethodSource("testScenarios")
    fun `M report TTID as TTFD W onTTIDEvent { onTTFDEvent called before onTTIDEvent }`(
        scenario: RumStartupScenario,
        forge: Forge
    ) {
        // Given
        val info = RumTTIDInfo(
            scenario = scenario,
            durationNs = forge.aLong(min = 0, max = 10000)
        )

        val ttidEvent = RumRawEvent.AppStartTTIDEvent(
            info = info
        )

        val ttfdEvent = RumRawEvent.AppStartTTFDEvent()

        // When
        manager.onAppStartEvent(RumRawEvent.AppStartEvent(scenario = scenario))

        manager.onTTFDEvent(
            event = ttfdEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )

        manager.onTTIDEvent(
            event = ttidEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockWriter,
            rumContext = rumContext,
            customAttributes = fakeParentAttributes
        )

        // Then
        inOrder(mockWriter) {
            argumentCaptor<RumVitalAppLaunchEvent> {
                verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
                verifyTTID(value = firstValue, info = info)

                verifyTTFD(
                    value = secondValue,
                    scenario = scenario,
                    durationNs = ttidEvent.info.durationNs
                )
            }

            verifyNoMoreInteractions()
        }
    }

    private fun verifyTTID(value: RumVitalAppLaunchEvent, info: RumTTIDInfo) {
        assertThat(value).apply {
            hasDate(info.scenario.initialTime.timestamp + fakeTimeInfo.serverTimeOffsetMs)
            hasApplicationId(rumContext.applicationId)
            containsExactlyContextAttributes(fakeParentAttributes)
            hasStartReason(rumContext.sessionStartReason)
            hasSampleRate(fakeSampleRate)
            hasNoSyntheticsTest()
            hasSessionId(rumContext.sessionId)
            hasSessionType(
                fakeRumSessionType?.toVitalAppLaunch() ?: RumVitalAppLaunchEvent.RumVitalAppLaunchEventSessionType.USER
            )
            hasNoSessionReplay()

            hasViewId(rumContext.viewId)
            hasUrl(rumContext.viewUrl)
            hasName(rumContext.viewName)

            hasSource(fakeVitalSource)
            hasAccountInfo(fakeDatadogContext.accountInfo)
            hasUserInfo(fakeDatadogContext.userInfo)
            hasDeviceInfo(
                fakeDatadogContext.deviceInfo.deviceName,
                fakeDatadogContext.deviceInfo.deviceModel,
                fakeDatadogContext.deviceInfo.deviceBrand,
                fakeDatadogContext.deviceInfo.deviceType.toVitalAppLaunchSchemaType(),
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
                .apply {
                    if (fakeDatadogContext.featuresContext.containsKey(Feature.PROFILING_FEATURE_NAME)) {
                        hasProfilingStatus(RumVitalAppLaunchEvent.ProfilingStatus.RUNNING)
                    } else {
                        hasNoProfilingStatus()
                    }
                }
            hasNoProfilingErrorReason()
        }

        val vital = value.vital

        assertThat(vital).apply {
            hasName("time_to_initial_display")
            hasDescription(null)
            hasAppLaunchMetric(RumVitalAppLaunchEvent.AppLaunchMetric.TTID)
            hasDuration(info.durationNs)
            hasStartupType(info.scenario.toVitalAppLaunchStartupType())
            hasPrewarmed(null)
            hasSavedInstanceStateBundle(info.scenario.hasSavedInstanceStateBundle)
        }
    }

    private fun verifyTTFD(value: RumVitalAppLaunchEvent, scenario: RumStartupScenario, durationNs: Long) {
        assertThat(value).apply {
            hasDate(scenario.initialTime.timestamp + fakeTimeInfo.serverTimeOffsetMs)
            hasApplicationId(rumContext.applicationId)
            containsExactlyContextAttributes(fakeParentAttributes)
            hasStartReason(rumContext.sessionStartReason)
            hasSampleRate(fakeSampleRate)
            hasNoSyntheticsTest()
            hasSessionId(rumContext.sessionId)
            hasSessionType(
                fakeRumSessionType?.toVitalAppLaunch() ?: RumVitalAppLaunchEvent.RumVitalAppLaunchEventSessionType.USER
            )
            hasNoSessionReplay()

            hasViewId(rumContext.viewId)
            hasUrl(rumContext.viewUrl)
            hasName(rumContext.viewName)

            hasSource(fakeVitalSource)
            hasAccountInfo(fakeDatadogContext.accountInfo)
            hasUserInfo(fakeDatadogContext.userInfo)
            hasDeviceInfo(
                fakeDatadogContext.deviceInfo.deviceName,
                fakeDatadogContext.deviceInfo.deviceModel,
                fakeDatadogContext.deviceInfo.deviceBrand,
                fakeDatadogContext.deviceInfo.deviceType.toVitalAppLaunchSchemaType(),
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
            hasNoProfilingStatus()
            hasNoProfilingErrorReason()
        }

        val vital = value.vital

        assertThat(vital).apply {
            hasName("time_to_full_display")
            hasDescription(null)
            hasAppLaunchMetric(RumVitalAppLaunchEvent.AppLaunchMetric.TTFD)
            hasDuration(durationNs)
            hasStartupType(scenario.toVitalAppLaunchStartupType())
            hasPrewarmed(null)
            hasSavedInstanceStateBundle(scenario.hasSavedInstanceStateBundle)
        }
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
