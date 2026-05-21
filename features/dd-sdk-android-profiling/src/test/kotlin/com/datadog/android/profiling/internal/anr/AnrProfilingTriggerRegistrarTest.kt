/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.anr

import android.content.Context
import android.os.ProfilingManager
import android.os.ProfilingResult
import android.os.ProfilingTrigger
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.profiling.ProfilingAnrDetectedEvent
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.internal.utils.ThreadDumper
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.function.Consumer

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class AnrProfilingTriggerRegistrarTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockService: ProfilingManager

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Mock
    private lateinit var mockExecutorService: ExecutorService

    @Mock
    private lateinit var mockAnrListener: AnrListener

    @Mock
    private lateinit var mockTimeProvider: TimeProvider

    private lateinit var testedRegistrar: AnrProfilingTriggerRegistrar

    @BeforeEach
    fun `set up`() {
        whenever(mockContext.getSystemService(ProfilingManager::class.java)).doReturn(mockService)
        testedRegistrar = AnrProfilingTriggerRegistrar(
            timeProvider = mockTimeProvider,
            executorService = mockExecutorService
        )
        val mockTrigger = mock<ProfilingTrigger> {
            on { triggerType } doReturn ProfilingTrigger.TRIGGER_TYPE_ANR
        }
        testedRegistrar.triggerFactory = { mockTrigger }
        testedRegistrar.threadDumper = ThreadDumper(
            mainThreadProvider = { Thread.currentThread() }
        )
        testedRegistrar.internalLogger = mockInternalLogger
    }

    @Test
    fun `M register system ANR trigger W register()`() {
        // When
        testedRegistrar.register(mockContext, mockAnrListener)

        // Then
        val triggersCaptor = argumentCaptor<List<ProfilingTrigger>>()
        verify(mockService).addProfilingTriggers(triggersCaptor.capture())
        assertThat(triggersCaptor.firstValue)
            .singleElement()
            .extracting { it.triggerType }
            .isEqualTo(ProfilingTrigger.TRIGGER_TYPE_ANR)
        verify(mockService).registerForAllProfilingResults(
            eq(mockExecutorService),
            any<Consumer<ProfilingResult>>()
        )
    }

    @Test
    fun `M register only once W register() {called twice}`() {
        // When
        testedRegistrar.register(mockContext, mockAnrListener)
        testedRegistrar.register(mockContext, mockAnrListener)

        // Then
        verify(mockService).addProfilingTriggers(any())
        verify(mockService).registerForAllProfilingResults(
            any<Executor>(),
            any<Consumer<ProfilingResult>>()
        )
    }

    @Test
    fun `M ignore result W trigger callback fires {non-ANR trigger type}`() {
        // Given
        testedRegistrar.register(mockContext, mockAnrListener)
        val triggerCallbackCaptor = argumentCaptor<Consumer<ProfilingResult>>()
        verify(mockService).registerForAllProfilingResults(any(), triggerCallbackCaptor.capture())
        val nonAnrResult = mock<ProfilingResult> {
            on { triggerType } doReturn ProfilingTrigger.TRIGGER_TYPE_NONE
        }

        // When
        triggerCallbackCaptor.firstValue.accept(nonAnrResult)

        // Then
        verify(mockAnrListener, never()).onAnrDetected(any())
    }

    @Test
    fun `M deliver empty allThreads W getAllStackTraces throws`(
        @TempDir tempDir: File
    ) {
        // Given
        val tmpFile = File(tempDir, "result.trace").apply { writeText("placeholder") }
        val creationTimeMs = Files.readAttributes(
            Paths.get(tmpFile.absolutePath),
            BasicFileAttributes::class.java
        ).creationTime().toMillis()
        whenever(mockTimeProvider.getDeviceTimestampMillis()).doReturn(creationTimeMs)
        testedRegistrar.threadDumper = ThreadDumper(
            mainThreadProvider = { Thread.currentThread() },
            allStackTracesProvider = { throw RuntimeException("boom") }
        )
        testedRegistrar.register(mockContext, mockAnrListener)
        val triggerCallbackCaptor = argumentCaptor<Consumer<ProfilingResult>>()
        verify(mockService).registerForAllProfilingResults(any(), triggerCallbackCaptor.capture())
        val anrResult = mock<ProfilingResult> {
            on { triggerType } doReturn ProfilingTrigger.TRIGGER_TYPE_ANR
            on { resultFilePath } doReturn tmpFile.absolutePath
        }

        // When
        triggerCallbackCaptor.firstValue.accept(anrResult)

        // Then
        val captor = argumentCaptor<ProfilingAnrDetectedEvent>()
        verify(mockAnrListener).onAnrDetected(captor.capture())
        assertThat(captor.firstValue.allThreads).isEmpty()
    }

    @Test
    fun `M remove system trigger W unregister()`() {
        // Given
        testedRegistrar.register(mockContext, mockAnrListener)

        // When
        testedRegistrar.unregister(mockContext)

        // Then
        val triggerTypesCaptor = argumentCaptor<IntArray>()
        verify(mockService).removeProfilingTriggersByType(triggerTypesCaptor.capture())
        assertThat(triggerTypesCaptor.firstValue).containsExactly(ProfilingTrigger.TRIGGER_TYPE_ANR)
        verify(mockService).unregisterForAllProfilingResults(any())
    }

    @Test
    fun `M skip system call W unregister() {not registered}`() {
        // When
        testedRegistrar.unregister(mockContext)

        // Then
        verify(mockService, never()).removeProfilingTriggersByType(any())
        verify(mockService, never()).unregisterForAllProfilingResults(any())
    }

    @Test
    fun `M re-register after unregister W register() called again`() {
        // Given
        testedRegistrar.register(mockContext, mockAnrListener)
        testedRegistrar.unregister(mockContext)

        // When
        testedRegistrar.register(mockContext, mockAnrListener)

        // Then
        verify(mockService, times(2)).addProfilingTriggers(any())
        verify(mockService, times(2)).registerForAllProfilingResults(any(), any())
    }

    @Test
    fun `M log warning and stay unregistered W register() {ProfilingManager service unavailable}`() {
        // Given
        whenever(mockContext.getSystemService(ProfilingManager::class.java)).doReturn(null)

        // When
        testedRegistrar.register(mockContext, mockAnrListener)

        // Then
        verify(mockService, never()).addProfilingTriggers(any())
        verify(mockService, never()).registerForAllProfilingResults(any(), any())
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            any<() -> String>(),
            isNull(),
            eq(false),
            isNull()
        )
    }

    @Test
    fun `M log warning and keep registered W unregister() {ProfilingManager service unavailable}`() {
        // Given
        testedRegistrar.register(mockContext, mockAnrListener)
        whenever(mockContext.getSystemService(ProfilingManager::class.java)).doReturn(null)

        // When
        testedRegistrar.unregister(mockContext)

        // Then
        verify(mockService, never()).removeProfilingTriggersByType(any())
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            any<() -> String>(),
            isNull(),
            eq(false),
            isNull()
        )
        // Retry path open: once the service is reachable again, unregister actually runs.
        reset(mockInternalLogger)
        whenever(mockContext.getSystemService(ProfilingManager::class.java)).doReturn(mockService)
        testedRegistrar.unregister(mockContext)
        verify(mockService).removeProfilingTriggersByType(any())
    }

    @Test
    fun `M delete result file W trigger callback fires {ANR result has filePath}`(
        @TempDir tempDir: File
    ) {
        // Given
        val tmpFile = File(tempDir, "result.trace").apply { writeText("placeholder") }
        testedRegistrar.register(mockContext, mockAnrListener)
        val triggerCallbackCaptor = argumentCaptor<Consumer<ProfilingResult>>()
        verify(mockService).registerForAllProfilingResults(any(), triggerCallbackCaptor.capture())
        val anrResult = mock<ProfilingResult> {
            on { triggerType } doReturn ProfilingTrigger.TRIGGER_TYPE_ANR
            on { resultFilePath } doReturn tmpFile.absolutePath
        }

        // When
        triggerCallbackCaptor.firstValue.accept(anrResult)

        // Then
        assertThat(tmpFile.exists()).isFalse
    }

    @Test
    fun `M drop ANR event and log warning W trigger callback fires {result file missing}`() {
        // Given
        testedRegistrar.register(mockContext, mockAnrListener)
        val triggerCallbackCaptor = argumentCaptor<Consumer<ProfilingResult>>()
        verify(mockService).registerForAllProfilingResults(any(), triggerCallbackCaptor.capture())
        val anrResult = mock<ProfilingResult> {
            on { triggerType } doReturn ProfilingTrigger.TRIGGER_TYPE_ANR
            on { resultFilePath } doReturn "/path/does/not/exist.trace"
        }

        // When
        triggerCallbackCaptor.firstValue.accept(anrResult)

        // Then
        verify(mockAnrListener, never()).onAnrDetected(any())
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            any<() -> String>(),
            any<Throwable>(),
            eq(false),
            isNull()
        )
    }

    @Test
    fun `M propagate logger to threadDumper W internalLogger setter`() {
        // Given
        val anotherLogger = mock<InternalLogger>()

        // When
        testedRegistrar.internalLogger = anotherLogger

        // Then
        assertThat(testedRegistrar.threadDumper.internalLogger).isSameAs(anotherLogger)
    }

    @Test
    fun `M dispatch to listener W trigger callback fires {delay below threshold}`(
        @TempDir tempDir: File,
        @LongForgery(min = 0L, max = 1_000L) fakeDelayMs: Long
    ) {
        // Given
        val tmpFile = File(tempDir, "result.trace").apply { writeText("placeholder") }
        val creationTimeMs = Files.readAttributes(
            Paths.get(tmpFile.absolutePath),
            BasicFileAttributes::class.java
        ).creationTime().toMillis()
        val fakeNow = creationTimeMs + fakeDelayMs
        whenever(mockTimeProvider.getDeviceTimestampMillis()).doReturn(fakeNow)
        testedRegistrar.register(mockContext, mockAnrListener)
        val triggerCallbackCaptor = argumentCaptor<Consumer<ProfilingResult>>()
        verify(mockService).registerForAllProfilingResults(any(), triggerCallbackCaptor.capture())
        val anrResult = mock<ProfilingResult> {
            on { triggerType } doReturn ProfilingTrigger.TRIGGER_TYPE_ANR
            on { resultFilePath } doReturn tmpFile.absolutePath
        }

        // When
        triggerCallbackCaptor.firstValue.accept(anrResult)

        // Then
        val captor = argumentCaptor<ProfilingAnrDetectedEvent>()
        verify(mockAnrListener).onAnrDetected(captor.capture())
        assertThat(captor.firstValue.detectedAtMs).isEqualTo(fakeNow)
    }

    @Test
    fun `M drop ANR event W trigger callback fires {delay above threshold}`(
        @TempDir tempDir: File,
        @LongForgery(min = 1_001L, max = 60_000L) fakeDelayMs: Long
    ) {
        // Given
        val tmpFile = File(tempDir, "result.trace").apply { writeText("placeholder") }
        val creationTimeMs = Files.readAttributes(
            Paths.get(tmpFile.absolutePath),
            BasicFileAttributes::class.java
        ).creationTime().toMillis()
        val fakeNow = creationTimeMs + fakeDelayMs
        whenever(mockTimeProvider.getDeviceTimestampMillis()).doReturn(fakeNow)
        testedRegistrar.register(mockContext, mockAnrListener)
        val triggerCallbackCaptor = argumentCaptor<Consumer<ProfilingResult>>()
        verify(mockService).registerForAllProfilingResults(any(), triggerCallbackCaptor.capture())
        val anrResult = mock<ProfilingResult> {
            on { triggerType } doReturn ProfilingTrigger.TRIGGER_TYPE_ANR
            on { resultFilePath } doReturn tmpFile.absolutePath
        }

        // When
        triggerCallbackCaptor.firstValue.accept(anrResult)

        // Then
        verify(mockAnrListener, never()).onAnrDetected(any())
        val propsCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.TELEMETRY),
            any<() -> String>(),
            isNull(),
            eq(false),
            propsCaptor.capture()
        )
        assertThat(propsCaptor.firstValue).containsEntry("delay_ms", fakeDelayMs)
    }
}
