/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.metrics.MethodCallSamplingRate
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.internal.utils.formatIsoUtc
import com.datadog.android.profiling.assertj.ProfileEventAssert.Companion.assertThat
import com.datadog.android.profiling.assertj.RumMetadataEventsAssert.Companion.assertThat
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.domain.ProfilingBatchMetadata
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.telemetry.ProfilingTelemetry
import com.datadog.android.profiling.model.ProfileEvent
import com.datadog.android.profiling.model.RumMetadataEvent
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ProfilingDataWriterTest {

    private lateinit var testedDataWriterTest: ProfilingDataWriter

    @Mock
    private lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockProfilingFeature: FeatureScope

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Mock
    private lateinit var mockTimeProvider: TimeProvider

    @TempDir
    lateinit var tmp: File

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BeforeEach
    fun `set up`() {
        testedDataWriterTest = ProfilingDataWriter(mockSdkCore)
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockProfilingFeature.withWriteContext(eq(emptySet()), any())) doAnswer {
            val callback =
                it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }
        whenever(mockSdkCore.getFeature(Feature.PROFILING_FEATURE_NAME))
            .thenReturn(mockProfilingFeature)

        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.timeProvider) doReturn mockTimeProvider
        whenever(mockTimeProvider.getDeviceElapsedRealtimeNanos()) doReturn 0L
    }

    @Test
    fun `M write the result in a batch W writeManualProfile()`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        @Forgery fakeLongTasks: List<ProfilerEvent.RumLongTaskEvent>,
        @Forgery fakeAnrs: List<ProfilerEvent.RumAnrEvent>,
        forge: Forge
    ) {
        // Given
        val fakeServerTimeMs = forge.aPositiveLong()
        whenever(mockTimeProvider.getServerTimestampMillis()) doReturn fakeServerTimeMs
        val file = tmp.resolve(fakeResult.resultFilePath)
        val fakePerfettoBytes = forge.aString().toByteArray()
        file.writeBytes(fakePerfettoBytes)
        val rumContext = fakeVitals.first().rumContext

        // When
        testedDataWriterTest.writeManualProfile(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = fakeVitals.map {
                it.copy(
                    rumContext = it.rumContext.copy(
                        applicationId = rumContext.applicationId,
                        sessionId = rumContext.sessionId
                    )
                )
            },
            anrEvents = fakeAnrs.map {
                it.copy(
                    rumContext = it.rumContext.copy(
                        applicationId = rumContext.applicationId,
                        sessionId = rumContext.sessionId
                    )
                )
            },
            longTasks = fakeLongTasks.map {
                it.copy(
                    rumContext = it.rumContext.copy(
                        applicationId = rumContext.applicationId,
                        sessionId = rumContext.sessionId
                    )
                )
            }
        )

        // Then
        val argumentCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = argumentCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )
        val actualEvent = ProfileEvent.fromJson(String(argumentCaptor.firstValue.data))
        val expectedTagList = arrayListOf(
            "service:${fakeDatadogContext.service}",
            "env:${fakeDatadogContext.env}",
            "version:${fakeDatadogContext.version}",
            "sdk_version:${fakeDatadogContext.sdkVersion}",
            "profiler_version:${fakeDatadogContext.sdkVersion}",
            "runtime_version:${fakeDatadogContext.deviceInfo.osVersion}",
            "operation:${fakeResult.startReason.value}"
        )
        fakeDatadogContext.appBuildId?.let {
            expectedTagList.add("build_id:${fakeDatadogContext.appBuildId}")
        }

        assertThat(actualEvent)
            .hasStart(formatIsoUtc(fakeResult.start))
            .hasEnd(formatIsoUtc(fakeResult.end))
            .hasAttachments(listOf("perfetto.proto", "rum-mobile-events.json"))
            .hasFamily(ProfileEvent.Family.ANDROID)
            .hasRuntime(ProfileEvent.Family.ANDROID)
            .hasVersion(4)
            .hasTags(expectedTagList)
            .hasClockDrift(TimeUnit.MILLISECONDS.toNanos(fakeServerTimeMs))
            .hasApplicationId(rumContext.applicationId)
            .hasSessionId(rumContext.sessionId)
            .hasViewIds(
                (
                    fakeVitals.mapNotNull { it.rumContext.viewId } +
                        fakeAnrs.mapNotNull { it.rumContext.viewId } +
                        fakeLongTasks.mapNotNull { it.rumContext.viewId }
                    ).toSet()
            )
            .hasViewNames(
                (
                    fakeVitals.mapNotNull { it.rumContext.viewName } +
                        fakeAnrs.mapNotNull { it.rumContext.viewName } +
                        fakeLongTasks.mapNotNull { it.rumContext.viewName }
                    ).toSet()
            )
            .hasVitalIds(fakeVitals.map { it.id })
            .hasVitalNames(fakeVitals.mapNotNull { it.name })
            .hasErrorIds(fakeAnrs.map { it.id })
            .hasLongTaskIds(fakeLongTasks.map { it.id })

        val actualMetadata = ProfilingBatchMetadata
            .fromBytesOrNull(argumentCaptor.firstValue.metadata, mock<InternalLogger>())
        checkNotNull(actualMetadata)
        assertThat(actualMetadata.perfettoBytes).isEqualTo(fakePerfettoBytes)

        val actualMetadataEvents = JsonParser.parseString(String(actualMetadata.rumMobileEventsBytes))
            .asJsonArray
            .map {
                RumMetadataEvent.fromJsonObject(it.asJsonObject)
            }
        assertThat(actualMetadataEvents).isNotEmpty

        val anrsMetadata = actualMetadataEvents.filter { it.type == RumMetadataEvent.Type.ERROR }
        assertThat(anrsMetadata).hasSize(fakeAnrs.size)
        anrsMetadata.forEach { anr ->
            val fakeAnr = fakeAnrs.first { it.id == anr.id }
            assertThat(anr).hasName(null)
            assertThat(anr).hasStartNs(TimeUnit.MILLISECONDS.toNanos(fakeAnr.startMs))
            assertThat(anr).hasDurationNs(fakeAnr.durationNs)
        }

        val longTasksMetadata = actualMetadataEvents.filter { it.type == RumMetadataEvent.Type.LONG_TASK }
        assertThat(longTasksMetadata).hasSize(fakeLongTasks.size)
        longTasksMetadata.forEach { longTask ->
            val fakeLongTask = fakeLongTasks.first { it.id == longTask.id }
            assertThat(longTask).hasName(null)
            assertThat(longTask).hasStartNs(TimeUnit.MILLISECONDS.toNanos(fakeLongTask.startMs))
            assertThat(longTask).hasDurationNs(fakeLongTask.durationNs)
        }

        val vitalsMetadata = actualMetadataEvents.filter { it.type == RumMetadataEvent.Type.VITAL }
        assertThat(vitalsMetadata).hasSize(fakeVitals.size)
        vitalsMetadata.forEach { vital ->
            val fakeVital = fakeVitals.first { it.id == vital.id }
            assertThat(vital).hasName(fakeVital.name)
            assertThat(vital).hasStartNs(TimeUnit.MILLISECONDS.toNanos(fakeVital.startMs))
            assertThat(vital).hasDurationNs(fakeVital.durationNs)
        }
        verifyNoMoreInteractions(mockEventBatchWriter)
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `M populate clock_drift from server time minus boot time W write`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        forge: Forge
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(forge.aString().toByteArray())
        val fakeServerTimeMs = forge.aPositiveLong()
        val fakeBootTimeNs = forge.aLong(min = 1L, max = 1_000_000L)
        val expectedClockDriftNs = TimeUnit.MILLISECONDS.toNanos(fakeServerTimeMs) - fakeBootTimeNs
        whenever(mockTimeProvider.getServerTimestampMillis()) doReturn fakeServerTimeMs
        whenever(mockTimeProvider.getDeviceElapsedRealtimeNanos()) doReturn fakeBootTimeNs
        val rumContext = fakeVitals.first().rumContext
        val alignedVitals = fakeVitals.map {
            it.copy(
                rumContext = it.rumContext.copy(
                    applicationId = rumContext.applicationId,
                    sessionId = rumContext.sessionId
                )
            )
        }

        // When
        testedDataWriterTest.writeManualProfile(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = alignedVitals,
            anrEvents = emptyList(),
            longTasks = emptyList()
        )

        // Then
        val argumentCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = argumentCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )
        val actualEvent = ProfileEvent.fromJson(String(argumentCaptor.firstValue.data))
        assertThat(actualEvent).hasClockDrift(expectedClockDriftNs)
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `M skip writing and log warn on delete W writeManualProfile() {perfetto file not found}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        @Forgery fakeLongTasks: List<ProfilerEvent.RumLongTaskEvent>,
        @Forgery fakeAnrs: List<ProfilerEvent.RumAnrEvent>
    ) {
        // Given — file path exists in TempDir but file is never created
        val nonExistentFile = File(tmp, "nonexistent.perfetto-stack-sample")

        // When
        testedDataWriterTest.writeManualProfile(
            profilingResult = fakeResult.copy(resultFilePath = nonExistentFile.absolutePath),
            vitalEvents = fakeVitals,
            anrEvents = fakeAnrs,
            longTasks = fakeLongTasks
        )

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            any<() -> String>(),
            isNull(),
            eq(false),
            isNull()
        )
        val expectedProps = mapOf(
            ProfilingTelemetry.KEY_METRIC_TYPE to ProfilingDataWriter.METRIC_TYPE_PROFILING_WRITE,
            ProfilingDataWriter.KEY_PROFILING_WRITE to mapOf(
                ProfilingDataWriter.KEY_DROPPED to true,
                ProfilingDataWriter.KEY_DROP_REASON to ProfilingDataWriter.DROP_REASON_PERFETTO_UNREADABLE,
                ProfilingTelemetry.KEY_START_REASON to fakeResult.startReason.value,
                ProfilingDataWriter.KEY_LONG_TASK_COUNT to fakeLongTasks.size,
                ProfilingDataWriter.KEY_ANR_COUNT to fakeAnrs.size,
                ProfilingDataWriter.KEY_VITAL_COUNT to fakeVitals.size
            )
        )
        verify(mockInternalLogger).logMetric(
            any(),
            eq(expectedProps),
            eq(MethodCallSamplingRate.ALL.rate),
            isNull()
        )
        verifyNoMoreInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M skip writing and report drop metric W writeManualProfile() {file is empty}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        @Forgery fakeLongTasks: List<ProfilerEvent.RumLongTaskEvent>,
        @Forgery fakeAnrs: List<ProfilerEvent.RumAnrEvent>
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(ByteArray(0))

        // When
        testedDataWriterTest.writeManualProfile(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = fakeVitals,
            anrEvents = fakeAnrs,
            longTasks = fakeLongTasks
        )

        // Then
        assertThat(file.exists()).isFalse()
        val expectedProps = mapOf(
            ProfilingTelemetry.KEY_METRIC_TYPE to ProfilingDataWriter.METRIC_TYPE_PROFILING_WRITE,
            ProfilingDataWriter.KEY_PROFILING_WRITE to mapOf(
                ProfilingDataWriter.KEY_DROPPED to true,
                ProfilingDataWriter.KEY_DROP_REASON to ProfilingDataWriter.DROP_REASON_PERFETTO_UNREADABLE,
                ProfilingTelemetry.KEY_START_REASON to fakeResult.startReason.value,
                ProfilingDataWriter.KEY_LONG_TASK_COUNT to fakeLongTasks.size,
                ProfilingDataWriter.KEY_ANR_COUNT to fakeAnrs.size,
                ProfilingDataWriter.KEY_VITAL_COUNT to fakeVitals.size
            )
        )
        verify(mockInternalLogger).logMetric(
            any(),
            eq(expectedProps),
            eq(MethodCallSamplingRate.ALL.rate),
            isNull()
        )
        verifyNoMoreInteractions(mockInternalLogger, mockEventBatchWriter)
    }

    @Test
    fun `M skip writing and report metric W writeManualProfile() {no rum events}`(
        @Forgery fakeResult: PerfettoResult,
        forge: Forge
    ) {
        // Given — perfetto file is readable but there's nothing to attach
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(forge.aString().toByteArray())

        // When
        testedDataWriterTest.writeManualProfile(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = emptyList(),
            anrEvents = emptyList(),
            longTasks = emptyList()
        )

        // Then
        assertThat(file.exists()).isFalse()
        val expectedProps = mapOf(
            ProfilingTelemetry.KEY_METRIC_TYPE to ProfilingDataWriter.METRIC_TYPE_PROFILING_WRITE,
            ProfilingDataWriter.KEY_PROFILING_WRITE to mapOf(
                ProfilingDataWriter.KEY_DROPPED to true,
                ProfilingDataWriter.KEY_DROP_REASON to ProfilingDataWriter.DROP_REASON_NO_RUM_EVENTS,
                ProfilingTelemetry.KEY_START_REASON to fakeResult.startReason.value,
                ProfilingDataWriter.KEY_LONG_TASK_COUNT to 0,
                ProfilingDataWriter.KEY_ANR_COUNT to 0,
                ProfilingDataWriter.KEY_VITAL_COUNT to 0
            )
        )
        verify(mockInternalLogger).logMetric(
            any(),
            eq(expectedProps),
            eq(MethodCallSamplingRate.ALL.rate),
            isNull()
        )
        verifyNoMoreInteractions(mockInternalLogger, mockEventBatchWriter)
    }

    @Test
    fun `M write the result in a batch W writeManualProfile() {only vital events present}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        forge: Forge
    ) {
        // Given — RUM context must come from vitals (elvis fallback after long tasks + anrs are empty)
        val file = tmp.resolve(fakeResult.resultFilePath)
        val fakePerfettoBytes = forge.aString().toByteArray()
        file.writeBytes(fakePerfettoBytes)
        val rumContext = fakeVitals.first().rumContext
        val alignedVitals = fakeVitals.map {
            it.copy(
                rumContext = it.rumContext.copy(
                    applicationId = rumContext.applicationId,
                    sessionId = rumContext.sessionId
                )
            )
        }

        // When
        testedDataWriterTest.writeManualProfile(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = alignedVitals,
            anrEvents = emptyList(),
            longTasks = emptyList()
        )

        // Then
        val argumentCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = argumentCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )
        val actualEvent = ProfileEvent.fromJson(String(argumentCaptor.firstValue.data))
        assertThat(actualEvent)
            .hasAttachments(listOf("perfetto.proto", "rum-mobile-events.json"))
            .hasApplicationId(rumContext.applicationId)
            .hasSessionId(rumContext.sessionId)
            .hasVitalIds(alignedVitals.map { it.id })
            .hasVitalNames(alignedVitals.mapNotNull { it.name })
            .hasLongTaskIds(emptyList())
            .hasErrorIds(emptyList())

        val actualMetadata = ProfilingBatchMetadata
            .fromBytesOrNull(argumentCaptor.firstValue.metadata, mock<InternalLogger>())
        checkNotNull(actualMetadata)
        assertThat(actualMetadata.perfettoBytes).isEqualTo(fakePerfettoBytes)
        val actualMetadataEvents = JsonParser.parseString(String(actualMetadata.rumMobileEventsBytes))
            .asJsonArray
            .map { RumMetadataEvent.fromJsonObject(it.asJsonObject) }
        val vitalsMetadata = actualMetadataEvents.filter { it.type == RumMetadataEvent.Type.VITAL }
        assertThat(vitalsMetadata).hasSize(alignedVitals.size)
        vitalsMetadata.forEach { vital ->
            val fakeVital = alignedVitals.first { it.id == vital.id }
            assertThat(vital.startNs).isEqualTo(TimeUnit.MILLISECONDS.toNanos(fakeVital.startMs))
            assertThat(vital.durationNs).isEqualTo(fakeVital.durationNs)
        }
        assertThat(actualMetadataEvents.none { it.type == RumMetadataEvent.Type.ERROR }).isTrue()
        assertThat(actualMetadataEvents.none { it.type == RumMetadataEvent.Type.LONG_TASK }).isTrue()
        assertThat(file.exists()).isFalse()
        verifyNoMoreInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M delete result file W writeManualProfile() {feature not initialized}`(
        @Forgery fakeResult: PerfettoResult,
        forge: Forge
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)) doReturn null
        val file = File(tmp, "fake_profile.perfetto-stack-sample")
        file.writeBytes(forge.aString().toByteArray())

        // When
        testedDataWriterTest.writeManualProfile(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = emptyList(),
            anrEvents = emptyList(),
            longTasks = emptyList()
        )

        // Then
        assertThat(file.exists()).isFalse()
        verifyNoInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M delete result file W writeManualProfile() {events present}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        forge: Forge
    ) {
        // Given
        val file = File(tmp, "fake_profile.perfetto-stack-sample")
        file.writeBytes(forge.aString().toByteArray())
        val rumContext = fakeVitals.first().rumContext
        val alignedVitals = fakeVitals.map {
            it.copy(
                rumContext = it.rumContext.copy(
                    applicationId = rumContext.applicationId,
                    sessionId = rumContext.sessionId
                )
            )
        }

        // When
        testedDataWriterTest.writeManualProfile(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = alignedVitals,
            anrEvents = emptyList(),
            longTasks = emptyList()
        )

        // Then
        assertThat(file.exists()).isFalse()
        verify(mockEventBatchWriter).write(any(), isNull(), eq(EventType.DEFAULT))
    }

    @Test
    fun `M write profile and record clock_drift W writeManualProfile() {extreme clock drift}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        forge: Forge
    ) {
        // Given
        val fakeServerTimeMs = forge.aLong(min = 1_000_000_000_000L, max = 2_000_000_000_000L)
        val fakeBootTimeNs = forge.aLong(min = 1L, max = 1_000_000L)
        whenever(mockTimeProvider.getServerTimestampMillis()) doReturn fakeServerTimeMs
        whenever(mockTimeProvider.getDeviceElapsedRealtimeNanos()) doReturn fakeBootTimeNs
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(forge.aString().toByteArray())

        // When
        testedDataWriterTest.writeManualProfile(
            profilingResult = fakeResult.copy(
                resultFilePath = file.absolutePath,
                startReason = ProfilingStartReason.CONTINUOUS
            ),
            vitalEvents = fakeVitals,
            anrEvents = emptyList(),
            longTasks = emptyList()
        )

        // Then
        val argumentCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = argumentCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )
        val actualEvent = ProfileEvent.fromJson(String(argumentCaptor.firstValue.data))
        assertThat(actualEvent).hasClockDrift(
            TimeUnit.MILLISECONDS.toNanos(fakeServerTimeMs) - fakeBootTimeNs
        )
        val expectedProps = mapOf(
            ProfilingTelemetry.KEY_METRIC_TYPE to ProfilingDataWriter.METRIC_TYPE_PROFILING_WRITE,
            ProfilingDataWriter.KEY_PROFILING_WRITE to mapOf(
                ProfilingDataWriter.KEY_DROPPED to false,
                ProfilingDataWriter.KEY_DROP_REASON to null,
                ProfilingTelemetry.KEY_START_REASON to ProfilingStartReason.CONTINUOUS.value,
                ProfilingDataWriter.KEY_LONG_TASK_COUNT to 0,
                ProfilingDataWriter.KEY_ANR_COUNT to 0,
                ProfilingDataWriter.KEY_VITAL_COUNT to fakeVitals.size
            )
        )
        verify(mockInternalLogger).logMetric(
            any(),
            eq(expectedProps),
            eq(MethodCallSamplingRate.ALL.rate),
            isNull()
        )
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `M report write metric with event counts W writeManualProfile() {write successful}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        @Forgery fakeLongTasks: List<ProfilerEvent.RumLongTaskEvent>,
        @Forgery fakeAnrs: List<ProfilerEvent.RumAnrEvent>,
        forge: Forge
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(forge.aString().toByteArray())

        // When
        testedDataWriterTest.writeManualProfile(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = fakeVitals,
            anrEvents = fakeAnrs,
            longTasks = fakeLongTasks
        )

        // Then
        val expectedProps = mapOf(
            ProfilingTelemetry.KEY_METRIC_TYPE to ProfilingDataWriter.METRIC_TYPE_PROFILING_WRITE,
            ProfilingDataWriter.KEY_PROFILING_WRITE to mapOf(
                ProfilingDataWriter.KEY_DROPPED to false,
                ProfilingDataWriter.KEY_DROP_REASON to null,
                ProfilingTelemetry.KEY_START_REASON to fakeResult.startReason.value,
                ProfilingDataWriter.KEY_LONG_TASK_COUNT to fakeLongTasks.size,
                ProfilingDataWriter.KEY_ANR_COUNT to fakeAnrs.size,
                ProfilingDataWriter.KEY_VITAL_COUNT to fakeVitals.size
            )
        )
        verify(mockInternalLogger).logMetric(
            any(),
            eq(expectedProps),
            eq(MethodCallSamplingRate.ALL.rate),
            isNull()
        )
    }

    @Test
    fun `M delete result file W discard`(
        @Forgery fakeResult: PerfettoResult,
        forge: Forge
    ) {
        // Given
        val file = File(tmp, "fake_profile.perfetto-stack-sample")
        file.writeBytes(forge.aString().toByteArray())

        // When
        testedDataWriterTest.discard(fakeResult.copy(resultFilePath = file.absolutePath))

        // Then
        assertThat(file.exists()).isFalse()
        verifyNoInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M log warn on delete failure W discard {file not found}`(
        @Forgery fakeResult: PerfettoResult
    ) {
        // Given — file path exists in TempDir but file is never created
        val nonExistentFile = File(tmp, "nonexistent.perfetto-stack-sample")

        // When
        testedDataWriterTest.discard(fakeResult.copy(resultFilePath = nonExistentFile.absolutePath))

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            any<() -> String>(),
            isNull(),
            eq(false),
            isNull()
        )
        verifyNoInteractions(mockEventBatchWriter)
    }
}
