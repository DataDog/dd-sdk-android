/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.activity

import android.content.Context
import com.datadog.android.okhttp.DatadogInterceptor
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.config.SyntheticsScenario
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkServiceImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

@Module
internal interface OkHttpModule {

    @Binds
    @BenchmarkActivityScope
    fun bindRickAndMortyNetworkService(impl: RickAndMortyNetworkServiceImpl): RickAndMortyNetworkService

    companion object {
        @Provides
        @BenchmarkActivityScope
        fun provideOkHttpClient(
            context: Context,
            config: BenchmarkConfig,
        ): OkHttpClient {
            return OkHttpClient.Builder().apply {
                cache(Cache(File(context.cacheDir, "okhttp-cache"), 10 * 1024 * 1024))
                // TODO WAHAHA
                if (config.scenario == SyntheticsScenario.RumAuto) {
                    addInterceptor(DatadogInterceptor.Builder(emptyMap()).build())
                }
            }.build()
        }
    }
}
