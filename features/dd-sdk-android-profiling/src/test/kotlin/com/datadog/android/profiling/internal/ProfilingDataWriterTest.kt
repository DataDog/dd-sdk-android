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
import com.datadog.android.internal.profiling.ProfilingRumContext
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.internal.utils.formatIsoUtc
import com.datadog.android.profiling.assertj.ProfileEventAssert.Companion.assertThat
import com.datadog.android.profiling.assertj.RumMetadataEventsAssert.Companion.assertThat
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.domain.ProfilingBatchMetadata
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.telemetry.ProfilingTelemetry
import com.datadog.android.profiling.internal.utils.CompressionResult
import com.datadog.android.profiling.model.ProfileEvent
import com.datadog.android.profiling.model.RumMetadataEvent
import com.github.luben.zstd.ZstdInputStream
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
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.io.RandomAccessFile
import java.util.Random
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
        testedDataWriterTest =
            ProfilingDataWriter(mockSdkCore, identityCompressFile)
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
        fakeResult.profileTypes.forEach {
            expectedTagList.add("profile_type:${it.value}")
        }
        assertThat(actualEvent)
            .hasStart(formatIsoUtc(fakeResult.start))
            .hasEnd(formatIsoUtc(fakeResult.end))
            .hasAttachments(listOf("perfetto.proto", "rum-mobile-events.json"))
            .hasFamily(ProfileEvent.Family.ANDROID)
            .hasRuntime(ProfileEvent.Family.ANDROID)
            .hasVersion(4)
            .hasTags(expectedTagList)
            .hasBootNtp(TimeUnit.MILLISECONDS.toNanos(fakeServerTimeMs))
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
            .hasVitalNames(fakeVitals.mapNotNull { it.name }.toSet())
            .hasErrorIds(fakeAnrs.map { it.id })
            .hasLongTaskIds(fakeLongTasks.map { it.id })
        val actualMetadata = ProfilingBatchMetadata
            .fromBytesOrNull(argumentCaptor.firstValue.metadata)
        checkNotNull(actualMetadata)
        assertThat(
            actualMetadata.bytes.copyOfRange(actualMetadata.perfettoOffset, actualMetadata.rumMobileEventsOffset)
        ).isEqualTo(fakePerfettoBytes)
        val actualMetadataEvents = JsonParser.parseString(
            String(actualMetadata.bytes, actualMetadata.rumMobileEventsOffset, actualMetadata.rumMobileEventsLength)
        )
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
    fun `M populate boot_ntp from server time minus boot time W write`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        forge: Forge
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(forge.aString().toByteArray())
        val fakeServerTimeMs = forge.aPositiveLong()
        val fakeBootTimeNs = forge.aLong(min = 1L, max = 1_000_000L)
        val expectedBootNtpNs = TimeUnit.MILLISECONDS.toNanos(fakeServerTimeMs) - fakeBootTimeNs
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
        assertThat(actualEvent).hasBootNtp(expectedBootNtpNs)
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `M skip writing W writeManualProfile() {perfetto file not found}`(
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
    fun `M drop and delete file W writeManualProfile() {profile one byte over the limit}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        @Forgery fakeLongTasks: List<ProfilerEvent.RumLongTaskEvent>,
        @Forgery fakeAnrs: List<ProfilerEvent.RumAnrEvent>
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        val fakeSize = PROFILE_SIZE_LIMIT_BYTES + 1
        RandomAccessFile(file, "rw").use { it.setLength(fakeSize) }
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
                ProfilingDataWriter.KEY_DROP_REASON to ProfilingDataWriter.DROP_REASON_PROFILE_TOO_LARGE,
                ProfilingTelemetry.KEY_START_REASON to fakeResult.startReason.value,
                ProfilingDataWriter.KEY_LONG_TASK_COUNT to fakeLongTasks.size,
                ProfilingDataWriter.KEY_ANR_COUNT to fakeAnrs.size,
                ProfilingDataWriter.KEY_VITAL_COUNT to fakeVitals.size,
                ProfilingDataWriter.KEY_PROFILE_SIZE_BYTES to fakeSize
            )
        )
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).logMetric(
                capture(),
                eq(expectedProps),
                eq(MethodCallSamplingRate.ALL.rate),
                isNull()
            )
            assertThat(firstValue()).isEqualTo(ProfilingTelemetry.TELEMETRY_MSG_PROFILING_SESSION)
        }
        verifyNoInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M drop and delete file W writeTriggerProfile() {profile one byte over the limit}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeRumContext: ProfilingRumContext,
        forge: Forge
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        val fakeSize = PROFILE_SIZE_LIMIT_BYTES + 1
        RandomAccessFile(file, "rw").use { it.setLength(fakeSize) }
        val fakeErrorId = forge.anAlphabeticalString()
        // When
        testedDataWriterTest.writeTriggerProfile(
            perfettoResult = fakeResult.copy(resultFilePath = file.absolutePath),
            rumErrorId = fakeErrorId,
            rumContext = fakeRumContext
        )
        // Then
        assertThat(file.exists()).isFalse()
        val expectedProps = mapOf(
            ProfilingTelemetry.KEY_METRIC_TYPE to ProfilingDataWriter.METRIC_TYPE_PROFILING_WRITE,
            ProfilingDataWriter.KEY_PROFILING_WRITE to mapOf(
                ProfilingDataWriter.KEY_DROPPED to true,
                ProfilingDataWriter.KEY_DROP_REASON to ProfilingDataWriter.DROP_REASON_PROFILE_TOO_LARGE,
                ProfilingTelemetry.KEY_START_REASON to fakeResult.startReason.value,
                ProfilingDataWriter.KEY_HAS_RUM_ERROR_ID to true,
                ProfilingDataWriter.KEY_PROFILE_SIZE_BYTES to fakeSize
            )
        )
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).logMetric(
                capture(),
                eq(expectedProps),
                eq(MethodCallSamplingRate.ALL.rate),
                isNull()
            )
            assertThat(firstValue()).isEqualTo(ProfilingTelemetry.TELEMETRY_MSG_PROFILING_SESSION)
        }
        verifyNoInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M write the result W writeManualProfile() {profile exactly at the limit}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        @Forgery fakeLongTasks: List<ProfilerEvent.RumLongTaskEvent>,
        @Forgery fakeAnrs: List<ProfilerEvent.RumAnrEvent>
    ) {
        // Given
        whenever(mockEventBatchWriter.write(any(), isNull(), eq(EventType.DEFAULT))) doReturn true
        val file = tmp.resolve(fakeResult.resultFilePath)
        RandomAccessFile(file, "rw").use { it.setLength(PROFILE_SIZE_LIMIT_BYTES) }
        // When
        testedDataWriterTest.writeManualProfile(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = fakeVitals,
            anrEvents = fakeAnrs,
            longTasks = fakeLongTasks
        )
        // Then
        argumentCaptor<RawBatchEvent> {
            verify(mockEventBatchWriter).write(capture(), isNull(), eq(EventType.DEFAULT))
            val metadata = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(firstValue.metadata))
            assertThat(metadata.perfettoLength.toLong()).isEqualTo(PROFILE_SIZE_LIMIT_BYTES)
        }
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `M write the result W writeTriggerProfile() {profile exactly at the limit}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeRumContext: ProfilingRumContext,
        forge: Forge
    ) {
        // Given
        whenever(mockEventBatchWriter.write(any(), isNull(), eq(EventType.DEFAULT))) doReturn true
        val file = tmp.resolve(fakeResult.resultFilePath)
        RandomAccessFile(file, "rw").use { it.setLength(PROFILE_SIZE_LIMIT_BYTES) }
        // When
        testedDataWriterTest.writeTriggerProfile(
            perfettoResult = fakeResult.copy(resultFilePath = file.absolutePath),
            rumErrorId = forge.anAlphabeticalString(),
            rumContext = fakeRumContext
        )
        // Then
        argumentCaptor<RawBatchEvent> {
            verify(mockEventBatchWriter).write(capture(), isNull(), eq(EventType.DEFAULT))
            val metadata = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(firstValue.metadata))
            assertThat(metadata.perfettoLength.toLong()).isEqualTo(PROFILE_SIZE_LIMIT_BYTES)
        }
        verifySuccessfulWriteMetric(successfulTriggerWriteProps(fakeResult), PROFILE_SIZE_LIMIT_BYTES)
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `M write the result in a batch W writeTriggerProfile()`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeRumContext: ProfilingRumContext,
        forge: Forge
    ) {
        // Given
        whenever(mockEventBatchWriter.write(any(), isNull(), eq(EventType.DEFAULT))) doReturn true
        val file = tmp.resolve(fakeResult.resultFilePath)
        val fakePerfettoBytes = forge.aString(size = forge.anInt(min = 1, max = 4096)).toByteArray()
        file.writeBytes(fakePerfettoBytes)
        val fakeErrorId = forge.anAlphabeticalString()
        // When
        testedDataWriterTest.writeTriggerProfile(
            perfettoResult = fakeResult.copy(resultFilePath = file.absolutePath),
            rumErrorId = fakeErrorId,
            rumContext = fakeRumContext
        )
        // Then
        argumentCaptor<RawBatchEvent> {
            verify(mockEventBatchWriter).write(capture(), isNull(), eq(EventType.DEFAULT))
            val metadata = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(firstValue.metadata))
            assertThat(
                metadata.bytes.copyOfRange(metadata.perfettoOffset, metadata.rumMobileEventsOffset)
            ).isEqualTo(fakePerfettoBytes)
            val rumEvents = JsonParser.parseString(
                String(metadata.bytes, metadata.rumMobileEventsOffset, metadata.rumMobileEventsLength)
            ).asJsonArray
                .map { RumMetadataEvent.fromJsonObject(it.asJsonObject) }
            assertThat(rumEvents.map { it.id }).containsExactly(fakeErrorId)
        }
        verifySuccessfulWriteMetric(successfulTriggerWriteProps(fakeResult), fakePerfettoBytes.size.toLong())
        assertThat(file.exists()).isFalse()
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
            .hasVitalNames(alignedVitals.mapNotNull { it.name }.toSet())
            .hasLongTaskIds(emptyList())
            .hasErrorIds(emptyList())
        val actualMetadata = ProfilingBatchMetadata
            .fromBytesOrNull(argumentCaptor.firstValue.metadata)
        checkNotNull(actualMetadata)
        assertThat(
            actualMetadata.bytes.copyOfRange(actualMetadata.perfettoOffset, actualMetadata.rumMobileEventsOffset)
        ).isEqualTo(fakePerfettoBytes)
        val actualMetadataEvents = JsonParser.parseString(
            String(actualMetadata.bytes, actualMetadata.rumMobileEventsOffset, actualMetadata.rumMobileEventsLength)
        )
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
    fun `M write profile and record boot_ntp W writeManualProfile() {extreme clock offset}`(
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
        val fakePerfettoBytes = forge.aString().toByteArray()
        file.writeBytes(fakePerfettoBytes)
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
        assertThat(actualEvent).hasBootNtp(
            TimeUnit.MILLISECONDS.toNanos(fakeServerTimeMs) - fakeBootTimeNs
        )
        val expectedProps = mapOf(
            ProfilingTelemetry.KEY_METRIC_TYPE to ProfilingDataWriter.METRIC_TYPE_PROFILING_WRITE,
            ProfilingDataWriter.KEY_PROFILING_WRITE to mapOf(
                ProfilingDataWriter.KEY_DROPPED to false,
                ProfilingDataWriter.KEY_PROFILE_COMPRESSED to false,
                ProfilingDataWriter.KEY_DROP_REASON to null,
                ProfilingTelemetry.KEY_START_REASON to ProfilingStartReason.CONTINUOUS.value,
                ProfilingDataWriter.KEY_LONG_TASK_COUNT to 0,
                ProfilingDataWriter.KEY_ANR_COUNT to 0,
                ProfilingDataWriter.KEY_VITAL_COUNT to fakeVitals.size
            )
        )
        verifySuccessfulWriteMetric(expectedProps, fakePerfettoBytes.size.toLong())
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
        val fakePerfettoBytes = forge.aString().toByteArray()
        file.writeBytes(fakePerfettoBytes)
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
                ProfilingDataWriter.KEY_PROFILE_COMPRESSED to false,
                ProfilingDataWriter.KEY_DROP_REASON to null,
                ProfilingTelemetry.KEY_START_REASON to fakeResult.startReason.value,
                ProfilingDataWriter.KEY_LONG_TASK_COUNT to fakeLongTasks.size,
                ProfilingDataWriter.KEY_ANR_COUNT to fakeAnrs.size,
                ProfilingDataWriter.KEY_VITAL_COUNT to fakeVitals.size
            )
        )
        verifySuccessfulWriteMetric(expectedProps, fakePerfettoBytes.size.toLong())
    }

    @Test
    fun `M save zstd trace and raw RUM events W writeManualProfile() {zstd compressor}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        @Forgery fakeLongTasks: List<ProfilerEvent.RumLongTaskEvent>,
        @Forgery fakeAnrs: List<ProfilerEvent.RumAnrEvent>,
        forge: Forge
    ) {
        // Given
        val rawBytes = forge.aString(size = 4096).toByteArray()
        val file = tmp.resolve(fakeResult.resultFilePath).apply { writeBytes(rawBytes) }
        whenever(mockEventBatchWriter.write(any(), isNull(), eq(EventType.DEFAULT))) doReturn true
        testedDataWriterTest.writeManualProfile(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = fakeVitals,
            anrEvents = fakeAnrs,
            longTasks = fakeLongTasks
        )
        val uncompressedRumEventsBytes = argumentCaptor<RawBatchEvent> {
            verify(mockEventBatchWriter).write(capture(), isNull(), eq(EventType.DEFAULT))
        }.firstValue.let { checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(it.metadata)) }.rumMobileEventsBytes
        clearInvocations(mockEventBatchWriter)
        file.writeBytes(rawBytes)
        // When
        val metadata = writeManualProfileWithZstd(fakeResult, file, fakeVitals, fakeAnrs, fakeLongTasks)
        // Then
        assertThat(metadata.perfettoBytes).startsWith(*ZSTD_MAGIC)
        assertThat(metadata.perfettoBytes.zstdDecompress()).isEqualTo(rawBytes)
        assertThat(metadata.rumMobileEventsBytes).isEqualTo(uncompressedRumEventsBytes)
        val rumEvents = JsonParser.parseString(String(metadata.rumMobileEventsBytes)).asJsonArray
        assertThat(rumEvents.size()).isEqualTo(fakeVitals.size + fakeAnrs.size + fakeLongTasks.size)
    }

    @Test
    fun `M keep profile W writeManualProfile() {raw over the limit, compressed under it}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        @Forgery fakeLongTasks: List<ProfilerEvent.RumLongTaskEvent>,
        @Forgery fakeAnrs: List<ProfilerEvent.RumAnrEvent>
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        RandomAccessFile(file, "rw").use { it.setLength(PROFILE_SIZE_LIMIT_BYTES + 1) }
        // When
        val metadata = writeManualProfileWithZstd(fakeResult, file, fakeVitals, fakeAnrs, fakeLongTasks)
        // Then
        assertThat(metadata.perfettoBytes.size.toLong()).isLessThan(PROFILE_SIZE_LIMIT_BYTES)
        assertThat(metadata.perfettoBytes.zstdDecompress().size.toLong()).isEqualTo(PROFILE_SIZE_LIMIT_BYTES + 1)
    }

    @Test
    fun `M drop profile W writeManualProfile() {compressed still over the limit}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        @Forgery fakeLongTasks: List<ProfilerEvent.RumLongTaskEvent>,
        @Forgery fakeAnrs: List<ProfilerEvent.RumAnrEvent>,
        forge: Forge
    ) {
        // Given
        testedDataWriterTest = ProfilingDataWriter(mockSdkCore)
        val incompressible = ByteArray(PROFILE_SIZE_LIMIT_BYTES.toInt() + 1).also {
            Random(forge.aLong()).nextBytes(it)
        }
        val file = tmp.resolve(fakeResult.resultFilePath).apply { writeBytes(incompressible) }
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
                ProfilingDataWriter.KEY_DROP_REASON to ProfilingDataWriter.DROP_REASON_PROFILE_TOO_LARGE,
                ProfilingTelemetry.KEY_START_REASON to fakeResult.startReason.value,
                ProfilingDataWriter.KEY_LONG_TASK_COUNT to fakeLongTasks.size,
                ProfilingDataWriter.KEY_ANR_COUNT to fakeAnrs.size,
                ProfilingDataWriter.KEY_VITAL_COUNT to fakeVitals.size
            )
        )
        argumentCaptor<Map<String, Any?>> {
            verify(mockInternalLogger).logMetric(any(), capture(), eq(MethodCallSamplingRate.ALL.rate), isNull())
            @Suppress("UNCHECKED_CAST")
            val writeProps = firstValue[ProfilingDataWriter.KEY_PROFILING_WRITE] as Map<String, Any?>
            val sizeBytes = writeProps[ProfilingDataWriter.KEY_PROFILE_SIZE_BYTES] as Long
            assertThat(sizeBytes).isGreaterThan(PROFILE_SIZE_LIMIT_BYTES)
            assertThat(writeProps.minus(ProfilingDataWriter.KEY_PROFILE_SIZE_BYTES))
                .isEqualTo(expectedProps.getValue(ProfilingDataWriter.KEY_PROFILING_WRITE))
        }
        verifyNoInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M report the compressed size W writeManualProfile() {trace compressed}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        @Forgery fakeLongTasks: List<ProfilerEvent.RumLongTaskEvent>,
        @Forgery fakeAnrs: List<ProfilerEvent.RumAnrEvent>,
        forge: Forge
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath).apply { writeBytes(forge.aString().toByteArray()) }
        val fakeCompressedBytes = forge.aString().toByteArray()
        testedDataWriterTest = ProfilingDataWriter(
            mockSdkCore,
            {
                CompressionResult.Compressed(
                    fakeCompressedBytes
                )
            }
        )
        whenever(mockEventBatchWriter.write(any(), isNull(), eq(EventType.DEFAULT))) doReturn true
        // When
        testedDataWriterTest.writeManualProfile(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = fakeVitals,
            anrEvents = fakeAnrs,
            longTasks = fakeLongTasks
        )
        // Then
        argumentCaptor<RawBatchEvent> {
            verify(mockEventBatchWriter).write(capture(), isNull(), eq(EventType.DEFAULT))
            val metadata = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(firstValue.metadata))
            assertThat(metadata.perfettoBytes).isEqualTo(fakeCompressedBytes)
        }
        val expectedProps = mapOf(
            ProfilingTelemetry.KEY_METRIC_TYPE to ProfilingDataWriter.METRIC_TYPE_PROFILING_WRITE,
            ProfilingDataWriter.KEY_PROFILING_WRITE to mapOf(
                ProfilingDataWriter.KEY_DROPPED to false,
                ProfilingDataWriter.KEY_PROFILE_COMPRESSED to false,
                ProfilingDataWriter.KEY_DROP_REASON to null,
                ProfilingTelemetry.KEY_START_REASON to fakeResult.startReason.value,
                ProfilingDataWriter.KEY_LONG_TASK_COUNT to fakeLongTasks.size,
                ProfilingDataWriter.KEY_ANR_COUNT to fakeAnrs.size,
                ProfilingDataWriter.KEY_VITAL_COUNT to fakeVitals.size,
                ProfilingDataWriter.KEY_PROFILE_SIZE_BYTES to fakeCompressedBytes.size.toLong(),
                ProfilingDataWriter.KEY_PROFILE_COMPRESSED to true
            )
        )
        verifySuccessfulWriteMetric(expectedProps, fakeCompressedBytes.size.toLong())
    }

    @Test
    fun `M save the raw trace W writeManualProfile() {compression fell back}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        @Forgery fakeLongTasks: List<ProfilerEvent.RumLongTaskEvent>,
        @Forgery fakeAnrs: List<ProfilerEvent.RumAnrEvent>,
        forge: Forge
    ) {
        // Given the trace was stored raw
        val rawBytes = forge.aString().toByteArray()
        val file = tmp.resolve(fakeResult.resultFilePath).apply { writeBytes(rawBytes) }
        testedDataWriterTest = ProfilingDataWriter(
            mockSdkCore,
            {
                CompressionResult.Raw(
                    rawBytes.size.toLong()
                )
            }
        )
        whenever(mockEventBatchWriter.write(any(), isNull(), eq(EventType.DEFAULT))) doReturn true

        // When
        testedDataWriterTest.writeManualProfile(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = fakeVitals,
            anrEvents = fakeAnrs,
            longTasks = fakeLongTasks
        )

        // Then
        argumentCaptor<RawBatchEvent> {
            verify(mockEventBatchWriter).write(capture(), isNull(), eq(EventType.DEFAULT))
            val metadata = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(firstValue.metadata))
            assertThat(metadata.perfettoBytes).isEqualTo(rawBytes)
        }
        val expectedProps = mapOf(
            ProfilingTelemetry.KEY_METRIC_TYPE to ProfilingDataWriter.METRIC_TYPE_PROFILING_WRITE,
            ProfilingDataWriter.KEY_PROFILING_WRITE to mapOf(
                ProfilingDataWriter.KEY_DROPPED to false,
                ProfilingDataWriter.KEY_DROP_REASON to null,
                ProfilingTelemetry.KEY_START_REASON to fakeResult.startReason.value,
                ProfilingDataWriter.KEY_LONG_TASK_COUNT to fakeLongTasks.size,
                ProfilingDataWriter.KEY_ANR_COUNT to fakeAnrs.size,
                ProfilingDataWriter.KEY_VITAL_COUNT to fakeVitals.size,
                ProfilingDataWriter.KEY_PROFILE_SIZE_BYTES to rawBytes.size.toLong(),
                ProfilingDataWriter.KEY_PROFILE_COMPRESSED to false
            )
        )
        verifySuccessfulWriteMetric(expectedProps, rawBytes.size.toLong())
    }

    @Test
    fun `M drop the profile W writeManualProfile() {the trace changed after it was measured}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        @Forgery fakeLongTasks: List<ProfilerEvent.RumLongTaskEvent>,
        @Forgery fakeAnrs: List<ProfilerEvent.RumAnrEvent>,
        forge: Forge
    ) {
        // Given the raw trace grew after the size the reader was handed
        val rawBytes = forge.aString(size = 4096).toByteArray()
        val file = tmp.resolve(fakeResult.resultFilePath).apply { writeBytes(rawBytes) }
        testedDataWriterTest = ProfilingDataWriter(
            mockSdkCore,
            {
                CompressionResult.Raw(
                    rawBytes.size.toLong() - 1
                )
            }
        )

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
        assertThat(file.exists()).isFalse()
        verifyNoInteractions(mockEventBatchWriter)
    }

    // A successful write metric always reports the size of the trace it saved.
    private fun verifySuccessfulWriteMetric(expectedProps: Map<String, Any?>, expectedSizeBytes: Long) {
        argumentCaptor<Map<String, Any?>> {
            verify(mockInternalLogger).logMetric(any(), capture(), eq(MethodCallSamplingRate.ALL.rate), isNull())
            val actualProps = profiledWriteProps(firstValue)
            assertThat(actualProps[ProfilingDataWriter.KEY_PROFILE_SIZE_BYTES]).isEqualTo(expectedSizeBytes)
            assertThat(actualProps.minus(ProfilingDataWriter.KEY_PROFILE_SIZE_BYTES))
                .isEqualTo(profiledWriteProps(expectedProps).minus(ProfilingDataWriter.KEY_PROFILE_SIZE_BYTES))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun profiledWriteProps(metricProps: Map<String, Any?>): Map<String, Any?> =
        metricProps[ProfilingDataWriter.KEY_PROFILING_WRITE] as Map<String, Any?>

    private fun writeManualProfileWithZstd(
        fakeResult: PerfettoResult,
        file: File,
        fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        fakeAnrs: List<ProfilerEvent.RumAnrEvent>,
        fakeLongTasks: List<ProfilerEvent.RumLongTaskEvent>
    ): ProfilingBatchMetadata {
        testedDataWriterTest = ProfilingDataWriter(mockSdkCore)
        whenever(mockEventBatchWriter.write(any(), isNull(), eq(EventType.DEFAULT))) doReturn true
        testedDataWriterTest.writeManualProfile(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = fakeVitals,
            anrEvents = fakeAnrs,
            longTasks = fakeLongTasks
        )
        return argumentCaptor<RawBatchEvent> {
            verify(mockEventBatchWriter).write(capture(), isNull(), eq(EventType.DEFAULT))
        }.firstValue.let { checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(it.metadata)) }
    }
    private val ProfilingBatchMetadata.perfettoBytes: ByteArray
        get() = bytes.copyOfRange(perfettoOffset, rumMobileEventsOffset)
    private val ProfilingBatchMetadata.rumMobileEventsBytes: ByteArray
        get() = bytes.copyOfRange(rumMobileEventsOffset, bytes.size)
    private fun ByteArray.zstdDecompress(): ByteArray = ZstdInputStream(inputStream()).use { it.readBytes() }
    private fun successfulTriggerWriteProps(fakeResult: PerfettoResult) = mapOf(
        ProfilingTelemetry.KEY_METRIC_TYPE to ProfilingDataWriter.METRIC_TYPE_PROFILING_WRITE,
        ProfilingDataWriter.KEY_PROFILING_WRITE to mapOf(
            ProfilingDataWriter.KEY_DROPPED to false,
            ProfilingDataWriter.KEY_DROP_REASON to null,
            ProfilingTelemetry.KEY_START_REASON to fakeResult.startReason.value,
            ProfilingDataWriter.KEY_HAS_RUM_ERROR_ID to true,
            ProfilingDataWriter.KEY_PROFILE_COMPRESSED to false
        )
    )
    companion object {
        // 30 MiB, the intake limit, kept as a literal independent from the production constant
        private const val PROFILE_SIZE_LIMIT_BYTES = 31_457_280L

        // A pass-through trace result: the file bytes are encoded as they are on disk.
        // A pass-through trace result: the file bytes are encoded as they are on disk.
        private val identityCompressFile: (File) -> CompressionResult = { traceFile ->
            val sizeBytes = traceFile.length()
            when {
                sizeBytes <= 0L -> CompressionResult.Unreadable
                sizeBytes > PROFILE_SIZE_LIMIT_BYTES -> CompressionResult.TooLarge(sizeBytes)
                else -> CompressionResult.Raw(sizeBytes)
            }
        }

        private val ZSTD_MAGIC = byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte())
    }
}
