/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TracerProviderTest {

    private lateinit var testedTracerProvider: TracerProvider

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockTracingFeature: FeatureScope

    @Mock
    lateinit var mockGlobalTracer: DatadogTracer

    @Mock
    lateinit var mockLocalTracer: DatadogTracer

    @Mock
    lateinit var mockFirstPartyHostResolver: FirstPartyHostHeaderTypeResolver

    @StringForgery
    lateinit var fakeNetworkInstrumentationName: String

    private lateinit var fakeLocalHeaderTypes: Set<TracingHeaderType>
    private lateinit var fakeGlobalHeaderTypes: Set<TracingHeaderType>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeLocalHeaderTypes = forge.aList { aValueFrom(TracingHeaderType::class.java) }.toSet()
        fakeGlobalHeaderTypes = forge.aList { aValueFrom(TracingHeaderType::class.java) }.toSet()

        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.firstPartyHostResolver) doReturn mockFirstPartyHostResolver
        whenever(mockFirstPartyHostResolver.getAllHeaderTypes()) doReturn fakeGlobalHeaderTypes

        testedTracerProvider = TracerProvider(
            localTracerFactory = { _, _ -> mockLocalTracer },
            globalTracerProvider = { null }
        )
    }

    @Test
    fun `M return null and log W provideTracer() {tracing feature not registered}`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn null

        // When
        val result = testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // Then
        assertThat(result).isNull()
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                eq(null),
                eq(true),
                eq(null)
            )
            assertThat(firstValue()).contains(fakeNetworkInstrumentationName)
            assertThat(firstValue()).contains("TracingFeature")
        }
    }

    @Test
    fun `M return global tracer W provideTracer() {global tracer available}`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mockTracingFeature
        testedTracerProvider = TracerProvider(
            localTracerFactory = { _, _ -> mockLocalTracer },
            globalTracerProvider = { mockGlobalTracer }
        )

        // When
        val result = testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // Then
        assertThat(result).isSameAs(mockGlobalTracer)
    }

    @Test
    fun `M create and return local tracer W provideTracer() {no global tracer}`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mockTracingFeature
        var capturedHeaderTypes: Set<TracingHeaderType>? = null
        testedTracerProvider = TracerProvider(
            localTracerFactory = { _, headerTypes ->
                capturedHeaderTypes = headerTypes
                mockLocalTracer
            },
            globalTracerProvider = { null }
        )

        // When
        val result = testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // Then
        assertThat(result).isSameAs(mockLocalTracer)
        assertThat(capturedHeaderTypes).isEqualTo(fakeLocalHeaderTypes + fakeGlobalHeaderTypes)
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                eq(null),
                eq(false),
                eq(null)
            )
            assertThat(firstValue()).contains(fakeNetworkInstrumentationName)
            assertThat(firstValue()).contains("local tracer")
        }
    }

    @Test
    fun `M reuse local tracer W provideTracer() called multiple times {no global tracer}`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mockTracingFeature
        var factoryCallCount = 0
        testedTracerProvider = TracerProvider(
            localTracerFactory = { _, _ ->
                factoryCallCount++
                mockLocalTracer
            },
            globalTracerProvider = { null }
        )

        // When
        val result1 = testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )
        val result2 = testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // Then
        assertThat(result1).isSameAs(mockLocalTracer)
        assertThat(result2).isSameAs(mockLocalTracer)
        assertThat(factoryCallCount).isEqualTo(1)
    }

    @Test
    fun `M clear local tracer W provideTracer() {global tracer becomes available}`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mockTracingFeature
        var globalTracerAvailable = false
        testedTracerProvider = TracerProvider(
            localTracerFactory = { _, _ -> mockLocalTracer },
            globalTracerProvider = { if (globalTracerAvailable) mockGlobalTracer else null }
        )

        // When - first call without global tracer
        val result1 = testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // Then
        assertThat(result1).isSameAs(mockLocalTracer)

        // When - second call with global tracer available
        globalTracerAvailable = true
        val result2 = testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // Then
        assertThat(result2).isSameAs(mockGlobalTracer)
    }

    @Test
    fun `M create new local tracer W provideTracer() {global tracer becomes unavailable after being available}`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mockTracingFeature
        var globalTracerAvailable = true
        var factoryCallCount = 0
        testedTracerProvider = TracerProvider(
            localTracerFactory = { _, _ ->
                factoryCallCount++
                mockLocalTracer
            },
            globalTracerProvider = { if (globalTracerAvailable) mockGlobalTracer else null }
        )

        // When - first call with global tracer
        val result1 = testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // Then
        assertThat(result1).isSameAs(mockGlobalTracer)
        assertThat(factoryCallCount).isEqualTo(0)

        // When - second call without global tracer
        globalTracerAvailable = false
        val result2 = testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // Then
        assertThat(result2).isSameAs(mockLocalTracer)
        assertThat(factoryCallCount).isEqualTo(1)
    }

    @Test
    fun `M log warning with onlyOnce flag W provideTracer() called multiple times {tracing feature not registered}`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn null

        // When
        testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )
        testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // Then
        verify(mockInternalLogger, times(2)).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            any<() -> String>(),
            eq(null),
            eq(true),
            eq(null)
        )
    }

    @Test
    fun `M create local tracer only once W provideTracer() called concurrently {no global tracer}`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mockTracingFeature
        val factoryCallCount = AtomicInteger(0)
        testedTracerProvider = TracerProvider(
            localTracerFactory = { _, _ ->
                factoryCallCount.incrementAndGet()
                mockLocalTracer
            },
            globalTracerProvider = { null }
        )

        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val results = mutableListOf<DatadogTracer?>()

        // When
        repeat(threadCount) {
            executor.submit {
                startLatch.await()
                val result = testedTracerProvider.provideTracer(
                    mockSdkCore,
                    fakeLocalHeaderTypes,
                    fakeNetworkInstrumentationName
                )
                synchronized(results) {
                    results.add(result)
                }
                doneLatch.countDown()
            }
        }
        startLatch.countDown()
        doneLatch.await(5, TimeUnit.SECONDS)
        executor.shutdown()

        // Then
        assertThat(factoryCallCount.get()).isEqualTo(1)
        assertThat(results).hasSize(threadCount)
        results.forEach { assertThat(it).isSameAs(mockLocalTracer) }
    }

    @Test
    fun `M not interact with firstPartyHostResolver W provideTracer() {tracing feature not registered}`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn null

        // When
        testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // Then
        verifyNoInteractions(mockFirstPartyHostResolver)
    }

    @Test
    fun `M not interact with firstPartyHostResolver W provideTracer() {global tracer available}`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mockTracingFeature
        testedTracerProvider = TracerProvider(
            localTracerFactory = { _, _ -> mockLocalTracer },
            globalTracerProvider = { mockGlobalTracer }
        )

        // When
        testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // Then
        verifyNoInteractions(mockFirstPartyHostResolver)
    }

    @Test
    fun `M create new local tracer each time W provideTracer() {global tracer cycles availability}`(
        @Mock mockSecondLocalTracer: DatadogTracer
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mockTracingFeature
        var globalTracerAvailable = false
        var localTracerInstance = 0
        val localTracers = listOf(mockLocalTracer, mockSecondLocalTracer)
        testedTracerProvider = TracerProvider(
            localTracerFactory = { _, _ ->
                localTracers[localTracerInstance++ % 2]
            },
            globalTracerProvider = { if (globalTracerAvailable) mockGlobalTracer else null }
        )

        // When - first call: no global, creates first local tracer
        val result1 = testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // Then
        assertThat(result1).isSameAs(mockLocalTracer)

        // When - second call: global becomes available, clears local
        globalTracerAvailable = true
        val result2 = testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // Then
        assertThat(result2).isSameAs(mockGlobalTracer)

        // When - third call: global becomes unavailable, creates second local tracer
        globalTracerAvailable = false
        val result3 = testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // Then - should create NEW local tracer since previous was cleared
        assertThat(result3).isSameAs(mockSecondLocalTracer)
        assertThat(localTracerInstance).isEqualTo(2)
    }

    @Test
    fun `M reuse same local tracer W provideTracer() {global tracer never available between calls}`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mockTracingFeature
        var factoryCallCount = 0
        testedTracerProvider = TracerProvider(
            localTracerFactory = { _, _ ->
                factoryCallCount++
                mockLocalTracer
            },
            globalTracerProvider = { null }
        )

        // When - multiple calls without global tracer ever being available
        val results = (1..5).map {
            testedTracerProvider.provideTracer(
                mockSdkCore,
                fakeLocalHeaderTypes,
                fakeNetworkInstrumentationName
            )
        }

        // Then - factory should be called only once
        assertThat(factoryCallCount).isEqualTo(1)
        results.forEach { assertThat(it).isSameAs(mockLocalTracer) }
    }

    @Test
    fun `M log warning for each local tracer creation W provideTracer() {global tracer cycles}`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mockTracingFeature
        var globalTracerAvailable = false
        testedTracerProvider = TracerProvider(
            localTracerFactory = { _, _ -> mockLocalTracer },
            globalTracerProvider = { if (globalTracerAvailable) mockGlobalTracer else null }
        )

        // When - create local tracer
        testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // When - switch to global
        globalTracerAvailable = true
        testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // When - switch back to local (new creation)
        globalTracerAvailable = false
        testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // Then - should log warning twice (once for each local tracer creation)
        verify(mockInternalLogger, times(2)).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            any<() -> String>(),
            eq(null),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M handle concurrent switch from local to global W provideTracer()`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mockTracingFeature
        var globalTracerAvailable = false
        val factoryCallCount = AtomicInteger(0)
        testedTracerProvider = TracerProvider(
            localTracerFactory = { _, _ ->
                factoryCallCount.incrementAndGet()
                mockLocalTracer
            },
            globalTracerProvider = { if (globalTracerAvailable) mockGlobalTracer else null }
        )

        // First, create local tracer
        val initialResult = testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )
        assertThat(initialResult).isSameAs(mockLocalTracer)

        // Now make global available
        globalTracerAvailable = true

        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val results = mutableListOf<DatadogTracer?>()

        // When - concurrent access after global becomes available
        repeat(threadCount) {
            executor.submit {
                startLatch.await()
                val result = testedTracerProvider.provideTracer(
                    mockSdkCore,
                    fakeLocalHeaderTypes,
                    fakeNetworkInstrumentationName
                )
                synchronized(results) {
                    results.add(result)
                }
                doneLatch.countDown()
            }
        }
        startLatch.countDown()
        doneLatch.await(5, TimeUnit.SECONDS)
        executor.shutdown()

        // Then - all threads should get global tracer
        assertThat(results).hasSize(threadCount)
        results.forEach { assertThat(it).isSameAs(mockGlobalTracer) }
        // Factory was called only once (initial local tracer creation)
        assertThat(factoryCallCount.get()).isEqualTo(1)
    }

    @Test
    fun `M use updated header types W provideTracer() {new local tracer created after global cleared}`(
        forge: Forge
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mockTracingFeature
        var globalTracerAvailable = false
        val capturedHeaderTypes = mutableListOf<Set<TracingHeaderType>>()
        testedTracerProvider = TracerProvider(
            localTracerFactory = { _, headerTypes ->
                capturedHeaderTypes.add(headerTypes)
                mockLocalTracer
            },
            globalTracerProvider = { if (globalTracerAvailable) mockGlobalTracer else null }
        )

        // When - first local tracer creation
        testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // When - switch to global
        globalTracerAvailable = true
        testedTracerProvider.provideTracer(
            mockSdkCore,
            fakeLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // When - switch back to local with different header types
        globalTracerAvailable = false
        val newLocalHeaderTypes = forge.aList { aValueFrom(TracingHeaderType::class.java) }.toSet()
        val newGlobalHeaderTypes = forge.aList { aValueFrom(TracingHeaderType::class.java) }.toSet()
        whenever(mockFirstPartyHostResolver.getAllHeaderTypes()) doReturn newGlobalHeaderTypes

        testedTracerProvider.provideTracer(
            mockSdkCore,
            newLocalHeaderTypes,
            fakeNetworkInstrumentationName
        )

        // Then - second local tracer should use new header types
        assertThat(capturedHeaderTypes).hasSize(2)
        assertThat(capturedHeaderTypes[0]).isEqualTo(fakeLocalHeaderTypes + fakeGlobalHeaderTypes)
        assertThat(capturedHeaderTypes[1]).isEqualTo(newLocalHeaderTypes + newGlobalHeaderTypes)
    }

    @Test
    fun `M not create local tracer W provideTracer() {global tracer always available}`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mockTracingFeature
        var factoryCallCount = 0
        testedTracerProvider = TracerProvider(
            localTracerFactory = { _, _ ->
                factoryCallCount++
                mockLocalTracer
            },
            globalTracerProvider = { mockGlobalTracer }
        )

        // When - multiple calls with global tracer always available
        val results = (1..5).map {
            testedTracerProvider.provideTracer(
                mockSdkCore,
                fakeLocalHeaderTypes,
                fakeNetworkInstrumentationName
            )
        }

        // Then - factory should never be called
        assertThat(factoryCallCount).isEqualTo(0)
        results.forEach { assertThat(it).isSameAs(mockGlobalTracer) }
        verifyNoInteractions(mockFirstPartyHostResolver)
    }
}
