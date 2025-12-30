/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.picture

import android.content.Context
import android.widget.ImageView
import coil3.SingletonImageLoader
import coil3.load
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.datadog.android.Datadog
import com.datadog.android.coil3.DatadogCoilRequestListener
import okhttp3.OkHttpClient

internal class Coil3ImageLoader : ImageLoader {

    private val listener = DatadogCoilRequestListener(Datadog.getInstance())

    override val type: ImageLoaderType = ImageLoaderType.COIL3

    override fun load(url: String, imageView: ImageView) {
        imageView.load(url) { listener(listener) }
    }

    companion object {
        fun initialize(context: Context, okHttpClient: OkHttpClient) {
            val imageLoader = coil3.ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory(okHttpClient))
                }
                .build()
            SingletonImageLoader.setSafe { imageLoader }
        }
    }
}
