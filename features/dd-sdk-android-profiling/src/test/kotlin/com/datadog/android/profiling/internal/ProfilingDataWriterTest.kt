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
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.utils.formatIsoUtc
import com.datadog.android.profiling.assertj.ProfileEventAssert.Companion.assertThat
import com.datadog.android.profiling.assertj.RumMobileEventsAssert.Companion.assertThat
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.domain.ProfilingBatchMetadata
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.model.ProfileEvent
import com.datadog.android.profiling.model.RumMobileEvents
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
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

    lateinit var fakeByteArray: ByteArray

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @TempDir
    lateinit var tmp: File

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BeforeEach
    fun `set up`(forge: Forge) {
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
        fakeByteArray = forge.aString().toByteArray()
    }

    @Test
    fun `M write the result in a batch W write`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeTTID: ProfilerEvent.TTID
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(fakeByteArray)

        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            ttidEvent = fakeTTID
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
            "operation:launch"
        )
        fakeDatadogContext.appBuildId?.let {
            expectedTagList.add("build_id:${fakeDatadogContext.appBuildId}")
        }

        assertThat(actualEvent)
            .hasStart(formatIsoUtc(fakeResult.start))
            .hasEnd(formatIsoUtc(fakeResult.end))
            .hasAttachments(listOf("perfetto.proto"))
            .hasFamily(ProfileEvent.Family.ANDROID)
            .hasRuntime(ProfileEvent.Family.ANDROID)
            .hasVersion(4)
            .hasTags(expectedTagList)
            .hasApplicationId(fakeTTID.rumContext.applicationId)
            .hasSessionId(fakeTTID.rumContext.sessionId)
            .hasVitalIds(listOf(fakeTTID.vitalId))
            .hasVitalNames(listOf(fakeTTID.vitalName.orEmpty()))
            .apply {
                if (fakeTTID.rumContext.viewId != null && fakeTTID.rumContext.viewName != null) {
                    hasViewIds(listOf(fakeTTID.rumContext.viewId.orEmpty()))
                    hasViewNames(listOf(fakeTTID.rumContext.viewName.orEmpty()))
                } else {
                    hasViewIds(null)
                    hasViewNames(null)
                }
            }
        assertThat(argumentCaptor.firstValue.metadata).isEqualTo(
            fakeByteArray
        )
        verifyNoMoreInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M skip writing W write {can't read perfetto File}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeTTID: ProfilerEvent.TTID
    ) {
        // Given
        // Don't create the tmp file so it can't be found

        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult,
            ttidEvent = fakeTTID
        )

        // Then
        verifyNoMoreInteractions(mockInternalLogger, mockEventBatchWriter)
    }

    @Test
    fun `M skip writing W file is empty`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeTTID: ProfilerEvent.TTID
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(ByteArray(0))

        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            ttidEvent = fakeTTID
        )

        // Then
        verifyNoMoreInteractions(mockInternalLogger, mockEventBatchWriter)
    }

    @Test
    fun `M write launch profile with long task events W write {RUM long task events present}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeTTID: ProfilerEvent.TTID,
        @Forgery fakeLongTask: ProfilerEvent.RumLongTaskEvent
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(fakeByteArray)

        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            ttidEvent = fakeTTID,
            longTasks = listOf(fakeLongTask),
            anrEvents = emptyList()
        )

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = captor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )
        val profileEvent = ProfileEvent.fromJson(String(captor.firstValue.data))
        assertThat(profileEvent)
            .hasLongTaskIds(listOf(fakeLongTask.id))
            .hasErrorIds(emptyList())
        // metadata is still raw perfetto bytes for launch profiles
        assertThat(captor.firstValue.metadata).isEqualTo(fakeByteArray)
    }

    @Test
    fun `M write launch profile with ANR events W write {RUM ANR events present}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeTTID: ProfilerEvent.TTID,
        @Forgery fakeAnrEvent: ProfilerEvent.RumAnrEvent
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(fakeByteArray)

        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            ttidEvent = fakeTTID,
            longTasks = emptyList(),
            anrEvents = listOf(fakeAnrEvent)
        )

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = captor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )
        val profileEvent = ProfileEvent.fromJson(String(captor.firstValue.data))
        assertThat(profileEvent)
            .hasLongTaskIds(emptyList())
            .hasErrorIds(listOf(fakeAnrEvent.id))
        assertThat(captor.firstValue.metadata).isEqualTo(fakeByteArray)
    }

    @Test
    fun `M write continuous profile with rum events W writeContinuous`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeLongTask: ProfilerEvent.RumLongTaskEvent,
        @Forgery fakeAnrEvent: ProfilerEvent.RumAnrEvent
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(fakeByteArray)

        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            longTasks = listOf(fakeLongTask),
            anrEvents = listOf(fakeAnrEvent)
        )

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = captor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )
        val rawEvent = captor.firstValue
        val profileEvent = ProfileEvent.fromJson(String(rawEvent.data))
        val expectedTagList = arrayListOf(
            "service:${fakeDatadogContext.service}",
            "env:${fakeDatadogContext.env}",
            "version:${fakeDatadogContext.version}",
            "sdk_version:${fakeDatadogContext.sdkVersion}",
            "profiler_version:${fakeDatadogContext.sdkVersion}",
            "runtime_version:${fakeDatadogContext.deviceInfo.osVersion}",
            "operation:continuous"
        )
        fakeDatadogContext.appBuildId?.let {
            expectedTagList.add("build_id:${fakeDatadogContext.appBuildId}")
        }

        assertThat(profileEvent)
            .hasStart(formatIsoUtc(fakeResult.start))
            .hasEnd(formatIsoUtc(fakeResult.end))
            .hasAttachments(
                listOf(ProfilingDataWriter.PERFETTO_ATTACHMENT_NAME)
            )
            .hasFamily(ProfileEvent.Family.ANDROID)
            .hasRuntime(ProfileEvent.Family.ANDROID)
            .hasVersion(4)
            .hasTags(expectedTagList)
            .hasApplicationId(fakeLongTask.rumContext.applicationId)
            .hasSessionId(fakeLongTask.rumContext.sessionId)
            .hasLongTaskIds(listOf(fakeLongTask.id))
            .hasErrorIds(listOf(fakeAnrEvent.id))

        // Then
        val batchMetadata = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(rawEvent.metadata, mockInternalLogger))
        assertThat(batchMetadata.perfettoBytes).isEqualTo(fakeByteArray)
        val rumMobileEvents = RumMobileEvents.fromJson(String(batchMetadata.rumMobileEventsBytes, Charsets.UTF_8))
        assertThat(rumMobileEvents)
            .hasErrors(
                listOf(
                    RumMobileEvents.Error(
                        id = fakeAnrEvent.id,
                        startNs = TimeUnit.MILLISECONDS.toNanos(fakeAnrEvent.startMs),
                        durationNs = fakeAnrEvent.durationNs
                    )
                )
            )
            .hasLongTasks(
                listOf(
                    RumMobileEvents.LongTask(
                        id = fakeLongTask.id,
                        startNs = TimeUnit.MILLISECONDS.toNanos(fakeLongTask.startMs),
                        durationNs = fakeLongTask.durationNs
                    )
                )
            )
    }

    @Test
    fun `M skip writing W writeContinuous {no rum events}`(
        @Forgery fakeResult: PerfettoResult
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(fakeByteArray)

        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            longTasks = emptyList(),
            anrEvents = emptyList()
        )

        // Then
        verifyNoMoreInteractions(mockInternalLogger, mockEventBatchWriter)
    }

    @Test
    fun `M write W writeContinuous {only anrEvents non-empty}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeAnrEvent: ProfilerEvent.RumAnrEvent
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(fakeByteArray)

        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            longTasks = emptyList(),
            anrEvents = listOf(fakeAnrEvent)
        )

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = captor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )
        val rawEvent = captor.firstValue
        val profileEvent = ProfileEvent.fromJson(String(rawEvent.data))
        assertThat(profileEvent)
            .hasAttachments(
                listOf(ProfilingDataWriter.PERFETTO_ATTACHMENT_NAME)
            )
            .hasLongTaskIds(emptyList())
            .hasErrorIds(listOf(fakeAnrEvent.id))
        val batchMetadata = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(rawEvent.metadata, mockInternalLogger))
        assertThat(batchMetadata.perfettoBytes).isEqualTo(fakeByteArray)
        val rumMobileEvents = RumMobileEvents.fromJson(String(batchMetadata.rumMobileEventsBytes, Charsets.UTF_8))
        assertThat(rumMobileEvents)
            .hasErrors(
                listOf(
                    RumMobileEvents.Error(
                        id = fakeAnrEvent.id,
                        startNs = TimeUnit.MILLISECONDS.toNanos(fakeAnrEvent.startMs),
                        durationNs = fakeAnrEvent.durationNs
                    )
                )
            )
            .hasLongTasks(null)
    }

    @Test
    fun `M write W writeContinuous {only longTasks non-empty}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeLongTask: ProfilerEvent.RumLongTaskEvent
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(fakeByteArray)

        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            longTasks = listOf(fakeLongTask),
            anrEvents = emptyList()
        )

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = captor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )
        val rawEvent = captor.firstValue
        val profileEvent = ProfileEvent.fromJson(String(rawEvent.data))
        assertThat(profileEvent)
            .hasAttachments(
                listOf(ProfilingDataWriter.PERFETTO_ATTACHMENT_NAME)
            )
            .hasLongTaskIds(listOf(fakeLongTask.id))
            .hasErrorIds(emptyList())
        val batchMetadata = checkNotNull(ProfilingBatchMetadata.fromBytesOrNull(rawEvent.metadata, mockInternalLogger))
        assertThat(batchMetadata.perfettoBytes).isEqualTo(fakeByteArray)
        val rumMobileEvents = RumMobileEvents.fromJson(String(batchMetadata.rumMobileEventsBytes, Charsets.UTF_8))
        assertThat(rumMobileEvents)
            .hasLongTasks(
                listOf(
                    RumMobileEvents.LongTask(
                        id = fakeLongTask.id,
                        startNs = TimeUnit.MILLISECONDS.toNanos(fakeLongTask.startMs),
                        durationNs = fakeLongTask.durationNs
                    )
                )
            )
            .hasErrors(null)
    }

    @Test
    fun `M skip writing W writeContinuous {can't read perfetto file}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeLongTask: ProfilerEvent.RumLongTaskEvent
    ) {
        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult,
            longTasks = listOf(fakeLongTask),
            anrEvents = emptyList()
        )

        // Then
        verifyNoMoreInteractions(mockInternalLogger, mockEventBatchWriter)
    }
}
