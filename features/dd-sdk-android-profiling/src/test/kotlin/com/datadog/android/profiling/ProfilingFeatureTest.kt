/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import android.content.Context
import android.content.SharedPreferences
import android.os.ProfilingManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.Profiler
import com.datadog.android.profiling.internal.ProfilingFeature
import com.datadog.android.profiling.internal.ProfilingRequestFactory
import com.datadog.android.profiling.internal.ProfilingStorage
import com.datadog.android.rum.TTIDEvent
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class ProfilingFeatureTest {

    private lateinit var testedFeature: ProfilingFeature

    @Mock
    private lateinit var mockSdkCore: InternalSdkCore

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Mock
    private lateinit var mockProfilingExecutor: ExecutorService

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockService: ProfilingManager

    @Mock
    private lateinit var mockProfiler: Profiler

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    @Forgery
    private lateinit var fakeConfiguration: ProfilingConfiguration

    @StringForgery
    private lateinit var fakeInstanceName: String

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.name) doReturn fakeInstanceName
        whenever(mockSdkCore.createSingleThreadExecutorService(any())) doReturn mockProfilingExecutor
        whenever(mockContext.getSystemService(ProfilingManager::class.java)) doReturn (mockService)
        whenever(mockContext.getSharedPreferences(any(), any())) doReturn mockSharedPreferences
        whenever(mockSharedPreferences.edit()) doReturn mockEditor
        whenever(mockEditor.putBoolean(any(), any())) doReturn mockEditor
        whenever(mockEditor.putInt(any(), any())) doReturn mockEditor
        whenever(mockEditor.putString(any(), any())) doReturn mockEditor
        whenever(mockEditor.putStringSet(any(), any())) doReturn mockEditor
        testedFeature = ProfilingFeature(mockSdkCore, fakeConfiguration, mockProfiler)
        testedFeature.onInitialize(mockContext)
    }

    @AfterEach
    fun `tear down`() {
        ProfilingStorage.sharedPreferencesStorage = null
    }

    @Test
    fun `M allow 18h storage W init()`() {
        // When
        val config = testedFeature.storageConfiguration

        // Then
        assertThat(config.oldBatchThreshold).isEqualTo(18L * 60L * 60L * 1000L)
    }

    @Test
    fun `M initialize ProfilingRequestFactory W initialize()`() {
        // When
        testedFeature.onInitialize(mockContext)

        // Then
        assertThat(testedFeature.requestFactory).isInstanceOf(ProfilingRequestFactory::class.java)
    }

    @Test
    fun `M stop Profiling W receive TTID event`(
        @Forgery fakeTTIDEvent: TTIDEvent
    ) {
        // When
        testedFeature.onReceive(fakeTTIDEvent)

        // Then
        verify(mockProfiler).stop(fakeInstanceName)
    }

    @Test
    fun `M not stop Profiling W receive illegal event`(@StringForgery fakeIllegalValue: String) {
        // When
        testedFeature.onReceive(fakeIllegalValue)

        // Then
        val argumentCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.MAINTAINER),
            argumentCaptor.capture(),
            isNull(),
            eq(false),
            isNull()
        )
        assertThat(argumentCaptor.firstValue.invoke())
            .isEqualTo("Profiling feature received an event of unsupported type=${String::class.java.canonicalName}.")
        verify(mockProfiler, never()).stop(fakeInstanceName)
    }
}
