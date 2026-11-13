/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.app

import android.content.Context
import com.datadog.android.api.SdkCore
import com.datadog.android.okhttp.DatadogInterceptor
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.config.SyntheticsRun
import com.datadog.benchmark.sample.config.SyntheticsScenario
import com.datadog.benchmark.sample.network.rickandmorty.fallback.RickAndMortyFallbackInterceptor
import com.datadog.benchmark.sample.network.rickandmorty.fallback.RickAndMortyFallbackListener
import com.datadog.benchmark.sample.network.rickandmorty.fallback.RickAndMortyLocalServer
import dagger.Lazy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class NetworkModuleTest {

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockLocalServer: RickAndMortyLocalServer

    @Mock
    lateinit var mockFallbackListener: RickAndMortyFallbackListener

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun `set up`() {
        whenever(mockContext.cacheDir).thenReturn(tempDir)
        whenever(mockSdkCore.name).thenReturn("test-sdk")
    }

    @ParameterizedTest
    @EnumSource(SyntheticsRun::class)
    fun `M add Datadog interceptor W provideOkHttpClient() {timeseries, any run}`(run: SyntheticsRun) {
        // Given
        val config = BenchmarkConfig(run = run, scenario = SyntheticsScenario.Timeseries)

        // When
        val client = provideOkHttpClient(config)

        // Then
        assertThat(client.interceptors).anyMatch { it is DatadogInterceptor }
    }

    @ParameterizedTest
    @EnumSource(SyntheticsRun::class)
    fun `M add fallback interceptor before Datadog interceptor W provideOkHttpClient() {timeseries}`(
        run: SyntheticsRun
    ) {
        // Given
        val config = BenchmarkConfig(run = run, scenario = SyntheticsScenario.Timeseries)

        // When
        val client = provideOkHttpClient(config)

        // Then
        val fallbackIndex = client.interceptors.indexOfFirst { it is RickAndMortyFallbackInterceptor }
        val datadogIndex = client.interceptors.indexOfFirst { it is DatadogInterceptor }
        assertThat(fallbackIndex).isNotNegative().isLessThan(datadogIndex)
    }

    @ParameterizedTest
    @EnumSource(SyntheticsScenario::class, names = ["Timeseries"], mode = EnumSource.Mode.EXCLUDE)
    fun `M not add fallback interceptor W provideOkHttpClient() {not timeseries}`(scenario: SyntheticsScenario) {
        // Given
        val config = BenchmarkConfig(run = SyntheticsRun.Instrumented, scenario = scenario)

        // When
        val client = provideOkHttpClient(config)

        // Then
        assertThat(client.interceptors).noneMatch { it is RickAndMortyFallbackInterceptor }
    }

    @Test
    fun `M not add Datadog interceptor W provideOkHttpClient() {rumAuto, baseline}`() {
        // Given
        val config = BenchmarkConfig(run = SyntheticsRun.Baseline, scenario = SyntheticsScenario.RumAuto)

        // When
        val client = provideOkHttpClient(config)

        // Then
        assertThat(client.interceptors).noneMatch { it is DatadogInterceptor }
    }

    private fun provideOkHttpClient(config: BenchmarkConfig) = NetworkModule.provideOkHttpClient(
        context = mockContext,
        config = config,
        sdkCore = Lazy { mockSdkCore },
        rickAndMortyLocalServer = Lazy { mockLocalServer },
        rickAndMortyFallbackListener = Lazy { mockFallbackListener }
    )
}
