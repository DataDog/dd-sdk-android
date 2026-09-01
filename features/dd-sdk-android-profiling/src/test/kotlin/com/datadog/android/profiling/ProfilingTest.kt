/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import android.content.Context
import android.content.pm.PackageManager
import android.os.ProfilingManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.internal.data.SharedPreferencesStorage
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.NoOpProfiler
import com.datadog.android.profiling.internal.Profiler
import com.datadog.android.profiling.internal.ProfilingFeature
import com.datadog.android.profiling.internal.ProfilingStartReason
import com.datadog.android.profiling.internal.ProfilingStorage
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler
import com.datadog.android.profiling.utils.config.MainLooperTestConfiguration
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale
import java.util.concurrent.ExecutorService

@OptIn(ExperimentalProfilingApi::class)
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
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
    private lateinit var mockPackageManager: PackageManager

    @Mock
    private lateinit var mockProfilingExecutor: ExecutorService

    @Mock
    private lateinit var mockTimeProvider: TimeProvider

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
        whenever(mockSdkCore.timeProvider) doReturn mockTimeProvider
        whenever(mockContext.getSystemService(ProfilingManager::class.java)) doReturn mockProfilingManager
        whenever(mockContext.packageManager) doReturn mockPackageManager
        ProfilingStorage.sharedPreferencesStorage = mockSharedPreferencesStorage
    }

    @AfterEach
    fun `tear down`() {
        resetProfilerField()
        Profiling.currentRegisteredCore = null
        ProfilingStorage.sharedPreferencesStorage = null
    }

    @Test
    fun `M use PerfettoProfiler W enable called before start`() {
        // When
        Profiling.enable(fakeConfiguration, mockSdkCore)
        Profiling.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())

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
        // When
        Profiling.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())
        Profiling.enable(fakeConfiguration, mockSdkCore)

        // Then
        verify(mockSdkCore).registerFeature(any<ProfilingFeature>())

        assertThat(Profiling.profiler).isNotInstanceOf(NoOpProfiler::class.java)
        assertThat(Profiling.profiler).isInstanceOf(PerfettoProfiler::class.java)
    }

    @Test
    fun `M keep same profiler instance W start called multiple times`() {
        // When
        Profiling.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())

        val firstProfiler = Profiling.profiler

        Profiling.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())

        val secondProfiler = Profiling.profiler

        // Then
        assertThat(firstProfiler).isNotInstanceOf(NoOpProfiler::class.java)
        assertThat(secondProfiler).isNotInstanceOf(NoOpProfiler::class.java)
        assertThat(firstProfiler).isSameAs(secondProfiler)
    }

    @Test
    fun `M start profiler W call Profiling start`() {
        // Given
        val mockProfiler = mock<Profiler>()
        Profiling.profiler = mockProfiler
        Profiling.isProfilerInitialized.set(true)

        // When
        Profiling.start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())

        // Then
        verify(mockProfiler).start(mockContext, ProfilingStartReason.APPLICATION_LAUNCH, emptyMap())
    }

    @Test
    fun `M stop profiler W call Profiling stop`() {
        // Given
        val mockProfiler = mock<Profiler>()
        Profiling.profiler = mockProfiler
        Profiling.isProfilerInitialized.set(true)

        // When
        Profiling.stop()

        // Then
        verify(mockProfiler).stop()
    }

    @Test
    fun `M warn and skip W enable { profiling already registered with another active core }`(
        @StringForgery fakeCore1Name: String
    ) {
        // Given
        val mockCore1 = mock<InternalSdkCore>()
        val mockCore2 = mock<InternalSdkCore>()
        whenever(mockCore1.isCoreActive()) doReturn true
        whenever(mockCore1.name) doReturn fakeCore1Name
        whenever(mockCore1.internalLogger) doReturn mockInternalLogger
        whenever(mockCore1.timeProvider) doReturn mockTimeProvider
        whenever(mockCore2.internalLogger) doReturn mockInternalLogger
        whenever(mockCore2.timeProvider) doReturn mockTimeProvider
        Profiling.enable(fakeConfiguration, mockCore1)

        // When
        Profiling.enable(fakeConfiguration, mockCore2)

        // Then
        verify(mockCore1).registerFeature(any<ProfilingFeature>())
        verify(mockCore2, never()).registerFeature(any())
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.USER),
            message = Profiling.IS_ALREADY_REGISTERED_USER_WARNING.format(Locale.US, fakeCore1Name)
        )
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.DEBUG,
            targets = listOf(InternalLogger.Target.TELEMETRY),
            message = Profiling.IS_ALREADY_REGISTERED_WARNING
        )
        assertThat(Profiling.currentRegisteredCore?.get()).isEqualTo(mockCore1)
    }

    @Test
    fun `M allow changing cores W enable { profiling enabled but old core inactive }`() {
        // Given
        val mockCore1 = mock<InternalSdkCore>()
        val mockCore2 = mock<InternalSdkCore>()
        whenever(mockCore1.internalLogger) doReturn mockInternalLogger
        whenever(mockCore1.timeProvider) doReturn mockTimeProvider
        whenever(mockCore2.internalLogger) doReturn mockInternalLogger
        whenever(mockCore2.timeProvider) doReturn mockTimeProvider
        whenever(mockCore1.isCoreActive()) doReturn true
        Profiling.enable(fakeConfiguration, mockCore1)
        assertThat(Profiling.currentRegisteredCore?.get()).isEqualTo(mockCore1)

        // When
        whenever(mockCore1.isCoreActive()) doReturn false
        Profiling.enable(fakeConfiguration, mockCore2)

        // Then
        verify(mockCore2).registerFeature(any<ProfilingFeature>())
        assertThat(Profiling.currentRegisteredCore?.get()).isEqualTo(mockCore2)
    }

    private fun resetProfilerField() {
        Profiling.profiler = NoOpProfiler()
        Profiling.isProfilerInitialized.set(false)
    }

    companion object {
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
