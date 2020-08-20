/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.picture

import android.content.Context
import android.widget.ImageView
import androidx.lifecycle.ViewModel
import coil.Coil
import coil.ImageLoader
import coil.load
import com.datadog.android.coil.DatadogCoilRequestListener
import okhttp3.OkHttpClient

class PictureViewModel : ViewModel() {

    fun loadPictureInto(picture: ImageView) {
        picture.load(RANDOM_URL) {
            listener(DatadogCoilRequestListener())
        }
    }

    companion object {
        private const val RANDOM_URL = "https://source.unsplash.com/random1/800x450/d"

        fun setup(context: Context, okHttpClient: OkHttpClient) {
            val imageLoader = ImageLoader.Builder(context).okHttpClient(okHttpClient).build()
            Coil.setImageLoader(imageLoader)
        }
    }
}
