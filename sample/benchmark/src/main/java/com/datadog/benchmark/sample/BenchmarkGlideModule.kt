/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.datadog.android.glide.DatadogGlideModule
import com.datadog.benchmark.sample.di.app.DATADOG_SDK_INSTANCE_NAME
import okhttp3.OkHttpClient
import javax.inject.Inject

@GlideModule
internal class BenchmarkGlideModule : DatadogGlideModule(
    sdkInstanceName = DATADOG_SDK_INSTANCE_NAME
) {
    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        context.benchmarkAppComponent.inject(this)

        super.registerComponents(context, glide, registry)
    }

    override fun getClientBuilder(): OkHttpClient.Builder {
        return okHttpClient.newBuilder()
    }
}
