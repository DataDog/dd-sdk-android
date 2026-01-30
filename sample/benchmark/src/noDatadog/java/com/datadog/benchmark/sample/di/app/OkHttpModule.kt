/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.app

import android.content.Context
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
internal interface OkHttpModule {

    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(context: Context): OkHttpClient {
            return OkHttpClient.Builder()
                .applyCommonConfiguration(context)
                .build()
        }
    }
}
