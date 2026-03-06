/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import android.app.ActivityManager
import android.app.ApplicationStartInfo
import android.content.Context
import com.datadog.android.internal.data.SharedPreferencesStorage
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.NoOpProfiler
import com.datadog.android.profiling.internal.Profiler
import com.datadog.android.profiling.internal.ProfilingStartReason
import com.datadog.android.profiling.internal.ProfilingStorage
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@OptIn(ExperimentalProfilingApi::class)
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class DdProfilingContentProviderTest {

    private lateinit var testedProvider: DdProfilingContentProvider

    @Mock
    private lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockActivityManager: ActivityManager

    @Mock
    private lateinit var mockApplicationStartInfo: ApplicationStartInfo

    @Mock
    private lateinit var mockProfiler: Profiler

    @Mock
    private lateinit var mockSharedPreferencesStorage: SharedPreferencesStorage

    @StringForgery
    private lateinit var fakeInstanceName: String

    @BeforeEach
    fun `set up`() {
        whenever(mockBuildSdkVersionProvider.isAtLeastVanillaIceCream).doReturn(true)
        whenever(mockContext.getSystemService(Context.ACTIVITY_SERVICE)).doReturn(
            mockActivityManager
        )
        whenever(mockActivityManager.getHistoricalProcessStartReasons(1))
            .doReturn(listOf(mockApplicationStartInfo))

        whenever(mockSharedPreferencesStorage.getFloat(any(), any())).doReturn(100f)

        whenever(mockSharedPreferencesStorage.getStringSet(any(), any()))
            .doReturn(setOf(fakeInstanceName))

        ProfilingStorage.sharedPreferencesStorage = mockSharedPreferencesStorage
        Profiling.profiler = mockProfiler
        Profiling.isProfilerInitialized.set(true)

        testedProvider = DdProfilingContentProvider(mockBuildSdkVersionProvider)
    }

    @AfterEach
    fun `tear down`() {
        Profiling.profiler = NoOpProfiler()
        Profiling.isProfilerInitialized.set(false)
        ProfilingStorage.sharedPreferencesStorage = null
    }

    @Test
    fun `M start profiling with launcher app_start_info W onCreate { start reason is LAUNCHER }`() {
        // Given
        whenever(mockApplicationStartInfo.reason)
            .doReturn(ApplicationStartInfo.START_REASON_LAUNCHER)

        // When
        testedProvider.onStart(mockContext)

        // Then
        verify(mockProfiler).start(
            eq(mockContext),
            eq(ProfilingStartReason.APPLICATION_LAUNCH),
            eq(mapOf("app_start_info" to "launcher")),
            eq(setOf(fakeInstanceName))
        )
    }

    @Test
    fun `M start profiling with start_activity app_start_info W onCreate { start reason is START_ACTIVITY }`() {
        // Given
        whenever(mockApplicationStartInfo.reason)
            .doReturn(ApplicationStartInfo.START_REASON_START_ACTIVITY)

        // When
        testedProvider.onStart(mockContext)

        // Then
        verify(mockProfiler).start(
            eq(mockContext),
            eq(ProfilingStartReason.APPLICATION_LAUNCH),
            eq(mapOf("app_start_info" to "start_activity")),
            eq(setOf(fakeInstanceName))
        )
    }

    @Test
    fun `M start profiling with recents app_start_info W onCreate { start reason is LAUNCHER_RECENTS }`() {
        // Given
        whenever(mockApplicationStartInfo.reason)
            .doReturn(ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS)

        // When
        testedProvider.onStart(mockContext)

        // Then
        verify(mockProfiler).start(
            eq(mockContext),
            eq(ProfilingStartReason.APPLICATION_LAUNCH),
            eq(mapOf("app_start_info" to "recents")),
            eq(setOf(fakeInstanceName))
        )
    }

    @Test
    fun `M not start profiling W onCreate { start reason is ineligible }`() {
        // Given
        whenever(mockApplicationStartInfo.reason)
            .doReturn(ApplicationStartInfo.START_REASON_ALARM)

        // When
        testedProvider.onStart(mockContext)

        // Then
        verify(mockProfiler, never()).start(
            any(),
            any(),
            any(),
            any()
        )
    }

    // endregion

    // region sampleProfiling early return / removeSampleRate

    @Test
    fun `M not remove sample rate W onCreate { start reason is ineligible }`() {
        // Given
        whenever(mockApplicationStartInfo.reason)
            .doReturn(ApplicationStartInfo.START_REASON_ALARM)

        // When
        testedProvider.onStart(mockContext)

        // Then
        verify(
            mockSharedPreferencesStorage,
            never()
        ).remove(ProfilingStorage.KEY_PROFILING_SAMPLE_RATE)
    }

    @Test
    fun `M not start profiling W onCreate { sampler does not sample }`() {
        // Given
        whenever(mockApplicationStartInfo.reason)
            .doReturn(ApplicationStartInfo.START_REASON_LAUNCHER)
        whenever(
            mockSharedPreferencesStorage.getFloat(
                eq(ProfilingStorage.KEY_PROFILING_SAMPLE_RATE),
                any()
            )
        ).doReturn(0f)

        // When
        testedProvider.onStart(mockContext)

        // Then
        verify(mockProfiler, never()).start(any(), any(), any(), any())
    }

    @Test
    fun `M remove sample rate W onCreate { sampler does not sample }`() {
        // Given
        whenever(mockApplicationStartInfo.reason)
            .doReturn(ApplicationStartInfo.START_REASON_LAUNCHER)
        whenever(
            mockSharedPreferencesStorage.getFloat(
                eq(ProfilingStorage.KEY_PROFILING_SAMPLE_RATE),
                any()
            )
        ).doReturn(0f)

        // When
        testedProvider.onStart(mockContext)

        // Then
        verify(mockSharedPreferencesStorage).remove(ProfilingStorage.KEY_PROFILING_SAMPLE_RATE)
    }

    // endregion
}
