/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.image

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.datadog.android.glide.DatadogGlideModule
import com.datadog.android.sample.SampleApplication
import java.io.InputStream

@GlideModule
internal class SampleGlideModule : DatadogGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        super.applyOptions(context, builder)

        builder.setLogLevel(Log.VERBOSE)
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val client = SampleApplication.getOkHttpClient(context)
        val factory = OkHttpUrlLoader.Factory(client)
        registry.replace(GlideUrl::class.java, InputStream::class.java, factory)
    }
}
