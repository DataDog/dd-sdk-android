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
import com.datadog.android.internal.profiling.TTIDRumContext
import com.datadog.android.internal.utils.formatIsoUtc
import com.datadog.android.profiling.assertj.ProfileEventAssert.Companion.assertThat
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.model.ProfileEvent
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
        @Forgery fakeTTIDRumContext: TTIDRumContext
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(fakeByteArray)

        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            ttidRumContext = fakeTTIDRumContext
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
            .hasFamily("android")
            .hasRuntime("android")
            .hasVersion("4")
            .hasTags(expectedTagList)
            .hasApplicationId(fakeTTIDRumContext.applicationId)
            .hasSessionId(fakeTTIDRumContext.sessionId)
            .hasVitalId(fakeTTIDRumContext.vitalId)
            .apply {
                if (fakeTTIDRumContext.viewId != null && fakeTTIDRumContext.viewName != null) {
                    hasViewId(fakeTTIDRumContext.viewId)
                    hasViewName(fakeTTIDRumContext.viewName)
                } else {
                    hasViewId(null)
                    hasViewName(null)
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
        @Forgery fakeTTIDRumContext: TTIDRumContext
    ) {
        // Given
        // Don't create the tmp file so it can't be found

        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult,
            ttidRumContext = fakeTTIDRumContext
        )

        // Then
        verifyNoMoreInteractions(mockInternalLogger, mockEventBatchWriter)
    }

    @Test
    fun `M skip writing W file is empty`(
        @Forgery fakeResult: PerfettoResult,
        @Forgery fakeTTIDRumContext: TTIDRumContext
    ) {
        // Given
        val file = tmp.resolve(fakeResult.resultFilePath)
        file.writeBytes(ByteArray(0))

        // When
        testedDataWriterTest.write(
            profilingResult = fakeResult.copy(resultFilePath = file.absolutePath),
            ttidRumContext = fakeTTIDRumContext
        )

        // Then
        verifyNoMoreInteractions(mockInternalLogger, mockEventBatchWriter)
    }
}
