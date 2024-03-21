/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumPerformanceMetric
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.assertj.ActionEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.ErrorEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.LongTaskEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.ViewEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.RumErrorSourceType
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.anr.ANRException
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.StorageEvent
import com.datadog.android.rum.internal.vitals.VitalInfo
import com.datadog.android.rum.internal.vitals.VitalListener
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aFilteredMap
import com.datadog.tools.unit.forge.anException
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Arrays
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumViewScopeTest {

    lateinit var testedScope: RumViewScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockChildScope: RumScope

    @Mock
    lateinit var mockActionScope: RumActionScope

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockResolver: FirstPartyHostHeaderTypeResolver

    @Mock
    lateinit var mockCpuVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockMemoryVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockFrameRateVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @StringForgery(regex = "[a-f0-9]{8}-([a-f0-9]{4}-){3}[a-f0-9]{12}")
    lateinit var fakeActionId: String

    lateinit var fakeUrl: String

    @Forgery
    lateinit var fakeKey: RumScopeKey
    lateinit var fakeAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeParentContext: RumContext

    lateinit var fakeEventTime: Time

    lateinit var fakeEvent: RumRawEvent

    @Forgery
    lateinit var fakeTimeInfoAtScopeStart: TimeInfo

    @Forgery
    lateinit var fakeNetworkInfoAtScopeStart: NetworkInfo

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    var fakeSourceViewEvent: ViewEvent.ViewEventSource? = null
    var fakeSourceErrorEvent: ErrorEvent.ErrorEventSource? = null
    var fakeSourceActionEvent: ActionEvent.ActionEventSource? = null
    var fakeSourceLongTaskEvent: LongTaskEvent.LongTaskEventSource? = null

    @BoolForgery
    var fakeHasReplay: Boolean = false

    @LongForgery(min = 0)
    var fakeReplayRecordsCount: Long = 0

    @Mock
    lateinit var mockFeaturesContextResolver: FeaturesContextResolver

    @Mock
    lateinit var mockViewChangedListener: RumViewChangedListener

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    lateinit var fakeReplayStats: ViewEvent.ReplayStats

    private var fakeSampleRate: Float = 0.0f

    @BeforeEach
    fun `set up`(forge: Forge) {
        val isValidSource = forge.aBool()

        val fakeSource = if (isValidSource) {
            forge.anElementFrom(
                ViewEvent.ViewEventSource.values().map { it.toJson().asString }
            )
        } else {
            forge.anAlphabeticalString()
        }

        fakeSourceViewEvent = if (isValidSource) {
            ViewEvent.ViewEventSource.fromJson(fakeSource)
        } else {
            null
        }
        fakeSourceErrorEvent = if (isValidSource) {
            ErrorEvent.ErrorEventSource.fromJson(fakeSource)
        } else {
            null
        }
        fakeSourceActionEvent = if (isValidSource) {
            ActionEvent.ActionEventSource.fromJson(fakeSource)
        } else {
            null
        }
        fakeSourceLongTaskEvent = if (isValidSource) {
            LongTaskEvent.LongTaskEventSource.fromJson(fakeSource)
        } else {
            null
        }

        fakeDatadogContext = fakeDatadogContext.copy(
            source = fakeSource
        )

        fakeParentContext = fakeParentContext.copy(syntheticsTestId = null, syntheticsResultId = null)

        val fakeOffset = -forge.aLong(1000, 50000)
        val fakeTimestamp = System.currentTimeMillis() + fakeOffset
        val fakeNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(fakeOffset)
        val maxLimit = max(Long.MAX_VALUE - fakeTimestamp, Long.MAX_VALUE)
        val minLimit = min(-fakeTimestamp, maxLimit)
        fakeSampleRate = forge.aFloat(min = 0.0f, max = 100.0f)

        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeTimeInfoAtScopeStart.copy(
                serverTimeOffsetMs = forge.aLong(min = minLimit, max = maxLimit)
            )
        )
        fakeEventTime = Time(fakeTimestamp, fakeNanos)
        fakeAttributes = forge.exhaustiveAttributes()
        fakeKey = forge.getForgery()
        fakeEvent = mockEvent()
        fakeUrl = fakeKey.url.replace('.', '/')

        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext
        whenever(mockChildScope.handleEvent(any(), any())) doReturn mockChildScope
        whenever(mockActionScope.handleEvent(any(), any())) doReturn mockActionScope
        whenever(mockActionScope.actionId) doReturn fakeActionId
        whenever(rumMonitor.mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(rumMonitor.mockSdkCore.time) doReturn fakeTimeInfoAtScopeStart
        whenever(rumMonitor.mockSdkCore.networkInfo) doReturn fakeNetworkInfoAtScopeStart
        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }
        whenever(mockWriter.write(eq(mockEventBatchWriter), any())) doReturn true
        fakeReplayStats = ViewEvent.ReplayStats(recordsCount = fakeReplayRecordsCount)
        testedScope = RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockFeaturesContextResolver,
            trackFrustrations = true,
            sampleRate = fakeSampleRate
        )
        mockSessionReplayContext(testedScope)
    }

    // region Context

    @Test
    fun `𝕄 return valid RumContext 𝕎 getRumContext()`() {
        // When
        val context = testedScope.getRumContext()

        // Then
        assertThat(context.actionId).isNull()
        assertThat(context.viewId).isEqualTo(testedScope.viewId)
        assertThat(context.viewName).isEqualTo(fakeKey.name)
        assertThat(context.viewUrl).isEqualTo(fakeUrl)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.sessionId).isEqualTo(fakeParentContext.sessionId)
    }

    @Test
    fun `𝕄 return active actionId 𝕎 getRumContext() with child ActionScope`() {
        // Given
        testedScope.activeActionScope = mockActionScope

        // When
        val context = testedScope.getRumContext()

        // Then
        assertThat(context.actionId).isEqualTo(fakeActionId)
        assertThat(context.viewId).isEqualTo(testedScope.viewId)
        assertThat(context.viewName).isEqualTo(fakeKey.name)
        assertThat(context.viewUrl).isEqualTo(fakeUrl)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)
        assertThat(context.sessionId).isEqualTo(fakeParentContext.sessionId)
    }

    @Test
    fun `𝕄 update RUM feature context 𝕎 init()`() {
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore)
                .updateFeatureContext(eq(Feature.RUM_FEATURE_NAME), capture())

            val rumContext = mutableMapOf<String, Any?>()
            firstValue.invoke(rumContext)

            assertThat(RumContext.fromFeatureContext(rumContext))
                .isEqualTo(testedScope.getRumContext())
        }
    }

    @Test
    fun `𝕄 update the viewId 𝕎 getRumContext() with parent sessionId changed`(
        @Forgery newSessionId: UUID
    ) {
        // Given
        val initialViewId = testedScope.viewId
        val context = testedScope.getRumContext()
        whenever(mockParentScope.getRumContext())
            .doReturn(fakeParentContext.copy(sessionId = newSessionId.toString()))

        // When
        val updatedContext = testedScope.getRumContext()

        // Then
        assertThat(context.actionId).isNull()
        assertThat(context.viewId).isEqualTo(initialViewId)
        assertThat(context.viewName).isEqualTo(fakeKey.name)
        assertThat(context.viewUrl).isEqualTo(fakeUrl)
        assertThat(context.sessionId).isEqualTo(fakeParentContext.sessionId)
        assertThat(context.applicationId).isEqualTo(fakeParentContext.applicationId)

        assertThat(updatedContext.actionId).isNull()
        assertThat(updatedContext.viewId).isNotEqualTo(initialViewId)
        assertThat(context.viewName).isEqualTo(fakeKey.name)
        assertThat(updatedContext.viewUrl).isEqualTo(fakeUrl)
        assertThat(updatedContext.sessionId).isEqualTo(newSessionId.toString())
        assertThat(updatedContext.applicationId).isEqualTo(fakeParentContext.applicationId)
    }

    @Test
    fun `M update the context with the viewType W initializing`(forge: Forge) {
        // Given
        val fakeViewEventType = forge.aValueFrom(RumViewScope.RumViewType::class.java)

        // When
        testedScope = RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockFeaturesContextResolver,
            type = fakeViewEventType,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore, times(2)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )

            val rumContext = mutableMapOf<String, Any?>()
            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }
            assertThat(rumContext["view_type"]).isEqualTo(fakeViewEventType.asString)
        }
    }

    @Test
    fun `𝕄 update the feature context with the view timestamp offset W initializing`() {
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )

            val rumContext = mutableMapOf<String, Any?>()
            lastValue.invoke(rumContext)
            assertThat(rumContext[RumFeature.VIEW_TIMESTAMP_OFFSET_IN_MS_KEY])
                .isEqualTo(fakeTimeInfoAtScopeStart.serverTimeOffsetMs)
        }
    }

    @Test
    fun `𝕄 update the context with viewType NONE W handleEvent(StopView)`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore, times(2)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )

            val rumContext = mutableMapOf<String, Any?>()
            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }
            assertThat(rumContext["view_type"]).isEqualTo(RumViewScope.RumViewType.NONE.asString)
        }
    }

    @Test
    fun `𝕄 not update the context with viewType NONE W handleEvent(StopView) { unknown key }`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(forge.getForgery(), attributes),
            mockWriter
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )
            val rumContext = mutableMapOf<String, Any?>()
            lastValue.invoke(rumContext)
            assertThat(rumContext["view_type"]).isEqualTo(RumViewScope.RumViewType.FOREGROUND.asString)
        }
    }

    @Test
    fun `𝕄 not update the context W handleEvent(StopView) { cur vs glob view ids don't match }`(
        forge: Forge
    ) {
        // Given
        val expectedViewType = forge.aValueFrom(RumViewScope.RumViewType::class.java)

        // need to create this one, because RUM context is updated in the constructor
        val anotherScope = RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockFeaturesContextResolver,
            type = expectedViewType,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )

        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            // A scope init + B scope init + A scope stop
            verify(rumMonitor.mockSdkCore, times(3)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )

            val rumContext = mutableMapOf<String, Any?>()

            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }

            assertThat(rumContext["view_type"]).isEqualTo(expectedViewType.asString)
            assertThat(rumContext["view_name"]).isEqualTo(anotherScope.getRumContext().viewName)
            assertThat(rumContext["view_id"]).isEqualTo(anotherScope.getRumContext().viewId)
            assertThat(rumContext["view_url"]).isEqualTo(anotherScope.getRumContext().viewUrl)
            assertThat(rumContext["action_id"]).isEqualTo(anotherScope.getRumContext().actionId)
        }
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.MAINTAINER,
            RumViewScope.RUM_CONTEXT_UPDATE_IGNORED_AT_STOP_VIEW_MESSAGE
        )
    }

    @Test
    fun `𝕄 update the context W handleEvent(StopView) { new session }`(
        forge: Forge
    ) {
        // Given
        val currentContext = testedScope.getRumContext()

        val fakeNewSessionContext: RumContext = forge.getForgery()
        whenever(mockParentScope.getRumContext()) doReturn fakeNewSessionContext

        assumeTrue { currentContext.sessionId != fakeNewSessionContext.sessionId }

        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore, times(2)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )
            val rumContext = mutableMapOf<String, Any?>()

            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }

            assertThat(rumContext["view_type"]).isEqualTo(RumViewScope.RumViewType.NONE.asString)
            assertThat(rumContext["view_name"]).isNull()
            assertThat(rumContext["view_id"]).isNull()
            assertThat(rumContext["view_url"]).isNull()
            assertThat(rumContext["action_id"]).isNull()
        }
    }

    @Test
    fun `𝕄 not update the context W handleEvent() { action completes after view stopped }`(
        @StringForgery actionName: String,
        @Forgery rumActionType: RumActionType
    ) {
        // Given
        testedScope.activeActionScope = mockChildScope
        val stopActionEvent = RumRawEvent.StopAction(rumActionType, actionName, emptyMap())
        whenever(mockChildScope.handleEvent(stopActionEvent, mockWriter)) doReturn null

        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )
        testedScope.handleEvent(
            stopActionEvent,
            mockWriter
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            // scope init + stop view + stop action
            verify(rumMonitor.mockSdkCore, times(3)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )

            val rumContext = mutableMapOf<String, Any?>()
            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }

            assertThat(rumContext["view_type"]).isEqualTo(RumViewScope.RumViewType.NONE.asString)
            assertThat(rumContext["view_name"]).isNull()
            assertThat(rumContext["view_id"]).isNull()
            assertThat(rumContext["view_url"]).isNull()
            assertThat(rumContext["action_id"]).isNull()
        }

        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.MAINTAINER,
            RumViewScope.RUM_CONTEXT_UPDATE_IGNORED_AT_ACTION_UPDATE_MESSAGE
        )
    }

    // endregion

    // region isActive

    @Test
    fun `M return true W isActive() {not stopped}`() {
        // Given
        testedScope.stopped = false

        // When
        val isActive = testedScope.isActive()

        // Then
        assertThat(isActive).isTrue()
    }

    @Test
    fun `M return false W isActive() {stopped}`() {
        // Given
        testedScope.stopped = true

        // When
        val isActive = testedScope.isActive()

        // Then
        assertThat(isActive).isFalse()
    }

    // endregion

    // region View

    @ParameterizedTest
    @EnumSource(
        value = RumViewScope.RumViewType::class,
        names = ["NONE"],
        mode = EnumSource.Mode.EXCLUDE
    )
    fun `𝕄 not update the viewType to NONE W handleEvent(StartView) { on active view }`(
        viewType: RumViewScope.RumViewType,
        @Forgery key: RumScopeKey
    ) {
        // Given
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )
        RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            key,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockFeaturesContextResolver,
            type = viewType,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )

        // When
        testedScope.handleEvent(
            RumRawEvent.StartView(key, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            // A scope init + onStopView + B scope init
            verify(rumMonitor.mockSdkCore, times(3)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )
            val rumContext = mutableMapOf<String, Any?>()
            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }
            assertThat(rumContext["view_type"]).isEqualTo(viewType.asString)
        }
    }

    @ParameterizedTest
    @EnumSource(
        value = RumViewScope.RumViewType::class,
        names = ["NONE"],
        mode = EnumSource.Mode.EXCLUDE
    )
    fun `𝕄 not update the viewType to NONE W handleEvent(StopView) {already stopped, active view}`(
        viewType: RumViewScope.RumViewType,
        @Forgery key: RumScopeKey
    ) {
        // Given
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )
        RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            key,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockFeaturesContextResolver,
            type = viewType,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )

        // When
        testedScope.handleEvent(
            RumRawEvent.StopView(key, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            // A scope init + A scope stop + B scope init
            verify(rumMonitor.mockSdkCore, times(3)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )
            val rumContext = mutableMapOf<String, Any?>()
            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }
            assertThat(rumContext["view_type"]).isEqualTo(viewType.asString)
        }
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(StartView) on stopped view`(
        @Forgery key: RumScopeKey
    ) {
        // Given
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StartView(key, emptyMap()),
            mockWriter
        )

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(StartView) on active view`(
        @Forgery key: RumScopeKey
    ) {
        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StartView(key, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue).apply {
                hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                hasName(fakeKey.name)
                hasUrl(fakeUrl)
                hasDurationGreaterThan(1)
                hasVersion(2)
                hasErrorCount(0)
                hasCrashCount(0)
                hasResourceCount(0)
                hasActionCount(0)
                hasFrustrationCount(0)
                hasLongTaskCount(0)
                hasFrozenFrameCount(0)
                hasCpuMetric(null)
                hasMemoryMetric(null, null)
                hasRefreshRateMetric(null, null)
                isActive(false)
                isSlowRendered(false)
                hasNoCustomTimings()
                hasUserInfo(fakeDatadogContext.userInfo)
                hasViewId(testedScope.viewId)
                hasApplicationId(fakeParentContext.applicationId)
                hasSessionId(fakeParentContext.sessionId)
                hasUserSession()
                hasNoSyntheticsTest()
                hasLiteSessionPlan()
                hasStartReason(fakeParentContext.sessionStartReason)
                hasReplay(fakeHasReplay)
                hasReplayStats(fakeReplayStats)
                hasSource(fakeSourceViewEvent)
                containsExactlyContextAttributes(fakeAttributes)
                hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )
                hasConnectivityInfo(fakeDatadogContext.networkInfo)
                hasServiceName(fakeDatadogContext.service)
                hasVersion(fakeDatadogContext.version)
                hasSessionActive(fakeParentContext.isSessionActive)
                hasSampleRate(fakeSampleRate)
            }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event once 𝕎 handleEvent(StartView) twice on active view`(
        @Forgery key: RumScopeKey,
        @Forgery key2: RumScopeKey
    ) {
        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StartView(key, emptyMap()),
            mockWriter
        )
        val result2 = testedScope.handleEvent(
            RumRawEvent.StartView(key2, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    containsExactlyContextAttributes(fakeAttributes)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(StopView) on active view`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    containsExactlyContextAttributes(expectedAttributes)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(StopView) on active view { pending attributes are negative }`(
        forge: Forge
    ) {
        // Given
        testedScope.pendingActionCount = forge.aLong(min = 0, max = 100) * (-1)
        testedScope.pendingResourceCount = forge.aLong(min = 0, max = 100) * (-1)
        testedScope.pendingErrorCount = forge.aLong(min = 0, max = 100) * (-1)
        testedScope.pendingLongTaskCount = forge.aLong(min = 0, max = 100) * (-1)

        // we limit it to 100 to avoid overflow and when we add those and end up with a positive
        // number
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(StopView) on active view { pending attributes are positive }`(
        forge: Forge
    ) {
        // Given
        testedScope.pendingActionCount = forge.aLong(min = 0, max = 100)
        testedScope.pendingResourceCount = forge.aLong(min = 0, max = 100)
        testedScope.pendingErrorCount = forge.aLong(min = 0, max = 100)
        testedScope.pendingLongTaskCount = forge.aLong(min = 0, max = 100)
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(StopView) on active view { still has ongoing resources }`(
        forge: Forge,
        @StringForgery key: String
    ) {
        // Given
        val mockResourceScope: RumScope = mock()
        whenever(mockResourceScope.handleEvent(any(), any())) doReturn mockResourceScope
        testedScope.activeResourceScopes[key] = mockResourceScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event with user extra attributes 𝕎 handleEvent(StopView) on active view`() {
        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event with updated global attributes 𝕎 handleEvent(StopView) on active view`(
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(emptyMap())
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn fakeGlobalAttributes

        testedScope = RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )
        mockSessionReplayContext(testedScope)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn emptyMap()

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event with global attributes 𝕎 handleEvent(StopView) on active view`(
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeGlobalAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn fakeGlobalAttributes
        testedScope = RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )
        mockSessionReplayContext(testedScope)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 not take into account global attribute removal 𝕎 handleEvent(StopView) on active view`(
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributeKey = forge.anAlphabeticalString()
        val fakeGlobalAttributeValue = forge.anAlphabeticalString()
        whenever(rumMonitor.mockInstance.getAttributes())
            .doReturn(mapOf(fakeGlobalAttributeKey to fakeGlobalAttributeValue))

        testedScope = RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )
        mockSessionReplayContext(testedScope)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.put(fakeGlobalAttributeKey, fakeGlobalAttributeValue)

        // When

        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 take into account global attribute update 𝕎 handleEvent(StopView) on active view`(
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributeKey = forge.anAlphabeticalString()
        val fakeGlobalAttributeValue = forge.anAlphabeticalString()
        val fakeGlobalAttributeNewValue =
            fakeGlobalAttributeValue + forge.anAlphabeticalString(size = 2)
        whenever(rumMonitor.mockInstance.getAttributes())
            .doReturn(mapOf(fakeGlobalAttributeKey to fakeGlobalAttributeValue))

        testedScope = RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )
        mockSessionReplayContext(testedScope)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes[fakeGlobalAttributeKey] = fakeGlobalAttributeNewValue
        whenever(rumMonitor.mockInstance.getAttributes())
            .doReturn(mapOf(fakeGlobalAttributeKey to fakeGlobalAttributeNewValue))

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, emptyMap()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event once 𝕎 handleEvent(StopView) twice on active view`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )
        val result2 = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(result2).isNull()
    }

    @Test
    fun `𝕄 returns not null 𝕎 handleEvent(StopView) and a resource is still active`(
        @StringForgery key: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeResourceScopes.put(key, mockChildScope)
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(StopView) on active view without matching key`(
        @Forgery key: RumScopeKey,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(key, attributes),
            mockWriter
        )

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(StopView) on stopped view`(
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ErrorSent) on active view`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        fakeEvent = RumRawEvent.ErrorSent(testedScope.viewId)
        testedScope.pendingErrorCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingErrorCount).isEqualTo(pending - 1)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ErrorSent) on active view {viewId changed}`(
        @LongForgery(1) pending: Long,
        @Forgery fakeNewViewId: UUID
    ) {
        // Given
        fakeEvent = RumRawEvent.ErrorSent(testedScope.viewId)
        testedScope.pendingErrorCount = pending
        testedScope.viewId = fakeNewViewId.toString()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    // TODO RUMM-3316 if viewId changes, we need to relink replay as well.
                    hasReplay(false)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingErrorCount).isEqualTo(pending - 1)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ErrorSent) on active view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ErrorSent(viewId)
        testedScope.pendingErrorCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingErrorCount).isEqualTo(pending)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ResourceSent) on active view`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.pendingResourceCount = pending
        fakeEvent = RumRawEvent.ResourceSent(testedScope.viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(1)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending - 1)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ResourceSent) on active view {viewId changed}`(
        @LongForgery(1) pending: Long,
        @Forgery fakeNewViewId: UUID
    ) {
        // Given
        testedScope.pendingResourceCount = pending
        fakeEvent = RumRawEvent.ResourceSent(testedScope.viewId)
        testedScope.viewId = fakeNewViewId.toString()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(1)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    // TODO RUMM-3316 if viewId changes, we need to relink replay as well.
                    hasReplay(false)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending - 1)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ResourceSent) on active view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.pendingResourceCount = pending
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ResourceSent(viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ActionSent) on active view`(
        @LongForgery(1) pending: Long,
        @IntForgery(0) frustrationCount: Int
    ) {
        // Given
        fakeEvent = RumRawEvent.ActionSent(testedScope.viewId, frustrationCount)
        testedScope.pendingActionCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(1)
                    hasFrustrationCount(frustrationCount.toLong())
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingActionCount).isEqualTo(pending - 1)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ActionSent) on active view {viewId changed}`(
        @LongForgery(1) pending: Long,
        @IntForgery(0) frustrationCount: Int,
        @Forgery fakeNewViewId: UUID
    ) {
        // Given
        fakeEvent = RumRawEvent.ActionSent(testedScope.viewId, frustrationCount)
        testedScope.pendingActionCount = pending
        testedScope.viewId = fakeNewViewId.toString()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(1)
                    hasFrustrationCount(frustrationCount.toLong())
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    // TODO RUMM-3316 if viewId changes, we need to relink replay as well.
                    hasReplay(false)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingActionCount).isEqualTo(pending - 1)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ActionSent) on active view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long,
        @IntForgery(0) frustrationCount: Int
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ActionSent(viewId, frustrationCount)
        testedScope.pendingActionCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingActionCount).isEqualTo(pending)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(LongTaskSent) on active view {not frozen}`(
        @LongForgery(1) pendingLongTask: Long,
        @LongForgery(1) pendingFrozenFrame: Long
    ) {
        // Given
        fakeEvent = RumRawEvent.LongTaskSent(testedScope.viewId)
        testedScope.pendingLongTaskCount = pendingLongTask
        testedScope.pendingFrozenFrameCount = pendingFrozenFrame

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(1)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pendingLongTask - 1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(pendingFrozenFrame)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(LongTaskSent) on active view {not frozen, viewId changed}`(
        @LongForgery(1) pendingLongTask: Long,
        @LongForgery(1) pendingFrozenFrame: Long,
        @Forgery fakeNewViewId: UUID
    ) {
        // Given
        fakeEvent = RumRawEvent.LongTaskSent(testedScope.viewId)
        testedScope.pendingLongTaskCount = pendingLongTask
        testedScope.pendingFrozenFrameCount = pendingFrozenFrame
        testedScope.viewId = fakeNewViewId.toString()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(1)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    // TODO RUMM-3316 if viewId changes, we need to relink replay as well.
                    hasReplay(false)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pendingLongTask - 1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(pendingFrozenFrame)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(LongTaskSent) on active view {frozen}`(
        @LongForgery(1) pendingLongTask: Long,
        @LongForgery(1) pendingFrozenFrame: Long
    ) {
        // Given
        fakeEvent = RumRawEvent.LongTaskSent(testedScope.viewId, true)
        testedScope.pendingLongTaskCount = pendingLongTask
        testedScope.pendingFrozenFrameCount = pendingFrozenFrame

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(1)
                    hasFrozenFrameCount(1)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pendingLongTask - 1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(pendingFrozenFrame - 1)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(LongTaskSent) on active view {frozen, viewId changed}`(
        @LongForgery(1) pendingLongTask: Long,
        @LongForgery(1) pendingFrozenFrame: Long,
        @Forgery fakeNewViewId: UUID
    ) {
        // Given
        fakeEvent = RumRawEvent.LongTaskSent(testedScope.viewId, true)
        testedScope.pendingLongTaskCount = pendingLongTask
        testedScope.pendingFrozenFrameCount = pendingFrozenFrame
        testedScope.viewId = fakeNewViewId.toString()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(1)
                    hasFrozenFrameCount(1)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    // TODO RUMM-3316 if viewId changes, we need to relink replay as well.
                    hasReplay(false)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pendingLongTask - 1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(pendingFrozenFrame - 1)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(LongTaskSent) on active view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.LongTaskSent(viewId)
        testedScope.pendingLongTaskCount = pending

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
    }

    @Test
    fun `𝕄 send event with global attributes 𝕎 handleEvent(ApplicationStarted) on active view`(
        @LongForgery(0) duration: Long,
        forge: Forge
    ) {
        // Given
        val eventTime = Time()
        fakeEvent = RumRawEvent.ApplicationStarted(eventTime, duration)
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn attributes

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(firstValue)
                .apply {
                    hasNonNullId()
                    hasTimestamp(testedScope.eventTimestamp)
                    hasType(ActionEvent.ActionEventActionType.APPLICATION_START)
                    hasNoTarget()
                    hasDuration(duration)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(false)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    containsExactlyContextAttributes(attributes)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event with synthetics info 𝕎 handleEvent(ApplicationStarted) on active view`(
        @LongForgery(0) duration: Long,
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String,
        forge: Forge
    ) {
        // Given
        val eventTime = Time()
        fakeEvent = RumRawEvent.ApplicationStarted(eventTime, duration)
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn attributes
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(firstValue)
                .apply {
                    hasNonNullId()
                    hasTimestamp(testedScope.eventTimestamp)
                    hasType(ActionEvent.ActionEventActionType.APPLICATION_START)
                    hasNoTarget()
                    hasDuration(duration)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSyntheticsSession()
                    hasSyntheticsTest(fakeTestId, fakeResultId)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(false)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    containsExactlyContextAttributes(attributes)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ErrorSent) on stopped view`() {
        // Given
        testedScope.stopped = true
        testedScope.pendingErrorCount = 1
        fakeEvent = RumRawEvent.ErrorSent(testedScope.viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.pendingErrorCount).isEqualTo(0)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ErrorSent) on stopped view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingErrorCount = pending
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ErrorSent(viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingErrorCount).isEqualTo(pending)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ResourceSent) on stopped view`() {
        // Given
        testedScope.stopped = true
        testedScope.pendingResourceCount = 1
        fakeEvent = RumRawEvent.ResourceSent(testedScope.viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(1)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.pendingResourceCount).isEqualTo(0)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ResourceSent) on stopped view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingResourceCount = pending
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ResourceSent(viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(ActionSent) on stopped view`(
        @IntForgery(0) frustrationCount: Int
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingActionCount = 1
        fakeEvent = RumRawEvent.ActionSent(testedScope.viewId, frustrationCount)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(1)
                    hasFrustrationCount(frustrationCount.toLong())
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.pendingActionCount).isEqualTo(0)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ActionSent) on stopped view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long,
        @IntForgery(0) frustrationCount: Int
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingActionCount = pending
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.ActionSent(viewId, frustrationCount)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingActionCount).isEqualTo(pending)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(LongTaskSent) on stopped view`() {
        // Given
        testedScope.stopped = true
        testedScope.pendingLongTaskCount = 1
        fakeEvent = RumRawEvent.LongTaskSent(testedScope.viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(1)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(0)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(LongTaskSent) on stopped view {unknown viewId}`(
        @Forgery viewUuid: UUID,
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingLongTaskCount = pending
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        fakeEvent = RumRawEvent.LongTaskSent(viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
    }

    @Test
    fun `𝕄 close the scope 𝕎 handleEvent(ActionSent) on stopped view { ApplicationStarted }`(
        @LongForgery(0) duration: Long,
        @IntForgery(0) frustrationCount: Int
    ) {
        // Given
        testedScope.stopped = true
        val eventTime = Time()
        fakeEvent = RumRawEvent.ApplicationStarted(eventTime, duration)
        val fakeActionSent = RumRawEvent.ActionSent(testedScope.viewId, frustrationCount)

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)
        val result = testedScope.handleEvent(fakeActionSent, mockWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture())
            assertThat(firstValue as ActionEvent)
                .apply {
                    hasNonNullId()
                    hasTimestamp(testedScope.eventTimestamp)
                    hasType(ActionEvent.ActionEventActionType.APPLICATION_START)
                    hasNoTarget()
                    hasDuration(duration)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(false)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 close the scope 𝕎 handleEvent(ActionDropped) on stopped view { ApplicationStarted }`(
        @LongForgery(0) duration: Long
    ) {
        // Given
        testedScope.stopped = true
        val eventTime = Time()
        fakeEvent = RumRawEvent.ApplicationStarted(eventTime, duration)
        val fakeActionSent = RumRawEvent.ActionDropped(testedScope.viewId)

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)
        val result = testedScope.handleEvent(fakeActionSent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(firstValue)
                .apply {
                    hasNonNullId()
                    hasTimestamp(testedScope.eventTimestamp)
                    hasType(ActionEvent.ActionEventActionType.APPLICATION_START)
                    hasNoTarget()
                    hasDuration(duration)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(false)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(KeepAlive) on stopped view`() {
        // Given
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(KeepAlive) on active view`() {
        // When
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 returns null 𝕎 handleEvent(any) on stopped view {no pending event}`() {
        // Given
        testedScope.stopped = true
        fakeEvent = mock()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 remove the hasReplay entry W handleEvent(any) on stopped view {no pending event}`(
        forge: Forge
    ) {
        // Given
        val argumentCaptor = argumentCaptor<(MutableMap<String, Any?>) -> Unit>()
        val fakeSessionReplayContext = forge.exhaustiveAttributes()
            .apply { put(testedScope.viewId, forge.aBool()) }
        testedScope.stopped = true
        fakeEvent = mock()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockSdkCore).updateFeatureContext(
            eq(Feature.SESSION_REPLAY_FEATURE_NAME),
            argumentCaptor.capture()
        )
        argumentCaptor.firstValue.invoke(fakeSessionReplayContext)
        assertThat(fakeSessionReplayContext).doesNotContainKey(testedScope.viewId)
    }

    @Test
    fun `𝕄 returns self 𝕎 handleEvent(any) on stopped view {pending action event}`(
        @LongForgery(1, 32) pendingEvents: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingActionCount = pendingEvents
        fakeEvent = mock()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        verify(rumMonitor.mockSdkCore, never()).updateFeatureContext(
            eq(Feature.SESSION_REPLAY_FEATURE_NAME),
            any()
        )
    }

    @Test
    fun `𝕄 returns self 𝕎 handleEvent(any) on stopped view {pending resource event}`(
        @LongForgery(1, 32) pendingEvents: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingResourceCount = pendingEvents
        fakeEvent = mock()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        verify(rumMonitor.mockSdkCore, never()).updateFeatureContext(
            eq(Feature.SESSION_REPLAY_FEATURE_NAME),
            any()
        )
    }

    @Test
    fun `𝕄 returns self 𝕎 handleEvent(any) on stopped view {pending error event}`(
        @LongForgery(1, 32) pendingEvents: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingErrorCount = pendingEvents
        fakeEvent = mock()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        verify(rumMonitor.mockSdkCore, never()).updateFeatureContext(
            eq(Feature.SESSION_REPLAY_FEATURE_NAME),
            any()
        )
    }

    @Test
    fun `𝕄 returns self 𝕎 handleEvent(any) on stopped view {pending long task event}`(
        @LongForgery(1, 32) pendingEvents: Long
    ) {
        // Given
        testedScope.stopped = true
        testedScope.pendingLongTaskCount = pendingEvents
        fakeEvent = mock()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        verify(rumMonitor.mockSdkCore, never()).updateFeatureContext(
            eq(Feature.SESSION_REPLAY_FEATURE_NAME),
            any()
        )
    }

    @Test
    fun `𝕄 send event with synthetics info 𝕎 handleEvent(StopView) on active view`(
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(attributes)
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, attributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(false)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSyntheticsSession()
                    hasSyntheticsTest(fakeTestId, fakeResultId)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    // endregion

    // region Action

    @Test
    fun `𝕄 create ActionScope 𝕎 handleEvent(StartAction)`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        // When
        val fakeStartActionEvent = RumRawEvent.StartAction(type, name, waitForStop, attributes)
        val result = testedScope.handleEvent(
            fakeStartActionEvent,
            mockWriter
        )

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isInstanceOf(RumActionScope::class.java)
        val actionScope = testedScope.activeActionScope as RumActionScope
        assertThat(actionScope.name).isEqualTo(name)
        assertThat(actionScope.eventTimestamp)
            .isEqualTo(resolveExpectedTimestamp(fakeStartActionEvent.eventTime.timestamp))
        assertThat(actionScope.waitForStop).isEqualTo(waitForStop)
        assertThat(actionScope.attributes).containsAllEntriesOf(attributes)
        assertThat(actionScope.parentScope).isSameAs(testedScope)
        assertThat(actionScope.sampleRate).isCloseTo(fakeSampleRate, Assertions.offset(0.001f))
    }

    @Test
    fun `𝕄 update the RumContext in GlobalRum W ActionScope created`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        // When
        val fakeStartActionEvent = RumRawEvent.StartAction(type, name, waitForStop, attributes)
        testedScope.handleEvent(
            fakeStartActionEvent,
            mockWriter
        )

        // Then
        argumentCaptor<(Map<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore, times(2)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )
            val rumContext = mutableMapOf<String, Any?>()
            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }
            assertThat(rumContext["action_id"])
                .isEqualTo((testedScope.activeActionScope as RumActionScope).actionId)
        }
    }

    @ParameterizedTest
    @EnumSource(RumActionType::class, names = ["CUSTOM"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 do nothing + log warning 𝕎 handleEvent(StartAction+!CUSTOM)+active child ActionScope`(
        actionType: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        testedScope.activeActionScope = mockChildScope
        fakeEvent = RumRawEvent.StartAction(actionType, name, waitForStop, attributes)
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isSameAs(mockChildScope)

        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumViewScope.ACTION_DROPPED_WARNING.format(
                Locale.US,
                (fakeEvent as RumRawEvent.StartAction).type,
                (fakeEvent as RumRawEvent.StartAction).name
            )
        )
    }

    @Test
    fun `𝕄 do nothing + log warning 𝕎 handleEvent(StartAction+CUSTOM+cont) + child ActionScope`(
        @StringForgery name: String,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        testedScope.activeActionScope = mockChildScope
        fakeEvent =
            RumRawEvent.StartAction(RumActionType.CUSTOM, name, waitForStop = true, attributes)
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isSameAs(mockChildScope)

        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            RumViewScope.ACTION_DROPPED_WARNING.format(
                Locale.US,
                (fakeEvent as RumRawEvent.StartAction).type,
                (fakeEvent as RumRawEvent.StartAction).name
            )
        )
    }

    @Test
    fun `𝕄 send action 𝕎 handleEvent(StartAction+CUSTOM+instant) + active child ActionScope`(
        @StringForgery name: String,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        testedScope.activeActionScope = mockChildScope
        fakeEvent = RumRawEvent.StartAction(RumActionType.CUSTOM, name, false, attributes)
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter, times(1)).write(eq(mockEventBatchWriter), capture())
            assertThat(firstValue)
                .apply {
                    hasNonNullId()
                    hasType(RumActionType.CUSTOM)
                    hasTargetName(name)
                    hasDuration(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasView(testedScope.getRumContext())
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)

        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isSameAs(mockChildScope)
        assertThat(testedScope.pendingActionCount).isEqualTo(1)
    }

    @Test
    fun `𝕄 send action with synthetics 𝕎 handleEvent(StartAction+CUSTOM+instant) + active child ActionScope`(
        @StringForgery name: String,
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        testedScope.activeActionScope = mockChildScope
        fakeEvent = RumRawEvent.StartAction(RumActionType.CUSTOM, name, false, attributes)
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ActionEvent> {
            verify(mockWriter, times(1)).write(eq(mockEventBatchWriter), capture())
            assertThat(firstValue)
                .apply {
                    hasNonNullId()
                    hasType(RumActionType.CUSTOM)
                    hasTargetName(name)
                    hasDuration(1)
                    hasResourceCount(0)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasLongTaskCount(0)
                    hasView(testedScope.getRumContext())
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasSyntheticsSession()
                    hasSyntheticsTest(fakeTestId, fakeResultId)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceActionEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeNetworkInfoAtScopeStart)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)

        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isSameAs(mockChildScope)
        assertThat(testedScope.pendingActionCount).isEqualTo(1)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(StartAction) on stopped view`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        testedScope.stopped = true
        fakeEvent = RumRawEvent.StartAction(type, name, waitForStop, attributes)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isNull()
        assertThat(testedScope.activeActionScope).isNull()
    }

    @Test
    fun `𝕄 send event to child ActionScope 𝕎 handleEvent(StartView) on active view`() {
        // Given
        testedScope.activeActionScope = mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event to child ActionScope 𝕎 handleEvent() on stopped view`() {
        // Given
        testedScope.stopped = true
        testedScope.activeActionScope = mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 remove child ActionScope 𝕎 handleEvent() returns null`() {
        // Given
        testedScope.activeActionScope = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn null

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeActionScope).isNull()
    }

    @Test
    fun `𝕄 update the RumContext in GlobalRum when removing the ActionScope`() {
        // Given
        testedScope.activeActionScope = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn null

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<(Map<String, Any?>) -> Unit> {
            verify(rumMonitor.mockSdkCore, times(2)).updateFeatureContext(
                eq(Feature.RUM_FEATURE_NAME),
                capture()
            )
            val rumContext = mutableMapOf<String, Any?>()
            allValues.fold(rumContext) { acc, function ->
                function.invoke(acc)
                acc
            }
            assertThat(rumContext["action_id"]).isNull()
        }
    }

    @Test
    fun `𝕄 wait for pending 𝕎 handleEvent(StartAction) on active view`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        @BoolForgery waitForStop: Boolean
    ) {
        // Given
        testedScope.activeActionScope = null
        testedScope.pendingActionCount = 0
        fakeEvent = RumRawEvent.StartAction(type, name, waitForStop, emptyMap())

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 wait for pending 𝕎 handleEvent(ApplicationStarted) on active view`(
        @LongForgery(0) duration: Long
    ) {
        // Given
        val eventTime = Time()
        fakeEvent = RumRawEvent.ApplicationStarted(eventTime, duration)
        testedScope.activeActionScope = null
        testedScope.pendingActionCount = 0

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Action 𝕎 handleEvent(ActionDropped) on active view`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.pendingActionCount = pending
        fakeEvent = RumRawEvent.ActionDropped(testedScope.viewId)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(pending - 1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Action 𝕎 handleEvent(ActionDropped) on active view {viewId changed}`(
        @LongForgery(1) pending: Long,
        @Forgery fakeNewViewId: UUID
    ) {
        // Given
        testedScope.pendingActionCount = pending
        fakeEvent = RumRawEvent.ActionDropped(testedScope.viewId)
        testedScope.viewId = fakeNewViewId.toString()

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(pending - 1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Action 𝕎 handleEvent(ActionDropped) on stopped view`() {
        // Given
        testedScope.pendingActionCount = 1
        fakeEvent = RumRawEvent.ActionDropped(testedScope.viewId)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 decrease pending Action 𝕎 handleEvent(ActionDropped) on stopped view {viewId changed}`(
        @Forgery fakeNewViewId: UUID
    ) {
        // Given
        testedScope.pendingActionCount = 1
        fakeEvent = RumRawEvent.ActionDropped(testedScope.viewId)
        testedScope.stopped = true
        testedScope.viewId = fakeNewViewId.toString()

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ActionDropped) on active view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingActionCount = pending
        fakeEvent = RumRawEvent.ActionDropped(viewId)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ActionDropped) on stopped view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingActionCount = pending
        fakeEvent = RumRawEvent.ActionDropped(viewId)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingActionCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Resource

    @Test
    fun `𝕄 create ResourceScope 𝕎 handleEvent(StartResource)`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String,
        forge: Forge
    ) {
        // Given
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)

        // When
        val fakeEvent = RumRawEvent.StartResource(key, url, method, attributes)
        val result = testedScope.handleEvent(
            fakeEvent,
            mockWriter
        )

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeResourceScopes).isNotEmpty()
        val entry = testedScope.activeResourceScopes.entries.first()
        assertThat(entry.key).isEqualTo(key)
        assertThat(entry.value).isInstanceOf(RumResourceScope::class.java)
        val resourceScope = entry.value as RumResourceScope
        assertThat(resourceScope.parentScope).isSameAs(testedScope)
        assertThat(resourceScope.attributes).containsAllEntriesOf(attributes)
        assertThat(resourceScope.key).isSameAs(key)
        assertThat(resourceScope.url).isEqualTo(url)
        assertThat(resourceScope.method).isSameAs(method)
        assertThat(resourceScope.eventTimestamp)
            .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
        assertThat(resourceScope.firstPartyHostHeaderTypeResolver).isSameAs(mockResolver)
        assertThat(resourceScope.sampleRate).isCloseTo(fakeSampleRate, Assertions.offset(0.001f))
    }

    @Test
    fun `𝕄 create ResourceScope with active actionId 𝕎 handleEvent(StartResource)`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.StartResource(key, url, method, attributes)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockActionScope).handleEvent(fakeEvent, mockWriter)
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeResourceScopes).isNotEmpty()
        val entry = testedScope.activeResourceScopes.entries.first()
        assertThat(entry.key).isEqualTo(key)
        val resourceScope = entry.value as RumResourceScope
        assertThat(resourceScope.parentScope).isSameAs(testedScope)
        assertThat(resourceScope.attributes).containsAllEntriesOf(attributes)
        assertThat(resourceScope.key).isSameAs(key)
        assertThat(resourceScope.url).isEqualTo(url)
        assertThat(resourceScope.method).isSameAs(method)
        assertThat(resourceScope.eventTimestamp)
            .isEqualTo(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
        assertThat(resourceScope.firstPartyHostHeaderTypeResolver).isSameAs(mockResolver)
        assertThat(resourceScope.sampleRate).isCloseTo(fakeSampleRate, Assertions.offset(0.001f))
    }

    @Test
    fun `𝕄 send event to children ResourceScopes 𝕎 handleEvent(StartView) on active view`(
        @StringForgery key: String
    ) {
        // Given
        testedScope.activeResourceScopes[key] = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event to children ResourceScopes 𝕎 handleEvent(StartView) on stopped view`(
        @StringForgery key: String
    ) {
        // Given
        testedScope.stopped = true
        testedScope.activeResourceScopes[key] = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn mockChildScope

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 remove child ResourceScope 𝕎 handleEvent() returns null`(
        @StringForgery key: String
    ) {
        // Given
        testedScope.activeResourceScopes[key] = mockChildScope
        whenever(mockChildScope.handleEvent(fakeEvent, mockWriter)) doReturn null

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(mockChildScope).handleEvent(fakeEvent, mockWriter)
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
        assertThat(testedScope.activeResourceScopes).isEmpty()
    }

    @Test
    fun `𝕄 wait for pending Resource 𝕎 handleEvent(StartResource) on active view`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+") url: String
    ) {
        // Given
        testedScope.pendingResourceCount = 0
        fakeEvent = RumRawEvent.StartResource(key, url, method, emptyMap())

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingResourceCount).isEqualTo(1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Resource 𝕎 handleEvent(ResourceDropped) on active view`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.pendingResourceCount = pending
        fakeEvent = RumRawEvent.ResourceDropped(testedScope.viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending - 1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Resource 𝕎 handleEvent(ResourceDropped) on active view {viewId changed}`(
        @LongForgery(1) pending: Long,
        @Forgery fakeNewViewId: UUID
    ) {
        // Given
        testedScope.pendingResourceCount = pending
        fakeEvent = RumRawEvent.ResourceDropped(testedScope.viewId)
        testedScope.viewId = fakeNewViewId.toString()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending - 1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Resource 𝕎 handleEvent(ResourceDropped) on stopped view`() {
        // Given
        testedScope.pendingResourceCount = 1
        fakeEvent = RumRawEvent.ResourceDropped(testedScope.viewId)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingResourceCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 decrease pending Resource 𝕎 handleEvent(ResourceDropped) on stopped view {viewId changed}`(
        @Forgery fakeNewViewId: UUID
    ) {
        // Given
        testedScope.pendingResourceCount = 1
        fakeEvent = RumRawEvent.ResourceDropped(testedScope.viewId)
        testedScope.stopped = true
        testedScope.viewId = fakeNewViewId.toString()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingResourceCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ResourceDropped) on active view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingResourceCount = pending
        fakeEvent = RumRawEvent.ResourceDropped(viewId)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ResourceDropped) on stopped view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingResourceCount = pending
        fakeEvent = RumRawEvent.ResourceDropped(viewId)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingResourceCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 convert pending resource to error 𝕎 handleEvent() {resource stopped by error}`(
        @LongForgery(1) pendingResources: Long,
        @LongForgery(min = 0, max = Long.MAX_VALUE - 1) pendingErrors: Long,
        forge: Forge
    ) {
        // Given
        testedScope.pendingErrorCount = pendingErrors
        testedScope.pendingResourceCount = pendingResources
        val fakeEvent = forge.stopResourceWithErrorEvent()
        testedScope.activeResourceScopes[fakeEvent.key] = mock()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingResourceCount).isEqualTo(pendingResources - 1)
        assertThat(testedScope.pendingErrorCount).isEqualTo(pendingErrors + 1)
    }

    @Test
    fun `𝕄 convert pending resource to error 𝕎 handleEvent() {resource stopped by error with stacktrace}`(
        @LongForgery(1) pendingResources: Long,
        @LongForgery(min = 0, max = Long.MAX_VALUE - 1) pendingErrors: Long,
        forge: Forge
    ) {
        // Given
        testedScope.pendingErrorCount = pendingErrors
        testedScope.pendingResourceCount = pendingResources
        val fakeEvent = forge.stopResourceWithStacktraceEvent()
        testedScope.activeResourceScopes[fakeEvent.key] = mock()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingResourceCount).isEqualTo(pendingResources - 1)
        assertThat(testedScope.pendingErrorCount).isEqualTo(pendingErrors + 1)
    }

    // endregion

    // region Error

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) on active view`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event with synthetics info 𝕎 handleEvent(AddError) on active view`(
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String,
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSyntheticsSession()
                    hasSyntheticsTest(fakeTestId, fakeResultId)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) on active view {throwable_message == null}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        val throwable = RuntimeException()
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) on active view {throwable is ANR}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        val throwable = ANRException(Thread.currentThread())
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.ANR)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) on active view {throwable_message == blank}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery(StringForgeryType.WHITESPACE) blankMessage: String,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        val throwable = RuntimeException(blankMessage)
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) on active view {message = throwable_message}`(
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val throwableMessage = throwable.message
        check(!throwableMessage.isNullOrBlank()) {
            "Expected throwable to have a non null, non blank message"
        }
        fakeEvent = RumRawEvent.AddError(
            throwableMessage,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(throwableMessage)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasStackTrace(stacktrace)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 AddError {throwable=null}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable = null,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(stacktrace)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 AddError {stacktrace=null}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes,
            sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) {throwable=null, stacktrace=null, fatal=false}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable = null,
            stacktrace = null,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes,
            sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasErrorSource(source)
                    hasErrorCategory(null)
                    hasStackTrace(null)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(null)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) {throwable=null, stacktrace=null, fatal=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery sourceType: RumErrorSourceType,
        @Forgery threads: List<ThreadDump>,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable = null,
            stacktrace = null,
            isFatal = true,
            threads = threads,
            attributes = attributes,
            sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture())
            assertThat(firstValue as ErrorEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(message)
                    hasErrorSource(source)
                    hasStackTrace(null)
                    isCrash(true)
                    hasThreads(threads)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(null)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasErrorCategory(null)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                }
            assertThat(lastValue as ViewEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) on active view { error fingerprint attribute }`(
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        @StringForgery fingerprint: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val mockAttributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val fullAttributes = mockAttributes.toMutableMap().apply {
            put(RumAttributes.ERROR_FINGERPRINT, fingerprint)
        }
        val throwableMessage = throwable.message
        check(!throwableMessage.isNullOrBlank()) {
            "Expected throwable to have a non null, non blank message"
        }
        fakeEvent = RumRawEvent.AddError(
            throwableMessage,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = fullAttributes
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(throwableMessage)
                    hasErrorSource(source)
                    hasStackTrace(stacktrace)
                    hasErrorFingerprint(fingerprint)
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(mockAttributes)
                    hasSource(fakeSourceErrorEvent)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event with global attributes 𝕎 handleEvent(AddError)`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = false,
            threads = emptyList(),
            attributes = emptyMap(),
            sourceType = sourceType
        )
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn attributes

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) {isFatal=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        @Forgery threads: List<ThreadDump>,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = true,
            threads = threads,
            attributes = attributes,
            sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture())
            assertThat(firstValue as ErrorEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(true)
                    hasThreads(threads)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                }
            assertThat(lastValue as ViewEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) {internal is_crash=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val attributesWithCrash = attributes.toMutableMap()
        attributesWithCrash["_dd.error.is_crash"] = true
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = false,
            // empty list, because _dd.error.is_crash is coming from Cross Platform, this not a native crash
            threads = emptyList(),
            attributes = attributesWithCrash,
            sourceType = sourceType
        )

        // Sending a second crash should not trigger a view update
        val fakeNativeCrashEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = true,
            threads = emptyList(),
            attributes = attributes,
            sourceType = sourceType
        )

        // When
        val result = testedScope
            .handleEvent(fakeEvent, mockWriter)
            ?.handleEvent(fakeNativeCrashEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture())
            assertThat(firstValue as ErrorEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(true)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                }
            assertThat(lastValue as ViewEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) {internal is_crash=false}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        val attributesWithCrash = attributes.toMutableMap()
        attributesWithCrash["_dd.error.is_crash"] = false
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = false,
            threads = emptyList(),
            attributes = attributesWithCrash,
            sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<Any> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(firstValue as ErrorEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddError) {custom error type}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery errorType: String,
        @Forgery sourceType: RumErrorSourceType,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes,
            type = errorType,
            sourceType = sourceType
        )

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(false)
                    hasNoThreads()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(errorType)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event with global attributes 𝕎 handleEvent(AddError) {isFatal=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @Forgery sourceType: RumErrorSourceType,
        @Forgery threads: List<ThreadDump>,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = true,
            threads = threads,
            attributes = emptyMap(),
            sourceType = sourceType
        )
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn attributes

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        val expectedMessage = "$message: ${throwable.message}"
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture())
            assertThat(firstValue as ErrorEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEvent.eventTime.timestamp))
                    hasMessage(expectedMessage)
                    hasErrorSource(source)
                    hasStackTrace(throwable.loggableStackTrace())
                    isCrash(true)
                    hasThreads(threads)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.key.name, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasErrorType(throwable.javaClass.canonicalName)
                    hasErrorSourceType(sourceType.toSchemaSourceType())
                    hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(attributes)
                    hasSource(fakeSourceErrorEvent)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                }
            assertThat(lastValue as ViewEvent)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(1)
                    hasCrashCount(1)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes + attributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(AddError) on stopped view {throwable}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @BoolForgery fatal: Boolean,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace = null,
            isFatal = fatal,
            threads = emptyList(),
            attributes = attributes
        )
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(AddError) on stopped view {stacktrace}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String,
        @BoolForgery fatal: Boolean,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable = null,
            stacktrace,
            fatal,
            threads = emptyList(),
            attributes = attributes
        )
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 wait for pending Error 𝕎 handleEvent(AddError) on active view {fatal=false}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String
    ) {
        // Given
        testedScope.pendingErrorCount = 0
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable = null,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = emptyMap()
        )

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 not wait for pending Error 𝕎 handleEvent(AddError) on active view {fatal=true}`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @StringForgery stacktrace: String
    ) {
        // Given
        testedScope.pendingErrorCount = 0
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable = null,
            stacktrace,
            isFatal = true,
            threads = emptyList(),
            attributes = emptyMap()
        )

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(0)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Error 𝕎 handleEvent(ErrorDropped) on active view`(
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.pendingErrorCount = pending
        fakeEvent = RumRawEvent.ErrorDropped(testedScope.viewId)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(pending - 1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Error 𝕎 handleEvent(ErrorDropped) on active view {viewId changed}`(
        @LongForgery(1) pending: Long,
        @Forgery fakeNewViewId: UUID
    ) {
        // Given
        testedScope.pendingErrorCount = pending
        fakeEvent = RumRawEvent.ErrorDropped(testedScope.viewId)
        testedScope.viewId = fakeNewViewId.toString()

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(pending - 1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Error 𝕎 handleEvent(ErrorDropped) on stopped view`() {
        // Given
        testedScope.pendingErrorCount = 1
        fakeEvent = RumRawEvent.ErrorDropped(testedScope.viewId)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 decrease pending Error 𝕎 handleEvent(ErrorDropped) on stopped view {viewId changed}`(
        @Forgery fakeNewViewId: UUID
    ) {
        // Given
        testedScope.pendingErrorCount = 1
        fakeEvent = RumRawEvent.ErrorDropped(testedScope.viewId)
        testedScope.stopped = true
        testedScope.viewId = fakeNewViewId.toString()

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ErrorDropped) on active view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingErrorCount = pending
        fakeEvent = RumRawEvent.ErrorDropped(viewId)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(ErrorDropped) on stopped view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingErrorCount = pending
        fakeEvent = RumRawEvent.ErrorDropped(viewId)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingErrorCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Long Task

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddLongTask) on active view {not frozen}`(
        @LongForgery(0L, 700_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = null
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(
                        resolveExpectedTimestamp(fakeEvent.eventTime.timestamp) - durationMs
                    )
                    hasDuration(durationNs)
                    isFrozenFrame(false)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasReplay(fakeHasReplay)
                    hasSource(fakeSourceLongTaskEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toLongTaskSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event 𝕎 handleEvent(AddLongTask) on active view {frozen}`(
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = null
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(
                        resolveExpectedTimestamp(fakeEvent.eventTime.timestamp) - durationMs
                    )
                    hasDuration(durationNs)
                    isFrozenFrame(true)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasReplay(fakeHasReplay)
                    hasSource(fakeSourceLongTaskEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toLongTaskSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event synthetics info 𝕎 handleEvent(AddLongTask) on active view {not frozen}`(
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String,
        @LongForgery(0L, 700_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = null
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(
                        resolveExpectedTimestamp(fakeEvent.eventTime.timestamp) - durationMs
                    )
                    hasDuration(durationNs)
                    isFrozenFrame(false)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasSyntheticsSession()
                    hasSyntheticsTest(fakeTestId, fakeResultId)
                    hasReplay(fakeHasReplay)
                    hasSource(fakeSourceLongTaskEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toLongTaskSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event with synthetics info 𝕎 handleEvent(AddLongTask) on active view {frozen}`(
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String,
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = null
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)
        fakeParentContext = fakeParentContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(
                        resolveExpectedTimestamp(fakeEvent.eventTime.timestamp) - durationMs
                    )
                    hasDuration(durationNs)
                    isFrozenFrame(true)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasSyntheticsSession()
                    hasSyntheticsTest(fakeTestId, fakeResultId)
                    hasReplay(fakeHasReplay)
                    hasSource(fakeSourceLongTaskEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toLongTaskSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event with global attributes 𝕎 handleEvent(AddLongTask) {not frozen}`(
        @LongForgery(0L, 700_000_000L) durationNs: Long,
        @StringForgery target: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val fakeLongTaskEvent = RumRawEvent.AddLongTask(durationNs, target)
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn attributes
        val expectedAttributes = attributes + mapOf(
            RumAttributes.LONG_TASK_TARGET to fakeLongTaskEvent.target
        )

        // When
        val result = testedScope.handleEvent(fakeLongTaskEvent, mockWriter)

        // Then
        val expectedTimestamp =
            resolveExpectedTimestamp(fakeLongTaskEvent.eventTime.timestamp) - durationMs
        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(expectedTimestamp)
                    hasDuration(durationNs)
                    isFrozenFrame(false)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceLongTaskEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toLongTaskSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send event with global attributes 𝕎 handleEvent(AddLongTask) {frozen}`(
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val fakeLongTaskEvent = RumRawEvent.AddLongTask(durationNs, target)
        val attributes = forgeGlobalAttributes(forge, fakeAttributes)
        val durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn attributes
        val expectedAttributes = attributes + mapOf(
            RumAttributes.LONG_TASK_TARGET to fakeLongTaskEvent.target
        )

        // When
        val result = testedScope.handleEvent(fakeLongTaskEvent, mockWriter)

        // Then
        argumentCaptor<LongTaskEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(firstValue)
                .apply {
                    hasTimestamp(
                        resolveExpectedTimestamp(fakeLongTaskEvent.eventTime.timestamp) - durationMs
                    )
                    hasDuration(durationNs)
                    isFrozenFrame(true)
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasView(testedScope.viewId, testedScope.url)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasActionId(fakeActionId)
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasReplay(fakeHasReplay)
                    containsExactlyContextAttributes(expectedAttributes)
                    hasSource(fakeSourceLongTaskEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toLongTaskSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(AddLongTask) on stopped view`(
        @LongForgery(0) durationNs: Long,
        @StringForgery target: String,
        @LongForgery(1) pending: Long
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        testedScope.pendingLongTaskCount = pending
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verifyNoInteractions(mockWriter)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 wait for pending Long Task 𝕎 handleEvent(AddLongTask) on active view {not frozen}`(
        @LongForgery(0L, 700_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.pendingLongTaskCount = 0
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(0)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 wait for pending LT and FF 𝕎 handleEvent(AddLongTask) on active view {frozen}`(
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.pendingLongTaskCount = 0
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Long Task 𝕎 handleEvent(LongTaskDropped) on active view {not frozen}`(
        @LongForgery(1) pendingLongTask: Long,
        @LongForgery(1) pendingFrozenFrame: Long
    ) {
        // Given
        testedScope.pendingLongTaskCount = pendingLongTask
        testedScope.pendingFrozenFrameCount = pendingFrozenFrame
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId, false)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pendingLongTask - 1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(pendingFrozenFrame)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending Long Task 𝕎 handleEvent(LongTaskDropped) on active view {not frozen, viewId changed}`(
        @LongForgery(1) pendingLongTask: Long,
        @LongForgery(1) pendingFrozenFrame: Long,
        @Forgery fakeNewViewId: UUID
    ) {
        // Given
        testedScope.pendingLongTaskCount = pendingLongTask
        testedScope.pendingFrozenFrameCount = pendingFrozenFrame
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId, false)
        testedScope.viewId = fakeNewViewId.toString()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pendingLongTask - 1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(pendingFrozenFrame)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending LT and FF 𝕎 handleEvent(LongTaskDropped) on active view {frozen}`(
        @LongForgery(1) pendingLongTask: Long,
        @LongForgery(1) pendingFrozenFrame: Long
    ) {
        // Given
        testedScope.pendingLongTaskCount = pendingLongTask
        testedScope.pendingFrozenFrameCount = pendingFrozenFrame
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId, true)

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pendingLongTask - 1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(pendingFrozenFrame - 1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending LT and FF 𝕎 handleEvent(LongTaskDropped) on active view {frozen, viewId changed}`(
        @LongForgery(1) pendingLongTask: Long,
        @LongForgery(1) pendingFrozenFrame: Long,
        @Forgery fakeNewViewId: UUID
    ) {
        // Given
        testedScope.pendingLongTaskCount = pendingLongTask
        testedScope.pendingFrozenFrameCount = pendingFrozenFrame
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId, true)
        testedScope.viewId = fakeNewViewId.toString()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pendingLongTask - 1)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(pendingFrozenFrame - 1)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 decrease pending LT 𝕎 handleEvent(LongTaskDropped) on stopped view {not frozen}`() {
        // Given
        testedScope.pendingLongTaskCount = 1
        testedScope.pendingFrozenFrameCount = 0
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId, false)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(0)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 decrease pending LT 𝕎 handleEvent(LongTaskDropped) on stopped view {not frozen, viewId changed}`(
        @Forgery fakeNewViewId: UUID
    ) {
        // Given
        testedScope.pendingLongTaskCount = 1
        testedScope.pendingFrozenFrameCount = 0
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId, false)
        testedScope.stopped = true
        testedScope.viewId = fakeNewViewId.toString()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(0)
        assertThat(testedScope.pendingFrozenFrameCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 decrease pending LT and FF 𝕎 handleEvent(LongTaskDropped) on stopped view {frozen}`() {
        // Given
        testedScope.pendingLongTaskCount = 1
        testedScope.pendingFrozenFrameCount = 1
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId, true)
        testedScope.stopped = true

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(0)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 decrease pending LT and FF 𝕎 handleEvent(LongTaskDropped) on stopped view {frozen, viewId changed}`(
        @Forgery fakeNewViewId: UUID
    ) {
        // Given
        testedScope.pendingLongTaskCount = 1
        testedScope.pendingFrozenFrameCount = 1
        fakeEvent = RumRawEvent.LongTaskDropped(testedScope.viewId, true)
        testedScope.stopped = true
        testedScope.viewId = fakeNewViewId.toString()

        // When
        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(0)
        assertThat(testedScope.pendingLongTaskCount).isEqualTo(0)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(LongTaskDropped) on active view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @BoolForgery isFrozenFrame: Boolean,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingLongTaskCount = pending
        fakeEvent = RumRawEvent.LongTaskDropped(viewId, isFrozenFrame)

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleEvent(LongTaskDropped) on stopped view {unknown viewId}`(
        @LongForgery(1) pending: Long,
        @BoolForgery isFrozenFrame: Boolean,
        @Forgery viewUuid: UUID
    ) {
        // Given
        val viewId = viewUuid.toString()
        assumeTrue(viewId != testedScope.viewId)
        testedScope.pendingLongTaskCount = pending
        fakeEvent = RumRawEvent.LongTaskDropped(viewId, isFrozenFrame)
        testedScope.stopped = true

        val result = testedScope.handleEvent(fakeEvent, mockWriter)

        assertThat(testedScope.pendingLongTaskCount).isEqualTo(pending)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Loading Time

    @Test
    fun `𝕄 send event with custom timing 𝕎 handleEvent(AddCustomTiming) on active view`(
        forge: Forge
    ) {
        // Given
        val fakeTimingKey = forge.anAlphabeticalString()

        // When
        testedScope.handleEvent(
            RumRawEvent.AddCustomTiming(fakeTimingKey),
            mockWriter
        )
        val customTimingEstimatedDuration = System.nanoTime() - fakeEventTime.nanoTime

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasCustomTimings(mapOf(fakeTimingKey to customTimingEstimatedDuration))
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `𝕄 send event with custom timings 𝕎 handleEvent(AddCustomTiming) called multiple times`(
        forge: Forge
    ) {
        // Given
        val fakeTimingKey1 = forge.anAlphabeticalString()
        val fakeTimingKey2 = forge.anAlphabeticalString()

        // When
        testedScope.handleEvent(
            RumRawEvent.AddCustomTiming(fakeTimingKey1),
            mockWriter
        )
        val customTiming1EstimatedDuration = System.nanoTime() - fakeEventTime.nanoTime
        testedScope.handleEvent(
            RumRawEvent.AddCustomTiming(fakeTimingKey2),
            mockWriter
        )
        val customTiming2EstimatedDuration = System.nanoTime() - fakeEventTime.nanoTime

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture())
            assertThat(firstValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasLoadingType(null)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasCustomTimings(mapOf(fakeTimingKey1 to customTiming1EstimatedDuration))
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSampleRate(fakeSampleRate)
                }
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasLoadingTime(null)
                    hasLoadingType(null)
                    hasVersion(3)
                    hasErrorCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasCustomTimings(
                        mapOf(
                            fakeTimingKey1 to customTiming1EstimatedDuration,
                            fakeTimingKey2 to customTiming2EstimatedDuration
                        )
                    )
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `𝕄 not add custom timing 𝕎 handleEvent(AddCustomTiming) on stopped view`(
        forge: Forge
    ) {
        // Given
        testedScope.stopped = true
        val fakeTimingKey = forge.anAlphabeticalString()

        // When
        testedScope.handleEvent(
            RumRawEvent.AddCustomTiming(fakeTimingKey),
            mockWriter
        )

        // Then
        assertThat(testedScope.customTimings).isEmpty()
        verifyNoInteractions(mockWriter)
    }

    // endregion

    // region Vitals

    @Test
    fun `𝕄 send View update 𝕎 onVitalUpdate()+handleEvent(KeepAlive) {CPU}`(
        forge: Forge
    ) {
        // Given
        // cpu ticks should be received in ascending order
        val cpuTicks = forge.aList { aLong(1L, 65536L).toDouble() }.sorted()
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockCpuVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        cpuTicks.forEachIndexed { index, value ->
            listener.onVitalUpdate(VitalInfo(index + 1, 0.0, value, value / 2.0))
        }
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        val expectedTotal = if (cpuTicks.size > 1) {
            cpuTicks.last() - cpuTicks.first()
        } else {
            // we need to have at least 2 ticks to submit "ticks on the view" metric
            null
        }
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(expectedTotal)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send View update 𝕎 onVitalUpdate()+handleEvent(KeepAlive) {CPU short timespan}`(
        @DoubleForgery(1024.0, 65536.0) cpuTicks: Double
    ) {
        // Given
        // cpu ticks should be received in ascending order
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockCpuVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        listener.onVitalUpdate(VitalInfo(1, 0.0, 0.0, 0.0))
        listener.onVitalUpdate(VitalInfo(1, 0.0, cpuTicks, cpuTicks / 2.0))
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(fakeEventTime),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(cpuTicks)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send View update 𝕎 onVitalUpdate()+handleEvent(KeepAlive) {Memory}`(
        forge: Forge
    ) {
        // Given
        val vitals = forge.aList { getForgery<VitalInfo>() }
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockMemoryVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        vitals.forEach { listener.onVitalUpdate(it) }
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(vitals.last().meanValue, vitals.last().maxValue)
                    hasRefreshRateMetric(null, null)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send View update 𝕎 onVitalUpdate()+handleEvent(KeepAlive) {high frameRate}`(
        forge: Forge
    ) {
        // Given
        val frameRates = forge.aList { aDouble(55.0, 60.0) }.sorted()
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockFrameRateVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        var sum = 0.0
        var min = 60.0
        var max = 0.0
        var count = 0
        frameRates.forEach { value ->
            count++
            sum += value
            min = min(min, value)
            max = max(max, value)
            listener.onVitalUpdate(VitalInfo(count, min, max, sum / count))
        }
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(sum / frameRates.size, min)
                    isActive(true)
                    isSlowRendered(false)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send View update 𝕎 onVitalUpdate()+handleEvent(KeepAlive) {low frameRate}`(
        forge: Forge
    ) {
        // Given
        val frameRates = forge.aList { aDouble(10.0, 55.0) }.sorted()
        val listenerCaptor = argumentCaptor<VitalListener> {
            verify(mockFrameRateVitalMonitor).register(capture())
        }
        val listener = listenerCaptor.firstValue

        // When
        var sum = 0.0
        var min = 60.0
        var max = 0.0
        var count = 0
        frameRates.forEach { value ->
            count++
            sum += value
            min = min(min, value)
            max = max(max, value)
            listener.onVitalUpdate(VitalInfo(count, min, max, sum / count))
        }
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasTimestamp(resolveExpectedTimestamp(fakeEventTime.timestamp))
                    hasName(fakeKey.name)
                    hasUrl(fakeUrl)
                    hasDurationGreaterThan(1)
                    hasVersion(2)
                    hasErrorCount(0)
                    hasCrashCount(0)
                    hasResourceCount(0)
                    hasActionCount(0)
                    hasFrustrationCount(0)
                    hasLongTaskCount(0)
                    hasFrozenFrameCount(0)
                    hasCpuMetric(null)
                    hasMemoryMetric(null, null)
                    hasRefreshRateMetric(sum / frameRates.size, min)
                    isActive(true)
                    isSlowRendered(true)
                    hasNoCustomTimings()
                    hasUserInfo(fakeDatadogContext.userInfo)
                    hasViewId(testedScope.viewId)
                    hasApplicationId(fakeParentContext.applicationId)
                    hasSessionId(fakeParentContext.sessionId)
                    hasUserSession()
                    hasNoSyntheticsTest()
                    hasLiteSessionPlan()
                    hasStartReason(fakeParentContext.sessionStartReason)
                    hasReplay(fakeHasReplay)
                    hasReplayStats(fakeReplayStats)
                    containsExactlyContextAttributes(fakeAttributes)
                    hasSource(fakeSourceViewEvent)
                    hasDeviceInfo(
                        fakeDatadogContext.deviceInfo.deviceName,
                        fakeDatadogContext.deviceInfo.deviceModel,
                        fakeDatadogContext.deviceInfo.deviceBrand,
                        fakeDatadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        fakeDatadogContext.deviceInfo.architecture
                    )
                    hasOsInfo(
                        fakeDatadogContext.deviceInfo.osName,
                        fakeDatadogContext.deviceInfo.osVersion,
                        fakeDatadogContext.deviceInfo.osMajorVersion
                    )
                    hasConnectivityInfo(fakeDatadogContext.networkInfo)
                    hasServiceName(fakeDatadogContext.service)
                    hasVersion(fakeDatadogContext.version)
                    hasSessionActive(fakeParentContext.isSessionActive)
                    hasSampleRate(fakeSampleRate)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Cross-platform performance metrics

    @Test
    fun `𝕄 send update 𝕎 handleEvent(UpdatePerformanceMetric+KeepAlive) { FlutterBuildTime }`(
        forge: Forge
    ) {
        // GIVEN
        val value = forge.aDouble()

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.UpdatePerformanceMetric(
                metric = RumPerformanceMetric.FLUTTER_BUILD_TIME,
                value = value
            ),
            mockWriter
        )
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasFlutterBuildTime(ViewEvent.FlutterBuildTime(value, value, value, null))
                    hasFlutterRasterTime(null)
                    hasJsRefreshRate(null)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send update 𝕎 handleEvent(UpdatePerformanceMetric+KeepAlive) { FlutterRasterTime }`(
        forge: Forge
    ) {
        // GIVEN
        val value = forge.aDouble()

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.UpdatePerformanceMetric(
                metric = RumPerformanceMetric.FLUTTER_RASTER_TIME,
                value = value
            ),
            mockWriter
        )
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasFlutterBuildTime(null)
                    hasFlutterRasterTime(ViewEvent.FlutterBuildTime(value, value, value, null))
                    hasJsRefreshRate(null)
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send View update 𝕎 handleEvent(UpdatePerformanceMetric+KeepAlive) { JsRefreshRate }`(
        forge: Forge
    ) {
        // GIVEN
        val value = forge.aPositiveDouble(true)
        val frameRate = TimeUnit.SECONDS.toNanos(1) / value

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.UpdatePerformanceMetric(
                metric = RumPerformanceMetric.JS_FRAME_TIME,
                value = value
            ),
            mockWriter
        )
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasFlutterBuildTime(null)
                    hasFlutterRasterTime(null)
                    hasJsRefreshRate(
                        ViewEvent.FlutterBuildTime(
                            frameRate,
                            frameRate,
                            frameRate,
                            null
                        )
                    )
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    @Test
    fun `𝕄 send View update with all values 𝕎 handleEvent(UpdatePerformanceMetric+KeepAlive)`(
        forge: Forge
    ) {
        // GIVEN
        val flutterBuildTimes = DoubleArray(5) { forge.aDouble() }
        val flutterRasterTimes = DoubleArray(5) { forge.aDouble() }
        val jsFrameTimes = DoubleArray(5) { forge.aPositiveDouble(true) }

        // WHEN
        for (i in 0..4) {
            testedScope.handleEvent(
                RumRawEvent.UpdatePerformanceMetric(
                    metric = RumPerformanceMetric.FLUTTER_BUILD_TIME,
                    value = flutterBuildTimes[i]
                ),
                mockWriter
            )
            testedScope.handleEvent(
                RumRawEvent.UpdatePerformanceMetric(
                    metric = RumPerformanceMetric.FLUTTER_RASTER_TIME,
                    value = flutterRasterTimes[i]
                ),
                mockWriter
            )
            testedScope.handleEvent(
                RumRawEvent.UpdatePerformanceMetric(
                    metric = RumPerformanceMetric.JS_FRAME_TIME,
                    value = jsFrameTimes[i]
                ),
                mockWriter
            )
        }
        val result = testedScope.handleEvent(
            RumRawEvent.KeepAlive(),
            mockWriter
        )

        // THEN
        val flutterBuildTimeStats = Arrays.stream(flutterBuildTimes).summaryStatistics()
        val flutterRasterTimeStats = Arrays.stream(flutterRasterTimes).summaryStatistics()
        val jsFrameTimeStats = Arrays.stream(jsFrameTimes).summaryStatistics()
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasFlutterBuildTime(
                        ViewEvent.FlutterBuildTime(
                            min = flutterBuildTimeStats.min,
                            max = flutterBuildTimeStats.max,
                            average = flutterBuildTimeStats.average
                        )
                    )
                    hasFlutterRasterTime(
                        ViewEvent.FlutterBuildTime(
                            min = flutterRasterTimeStats.min,
                            max = flutterRasterTimeStats.max,
                            average = flutterRasterTimeStats.average
                        )
                    )
                    hasJsRefreshRate(
                        ViewEvent.FlutterBuildTime(
                            min = TimeUnit.SECONDS.toNanos(1) / jsFrameTimeStats.max,
                            max = TimeUnit.SECONDS.toNanos(1) / jsFrameTimeStats.min,
                            average = TimeUnit.SECONDS.toNanos(1) / jsFrameTimeStats.average
                        )
                    )
                }
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isSameAs(testedScope)
    }

    // endregion

    // region Feature Flags

    @Test
    fun `M send event W handleEvent(AddFeatureFlagEvaluation) on active view`(
        @StringForgery flagName: String,
        @StringForgery flagValue: String
    ) {
        // WHEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluation(
                name = flagName,
                value = flagValue
            ),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue).hasFeatureFlag(flagName, flagValue)
        }
    }

    @Test
    fun `M not add feature flag W handleEvent(AddFeatureFlagEvaluation) on stopped view`(
        @StringForgery flagName: String,
        @StringForgery flagValue: String
    ) {
        // GIVEN
        testedScope.stopped = true

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluation(
                name = flagName,
                value = flagValue
            ),
            mockWriter
        )

        // THEN
        assertThat(testedScope.featureFlags).isEmpty()
        verifyNoInteractions(mockWriter)
    }

    @Test
    fun `M modify flag W handleEvent(AddFeatureFlagEvaluation) on active view { existing feature flag }`(
        @StringForgery flagName: String,
        @BoolForgery oldFlagValue: Boolean,
        @StringForgery flagValue: String
    ) {
        // GIVEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluation(
                name = flagName,
                value = oldFlagValue
            ),
            mockWriter
        )

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluation(
                name = flagName,
                value = flagValue
            ),
            mockWriter
        )

        // THEN
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue).hasFeatureFlag(flagName, flagValue)
        }
    }

    @Test
    fun `M send flags on ErrorEvent W handleEvent(AddError) on active view { existing feature flags }`(
        forge: Forge,
        @StringForgery flagName: String,
        @StringForgery flagValue: String
    ) {
        // GIVEN
        testedScope.handleEvent(
            RumRawEvent.AddFeatureFlagEvaluation(
                name = flagName,
                value = flagValue
            ),
            mockWriter
        )

        // WHEN
        testedScope.handleEvent(
            RumRawEvent.AddError(
                forge.anAlphabeticalString(),
                forge.aValueFrom(RumErrorSource::class.java),
                throwable = null,
                stacktrace = null,
                isFatal = false,
                threads = emptyList(),
                attributes = emptyMap()
            ),
            mockWriter
        )

        // THEN
        argumentCaptor<Any> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue as ErrorEvent).hasFeatureFlag(flagName, flagValue)
        }
    }

    // endregion

    // region Stopping Sessions

    @Test
    fun `M set view to inactive and send update W handleEvent { StopSession }`() {
        // Given
        whenever(mockParentScope.getRumContext())
            .doReturn(fakeParentContext.copy(isSessionActive = false))

        // When
        testedScope.handleEvent(
            RumRawEvent.StopSession(),
            mockWriter
        )

        // Then
        assertThat(testedScope.isActive()).isFalse()

        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue)
                .apply {
                    hasSessionActive(false)
                }
        }
    }

    // endregion

    // region write notification

    @Test
    fun `𝕄 notify about success 𝕎 handleEvent(AddError+non-fatal) { write succeeded }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventSent(testedScope.viewId, StorageEvent.Error)
    }

    @Test
    fun `𝕄 notify about error 𝕎 handleEvent(AddError+non-fatal) { write failed }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )
        whenever(mockWriter.write(eq(mockEventBatchWriter), isA<ErrorEvent>())) doReturn false

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(testedScope.viewId, StorageEvent.Error)
    }

    @Test
    fun `𝕄 notify about error 𝕎 handleEvent(AddError+non-fatal) { write throws }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = false,
            threads = emptyList(),
            attributes = attributes
        )
        whenever(
            mockWriter.write(eq(mockEventBatchWriter), isA<ErrorEvent>())
        ) doThrow forge.anException()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(testedScope.viewId, StorageEvent.Error)
    }

    @Test
    fun `𝕄 not notify about success 𝕎 handleEvent(AddError+fatal) { write succeeded }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = true,
            threads = emptyList(),
            attributes = attributes
        )

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor, never())
            .eventSent(testedScope.viewId, StorageEvent.Error)
    }

    @Test
    fun `𝕄 not notify about error 𝕎 handleEvent(AddError+fatal) { write failed }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = true,
            threads = emptyList(),
            attributes = attributes
        )
        whenever(mockWriter.write(eq(mockEventBatchWriter), isA<ErrorEvent>())) doReturn false

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor, never())
            .eventDropped(testedScope.viewId, StorageEvent.Error)
    }

    @Test
    fun `𝕄 not notify about error 𝕎 handleEvent(AddError+fatal) { write throws }`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @Forgery throwable: Throwable,
        @StringForgery stacktrace: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        val attributes = forge.exhaustiveAttributes(excludedKeys = fakeAttributes.keys)
        fakeEvent = RumRawEvent.AddError(
            message,
            source,
            throwable,
            stacktrace,
            isFatal = true,
            threads = emptyList(),
            attributes = attributes
        )
        whenever(
            mockWriter.write(eq(mockEventBatchWriter), isA<ErrorEvent>())
        ) doThrow forge.anException()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor, never())
            .eventDropped(testedScope.viewId, StorageEvent.Error)
    }

    @Test
    fun `𝕄 notify about success 𝕎 handleEvent(ApplicationStarted) { write succeeded }`(
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.ApplicationStarted(
            eventTime = Time(),
            applicationStartupNanos = forge.aPositiveLong()
        )

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventSent(testedScope.viewId, StorageEvent.Action(0))
    }

    @Test
    fun `𝕄 notify about error 𝕎 handleEvent(ApplicationStarted) { write failed }`(
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.ApplicationStarted(
            eventTime = Time(),
            applicationStartupNanos = forge.aPositiveLong()
        )
        whenever(mockWriter.write(eq(mockEventBatchWriter), isA<ActionEvent>())) doReturn false

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(testedScope.viewId, StorageEvent.Action(0))
    }

    @Test
    fun `𝕄 notify about error 𝕎 handleEvent(ApplicationStarted) { write throws }`(
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.ApplicationStarted(
            eventTime = Time(),
            applicationStartupNanos = forge.aPositiveLong()
        )
        whenever(
            mockWriter.write(eq(mockEventBatchWriter), isA<ActionEvent>())
        ) doThrow forge.anException()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(testedScope.viewId, StorageEvent.Action(0))
    }

    @Test
    fun `𝕄 notify about success 𝕎 handleEvent(AddLongTask) { write succeeded }`(
        @LongForgery(250_000_000L, 700_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventSent(testedScope.viewId, StorageEvent.LongTask)
    }

    @Test
    fun `𝕄 notify about error 𝕎 handleEvent(AddLongTask) { write failed }`(
        @LongForgery(250_000_000L, 700_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        whenever(mockWriter.write(eq(mockEventBatchWriter), isA<LongTaskEvent>())) doReturn false

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(testedScope.viewId, StorageEvent.LongTask)
    }

    @Test
    fun `𝕄 notify about error 𝕎 handleEvent(AddLongTask) { write throws }`(
        @LongForgery(250_000_000L, 700_000_000L) durationNs: Long,
        @StringForgery target: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        whenever(
            mockWriter.write(eq(mockEventBatchWriter), isA<LongTaskEvent>())
        ) doThrow forge.anException()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(testedScope.viewId, StorageEvent.LongTask)
    }

    @Test
    fun `𝕄 notify about success 𝕎 handleEvent(AddLongTask, is frozen frame) { write succeeded }`(
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventSent(testedScope.viewId, StorageEvent.FrozenFrame)
    }

    @Test
    fun `𝕄 notify about error 𝕎 handleEvent(AddLongTask, is frozen frame) { write failed }`(
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        whenever(mockWriter.write(eq(mockEventBatchWriter), isA<LongTaskEvent>())) doReturn false

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(testedScope.viewId, StorageEvent.FrozenFrame)
    }

    @Test
    fun `𝕄 notify about error 𝕎 handleEvent(AddLongTask, is frozen frame) { write throws }`(
        @LongForgery(700_000_000L, 10_000_000_000L) durationNs: Long,
        @StringForgery target: String,
        forge: Forge
    ) {
        // Given
        testedScope.activeActionScope = mockActionScope
        fakeEvent = RumRawEvent.AddLongTask(durationNs, target)
        whenever(
            mockWriter.write(eq(mockEventBatchWriter), isA<LongTaskEvent>())
        ) doThrow forge.anException()

        // When
        testedScope.handleEvent(fakeEvent, mockWriter)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .eventDropped(testedScope.viewId, StorageEvent.FrozenFrame)
    }

    // endregion

    // region Misc

    @ParameterizedTest
    @MethodSource("brokenTimeRawEventData")
    fun `M update the duration to 1ns W handleEvent { computed duration less or equal to 0 }`(
        rawEventData: RumRawEventData
    ) {
        // Given
        testedScope = RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            rawEventData.viewKey,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockFeaturesContextResolver,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )

        // When
        testedScope.handleEvent(rawEventData.event, mockWriter)

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(firstValue)
                .apply {
                    hasDuration(1)
                }
        }
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            RumViewScope.NEGATIVE_DURATION_WARNING_MESSAGE.format(Locale.US, testedScope.key.name)
        )
    }

    // endregion

    // region Global Attributes

    @Test
    fun `𝕄 update the global attributes 𝕎 handleEvent(StopView)`(
        forge: Forge
    ) {
        // Given
        val fakeStopEventAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val excludedKeys = fakeAttributes.keys + fakeStopEventAttributes.keys
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }
        val fakeNewGlobalAttributes = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeNewGlobalAttributes)
        expectedAttributes.putAll(fakeStopEventAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturnConsecutively listOf(
            // one for initialization
            fakeGlobalAttributes,
            // second for event handling
            fakeNewGlobalAttributes
        )

        testedScope = RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, fakeStopEventAttributes),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue).containsExactlyContextAttributes(expectedAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 not update the global attributes 𝕎 handleEvent(StartView)`(
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val fakeNewGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeGlobalAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturnConsecutively listOf(
            // one for initialization
            fakeGlobalAttributes,
            // second for event handling
            fakeNewGlobalAttributes
        )

        testedScope = RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StartView(forge.getForgery(), emptyMap()),
            mockWriter
        )

        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue).containsExactlyContextAttributes(expectedAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 not update the global attributes 𝕎 handleEvent(Resource Sent) on new started view`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery url: String,
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val fakeNewGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeGlobalAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturnConsecutively listOf(
            // one for initialization
            fakeGlobalAttributes,
            // second one for when the resource is started
            fakeGlobalAttributes,
            // third one for when the resource scope init
            fakeGlobalAttributes,
            // third one when the new view was started
            fakeNewGlobalAttributes
        )

        testedScope = RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )
        testedScope.handleEvent(
            RumRawEvent.StartResource(key, url, method, emptyMap()),
            mockWriter
        )
        testedScope.handleEvent(
            RumRawEvent.StartView(forge.getForgery(), emptyMap()),
            mockWriter
        )
        // When
        testedScope.handleEvent(
            RumRawEvent.ResourceSent(testedScope.viewId),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue).containsExactlyContextAttributes(expectedAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `𝕄 not update the global attributes 𝕎 handleEvent(Action Sent) on new started view`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        forge: Forge
    ) {
        // Given
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val fakeNewGlobalAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeGlobalAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturnConsecutively listOf(
            // one for initialization
            fakeGlobalAttributes,
            // second one for when the action is started
            fakeGlobalAttributes,
            // third one for when the action scope init
            fakeGlobalAttributes,
            // third one new when the new view was started
            fakeNewGlobalAttributes
        )

        testedScope = RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )
        testedScope.handleEvent(
            RumRawEvent.StartAction(type, name, forge.aBool(), emptyMap()),
            mockWriter
        )
        testedScope.handleEvent(
            RumRawEvent.StartView(forge.getForgery(), emptyMap()),
            mockWriter
        )
        // When
        testedScope.handleEvent(
            RumRawEvent.ActionSent(testedScope.viewId, forge.anInt()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue).containsExactlyContextAttributes(expectedAttributes)
        }
    }

    @Test
    fun `𝕄 not update the global attributes 𝕎 handleEvent(Resource Sent) on stopped view`(
        @StringForgery key: String,
        @Forgery method: RumResourceMethod,
        @StringForgery url: String,
        forge: Forge
    ) {
        // Given
        val fakeStopEventAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val excludedKeys = fakeAttributes.keys + fakeStopEventAttributes.keys
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }
        val fakeViewStoppedGlobalAttributes = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }
        val fakeResourceStoppedGlobalProperties = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeViewStoppedGlobalAttributes)
        expectedAttributes.putAll(fakeStopEventAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturnConsecutively listOf(
            // one for initialization
            fakeGlobalAttributes,
            // second one for when the resource is started
            fakeGlobalAttributes,
            // third one for when the resource scope init
            fakeGlobalAttributes,
            // fourth one for when the view is stopped
            fakeViewStoppedGlobalAttributes,
            // last one when the resource is stopped
            fakeResourceStoppedGlobalProperties
        )

        testedScope = RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )
        testedScope.handleEvent(
            RumRawEvent.StartResource(key, url, method, emptyMap()),
            mockWriter
        )
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, fakeStopEventAttributes),
            mockWriter
        )

        // When
        testedScope.handleEvent(
            RumRawEvent.ResourceSent(testedScope.viewId),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue).containsExactlyContextAttributes(expectedAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
    }

    @Test
    fun `𝕄 not update the global attributes 𝕎 handleEvent(Action Sent) on stopped view`(
        @Forgery type: RumActionType,
        @StringForgery name: String,
        forge: Forge
    ) {
        // Given
        val fakeStopEventAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val excludedKeys = fakeAttributes.keys + fakeStopEventAttributes.keys
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }
        val fakeViewStoppedGlobalAttributes = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }
        val fakeActionSentGlobalProperties = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeViewStoppedGlobalAttributes)
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeStopEventAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturnConsecutively listOf(
            // one for initialization
            fakeGlobalAttributes,
            // second one for when the action is started
            fakeGlobalAttributes,
            // third one for the action scope init
            fakeGlobalAttributes,
            // fourth one for when the view is stopped
            fakeViewStoppedGlobalAttributes,
            // last one when the action was sent
            fakeActionSentGlobalProperties
        )
        testedScope = RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )
        testedScope.handleEvent(
            RumRawEvent.StartAction(type, name, forge.aBool(), emptyMap()),
            mockWriter
        )
        testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, fakeStopEventAttributes),
            mockWriter
        )

        // When
        testedScope.handleEvent(
            RumRawEvent.ActionSent(testedScope.viewId, forge.anInt()),
            mockWriter
        )

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter, times(2)).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue).containsExactlyContextAttributes(expectedAttributes)
        }
    }

    @Test
    fun `𝕄 use a copy of the global attributes 𝕎 handleEvent(StopView)`(
        forge: Forge
    ) {
        // Given
        val fakeStopEventAttributes = forge.aFilteredMap(excludedKeys = fakeAttributes.keys) {
            anHexadecimalString() to anAsciiString()
        }
        val excludedKeys = fakeAttributes.keys + fakeStopEventAttributes.keys
        val fakeGlobalAttributes = forge.aFilteredMap(excludedKeys = excludedKeys) {
            anHexadecimalString() to anAsciiString()
        }.toMutableMap()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeAttributes)
        expectedAttributes.putAll(fakeGlobalAttributes)
        expectedAttributes.putAll(fakeStopEventAttributes)
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn fakeGlobalAttributes

        testedScope = RumViewScope(
            mockParentScope,
            rumMonitor.mockSdkCore,
            fakeKey,
            fakeEventTime,
            fakeAttributes,
            mockViewChangedListener,
            mockResolver,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            featuresContextResolver = mockFeaturesContextResolver,
            trackFrustrations = fakeTrackFrustrations,
            sampleRate = fakeSampleRate
        )

        // When
        val result = testedScope.handleEvent(
            RumRawEvent.StopView(fakeKey, fakeStopEventAttributes),
            mockWriter
        )
        // updating the global attributes here
        fakeGlobalAttributes[forge.anAlphabeticalString()] = forge.anAlphabeticalString()

        // Then
        argumentCaptor<ViewEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())
            assertThat(lastValue).containsExactlyContextAttributes(expectedAttributes)
        }
        verifyNoMoreInteractions(mockWriter)
        assertThat(result).isNull()
    }

    // endregion

    // region Internal

    private fun mockEvent(): RumRawEvent {
        val event: RumRawEvent = mock()
        whenever(event.eventTime) doReturn Time()
        return event
    }

    private fun forgeGlobalAttributes(
        forge: Forge,
        existingAttributes: Map<String, Any?>
    ): Map<String, Any?> {
        val existingKeys = existingAttributes.keys
        return forge.aFilteredMap(excludedKeys = existingKeys) {
            anHexadecimalString() to anAsciiString()
        }
    }

    private fun resolveExpectedTimestamp(timestamp: Long): Long {
        return timestamp + fakeTimeInfoAtScopeStart.serverTimeOffsetMs
    }

    private fun mockSessionReplayContext(testedScope: RumViewScope) {
        whenever(
            mockFeaturesContextResolver.resolveViewHasReplay(
                fakeDatadogContext,
                testedScope.viewId
            )
        ).thenReturn(fakeHasReplay)
        whenever(
            mockFeaturesContextResolver.resolveViewRecordsCount(
                fakeDatadogContext,
                testedScope.viewId
            )
        ).thenReturn(fakeReplayRecordsCount)
    }

    // endregion

    data class RumRawEventData(val event: RumRawEvent, val viewKey: RumScopeKey)

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }

        @Suppress("unused")
        @JvmStatic
        fun brokenTimeRawEventData(): List<RumRawEventData> {
            val forge = Forge()
            Configurator().apply { configure(forge) }
            val fakeKey = forge.getForgery<RumScopeKey>()
            val fakeName = forge.anAlphabeticalString()
            val eventTime = Time(0, 0)
            return listOf(
                RumRawEventData(
                    RumRawEvent.KeepAlive(
                        eventTime = eventTime
                    ),
                    fakeKey
                ),
                RumRawEventData(
                    RumRawEvent.AddCustomTiming(
                        fakeName,
                        eventTime = eventTime
                    ),
                    fakeKey
                ),
                RumRawEventData(RumRawEvent.StopView(fakeKey, emptyMap(), eventTime), fakeKey),
                RumRawEventData(
                    RumRawEvent.StartView(
                        fakeKey,
                        emptyMap(),
                        eventTime = eventTime
                    ),
                    fakeKey
                )
            )
        }
    }
}
