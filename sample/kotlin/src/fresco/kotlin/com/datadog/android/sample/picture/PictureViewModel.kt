/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.picture

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import androidx.lifecycle.ViewModel
import com.datadog.android.fresco.DatadogFrescoCacheListener
import com.facebook.cache.disk.DiskCacheConfig
import com.facebook.common.util.ByteConstants
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.backends.okhttp3.OkHttpImagePipelineConfigFactory
import com.facebook.imagepipeline.cache.MemoryCacheParams
import okhttp3.OkHttpClient

class PictureViewModel : ViewModel() {

    fun loadPictureInto(picture: ImageView) {
        if (picture is SimpleDraweeView) {
            picture.setImageURI(Uri.parse(RANDOM_URL))
        }
    }

    companion object {
        private const val RANDOM_URL = "https://source.unsplash.com/random/800x450"
        private val MAX_HEAP_SIZE = Runtime.getRuntime().maxMemory().toInt()
        private val MAX_MEMORY_CACHE_SIZE = MAX_HEAP_SIZE / 4
        private const val MAX_DISK_CACHE_SIZE = 40L * ByteConstants.MB

        fun setup(context: Context, okHttpClient: OkHttpClient) {
            val config = OkHttpImagePipelineConfigFactory
                .newBuilder(context, okHttpClient)
                .setBitmapMemoryCacheParamsSupplier {
                    MemoryCacheParams(
                        MAX_MEMORY_CACHE_SIZE,
                        Int.MAX_VALUE,
                        MAX_MEMORY_CACHE_SIZE,
                        Int.MAX_VALUE,
                        Int.MAX_VALUE
                    )
                }
                .setMainDiskCacheConfig(
                    DiskCacheConfig.newBuilder(context)
                        .setCacheEventListener(DatadogFrescoCacheListener())
                        .setMaxCacheSize(MAX_DISK_CACHE_SIZE)
                        .build()
                )
                .build()
            Fresco.initialize(context, config)
        }
    }
}
