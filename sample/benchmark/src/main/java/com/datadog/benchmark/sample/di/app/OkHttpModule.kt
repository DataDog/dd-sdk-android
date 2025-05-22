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
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkServiceImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

@Module
internal interface OkHttpModule {

    @Binds
    @Singleton
    fun bindRickAndMortyNetworkService(impl: RickAndMortyNetworkServiceImpl): RickAndMortyNetworkService

    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(
            context: Context,
            config: BenchmarkConfig,
            sdkCore: dagger.Lazy<SdkCore>,
        ): OkHttpClient {

            return OkHttpClient.Builder().apply {
                cache(Cache(File(context.cacheDir, "okhttp-cache"), 10 * 1024 * 1024))

                if (config.scenario == SyntheticsScenario.RumAuto && config.run == SyntheticsRun.Instrumented) {
                    val interceptor = DatadogInterceptor.Builder(emptyMap()).apply {
                        setSdkInstanceName(sdkCore.get().name)
                    }.build()

                    addInterceptor(interceptor)
                }
            }.build()
        }

        @Provides
        @Singleton
        fun provideKtorHttpClient(
            okHttpClient: OkHttpClient
        ): HttpClient {
            return HttpClient(OkHttp) {
                engine {
                    preconfigured = okHttpClient
                }
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
        }
    }
}
