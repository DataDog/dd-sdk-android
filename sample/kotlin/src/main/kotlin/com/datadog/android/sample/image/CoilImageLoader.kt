/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.image

import android.content.Context
import android.widget.ImageView
import coil.Coil
import coil.load
import com.datadog.android.sample.R
import okhttp3.OkHttpClient

internal class CoilImageLoader : ImageLoader {

    override val type: ImageLoaderType = ImageLoaderType.COIL

    override fun load(url: String, imageView: ImageView) {
        imageView.load(url) {
            placeholder(R.drawable.ph_default)
            error(R.drawable.ph_error)
        }
    }

    companion object {
        fun initialize(context: Context, okHttpClient: OkHttpClient) {
            val imageLoader = coil.ImageLoader.Builder(context).okHttpClient(okHttpClient).build()
            Coil.setImageLoader(imageLoader)
        }
    }
}
