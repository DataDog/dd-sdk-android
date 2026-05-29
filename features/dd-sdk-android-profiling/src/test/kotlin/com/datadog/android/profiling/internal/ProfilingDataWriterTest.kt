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
import com.datadog.android.profiling.assertj.RumMetadataEventsAssert.Companion.assertThat
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.domain.ProfilingBatchMetadata
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
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
    }

    @Test
    fun `M write the result in a batch W write`(
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
        val rumContext = fakeVitals.first().rumContext

        // When
        testedDataWriterTest.write(
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
    }

    @Test
    fun `M skip writing W write {can't read perfetto File}`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        @Forgery fakeLongTasks: List<ProfilerEvent.RumLongTaskEvent>,
        @Forgery fakeAnrs: List<ProfilerEvent.RumAnrEvent>
    ) {
        // Given
        // Don't create the tmp file so it can't be found

        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult,
            vitalEvents = fakeVitals,
            anrEvents = fakeAnrs,
            longTasks = fakeLongTasks
        )

        // Then
        verifyNoMoreInteractions(mockInternalLogger, mockEventBatchWriter)
    }

    @Test
    fun `M skip writing W file is empty`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeVitals: List<ProfilerEvent.RumVitalEvent>,
        @Forgery fakeLongTasks: List<ProfilerEvent.RumLongTaskEvent>,
        @Forgery fakeAnrs: List<ProfilerEvent.RumAnrEvent>
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(ByteArray(0))

        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = fakeVitals,
            anrEvents = fakeAnrs,
            longTasks = fakeLongTasks
        )

        // Then
        verifyNoMoreInteractions(mockInternalLogger, mockEventBatchWriter)
    }

    @Test
    fun `M skip writing W write {no rum events}`(
        @Forgery fakeResult: PerfettoResult,
        forge: Forge
    ) {
        // Given — perfetto file is readable but there's nothing to attach
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(forge.aString().toByteArray())

        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = emptyList(),
            anrEvents = emptyList(),
            longTasks = emptyList()
        )

        // Then
        verifyNoMoreInteractions(mockInternalLogger, mockEventBatchWriter)
    }

    @Test
    fun `M write the result in a batch W write {only vital events present}`(
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
        testedDataWriterTest.write(
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
        verifyNoMoreInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M delete result file W write {feature not initialized}`(
        @Forgery fakeResult: PerfettoResult,
        forge: Forge
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)) doReturn null
        val file = File(tmp, "fake_profile.perfetto-stack-sample")
        file.writeBytes(forge.aString().toByteArray())

        // When
        testedDataWriterTest.write(
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
    fun `M delete result file W write {events present}`(
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
        testedDataWriterTest.write(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = alignedVitals,
            anrEvents = emptyList(),
            longTasks = emptyList()
        )

        // Then
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `M delete result file W write {no rum events}`(
        @Forgery fakeResult: PerfettoResult,
        forge: Forge
    ) {
        // Given — file exists but there are no events, so buildRawBatchEvent returns null
        val file = File(tmp, "fake_profile.perfetto-stack-sample")
        file.writeBytes(forge.aString().toByteArray())

        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            vitalEvents = emptyList(),
            anrEvents = emptyList(),
            longTasks = emptyList()
        )

        // Then
        assertThat(file.exists()).isFalse()
        verifyNoInteractions(mockEventBatchWriter)
    }
}
