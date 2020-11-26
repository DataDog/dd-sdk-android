/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.picture

import android.content.Context
import android.util.Log
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.datadog.android.glide.DatadogGlideModule

@GlideModule
class SampleGlideModule : DatadogGlideModule(listOf("shopist.io")) {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        super.applyOptions(context, builder)

        val size10mb = 10485760L
        val factory = InternalCacheDiskCacheFactory(context, "glide", size10mb)
        builder.setMemoryCache(LruResourceCache(1))
        builder.setLogLevel(Log.VERBOSE)
    }
}
