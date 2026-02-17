/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import android.content.Context
import android.os.ProfilingManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.internal.data.SharedPreferencesStorage
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.NoOpProfiler
import com.datadog.android.profiling.internal.Profiler
import com.datadog.android.profiling.internal.ProfilingFeature
import com.datadog.android.profiling.internal.ProfilingStorage
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

@OptIn(ExperimentalProfilingApi::class)
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class ProfilingTest {

    @Mock
    private lateinit var mockSdkCore: InternalSdkCore

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockProfilingExecutor: ExecutorService

    @Mock
    private lateinit var mockProfilingManager: ProfilingManager

    @Mock
    private lateinit var mockSharedPreferencesStorage: SharedPreferencesStorage

    @Forgery
    private lateinit var fakeConfiguration: ProfilingConfiguration

    @StringForgery
    private lateinit var fakeInstanceName: String

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.name) doReturn fakeInstanceName
        whenever(mockSdkCore.createSingleThreadExecutorService(any())) doReturn mockProfilingExecutor
        whenever(mockContext.getSystemService(ProfilingManager::class.java)) doReturn mockProfilingManager
        ProfilingStorage.sharedPreferencesStorage = mockSharedPreferencesStorage
    }

    @AfterEach
    fun `tear down`() {
        resetProfilerField()
        ProfilingStorage.sharedPreferencesStorage = null
    }

    @Test
    fun `M use PerfettoProfiler W enable called before start`() {
        // Given
        val sdkInstanceNames = setOf(fakeInstanceName)

        // When
        Profiling.enable(fakeConfiguration, mockSdkCore)
        Profiling.start(mockContext, sdkInstanceNames)

        // Then
        verify(mockSdkCore).registerFeature(any<ProfilingFeature>())

        assertThat(Profiling.profiler).isNotInstanceOf(NoOpProfiler::class.java)
        assertThat(Profiling.profiler).isInstanceOf(PerfettoProfiler::class.java)
    }

    @Test
    fun `M use PerfettoProfiler W enable called but start not called`() {
        // When
        Profiling.enable(fakeConfiguration, mockSdkCore)

        // Then
        verify(mockSdkCore).registerFeature(any<ProfilingFeature>())

        assertThat(Profiling.profiler).isNotInstanceOf(NoOpProfiler::class.java)
        assertThat(Profiling.profiler).isInstanceOf(PerfettoProfiler::class.java)
    }

    @Test
    fun `M use PerfettoProfiler W start called before enable`() {
        // Given
        val sdkInstanceNames = setOf(fakeInstanceName)

        // When
        Profiling.start(mockContext, sdkInstanceNames)
        Profiling.enable(fakeConfiguration, mockSdkCore)

        // Then
        verify(mockSdkCore).registerFeature(any<ProfilingFeature>())

        assertThat(Profiling.profiler).isNotInstanceOf(NoOpProfiler::class.java)
        assertThat(Profiling.profiler).isInstanceOf(PerfettoProfiler::class.java)
    }

    @Test
    fun `M keep same profiler instance W start called multiple times`() {
        // Given
        val sdkInstanceNames = setOf(fakeInstanceName)

        // When
        Profiling.start(mockContext, sdkInstanceNames)

        val firstProfiler = Profiling.profiler

        Profiling.start(mockContext, sdkInstanceNames)

        val secondProfiler = Profiling.profiler

        // Then
        assertThat(firstProfiler).isNotInstanceOf(NoOpProfiler::class.java)
        assertThat(secondProfiler).isNotInstanceOf(NoOpProfiler::class.java)
        assertThat(firstProfiler).isSameAs(secondProfiler)
    }

    @Test
    fun `M start profiler W call Profiling start(with instance names)`() {
        // Given
        val sdkInstanceNames = setOf(fakeInstanceName)
        val mockProfiler = mock<Profiler>()
        Profiling.profiler = mockProfiler
        Profiling.isProfilerInitialized.set(true)

        // When
        Profiling.start(mockContext, sdkInstanceNames)

        // Then
        verify(mockProfiler).start(mockContext, sdkInstanceNames)
    }

    @Test
    fun `M start profiler W call Profiling start(with sdk core)`() {
        // Given
        val mockProfiler = mock<Profiler>()
        Profiling.profiler = mockProfiler
        Profiling.isProfilerInitialized.set(true)

        // When
        Profiling.start(mockContext, mockSdkCore)

        // Then
        verify(mockProfiler).start(mockContext, setOf(fakeInstanceName))
    }

    @Test
    fun `M stop profiler W call Profiling stop`() {
        // Given
        val mockProfiler = mock<Profiler>()
        Profiling.profiler = mockProfiler
        Profiling.isProfilerInitialized.set(true)

        // When
        Profiling.start(mockContext, mockSdkCore)
        Profiling.stop(mockSdkCore)

        // Then
        verify(mockProfiler).stop(fakeInstanceName)
    }

    private fun resetProfilerField() {
        Profiling.profiler = NoOpProfiler()
        Profiling.isProfilerInitialized.set(false)
    }
}
