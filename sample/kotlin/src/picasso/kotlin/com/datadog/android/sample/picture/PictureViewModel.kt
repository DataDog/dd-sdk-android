/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.picture

import android.content.Context
import android.widget.ImageView
import androidx.lifecycle.ViewModel
import com.datadog.android.sample.BuildConfig
import com.datadog.android.sample.R
import com.squareup.picasso.LruCache
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import java.security.SecureRandom
import okhttp3.OkHttpClient

class PictureViewModel : ViewModel() {

    val random = SecureRandom()

    fun loadPictureInto(picture: ImageView) {
        val url = if (random.nextBoolean()) {
            RANDOM_URL
        } else {
            FAILING_URL
        }
        Picasso.get()
            .load(url)
            .placeholder(R.drawable.ph_default)
            .error(R.drawable.ph_error)
            .into(picture)
    }

    companion object {
        const val RANDOM_URL = "https://source.unsplash.com/random/800x450"
        const val FAILING_URL = "https://s0urce.unsplash.com/random/800x450"

        fun setup(context: Context, okHttpClient: OkHttpClient) {

            val picasso = Picasso.Builder(context)
                .downloader(OkHttp3Downloader(okHttpClient))
                .indicatorsEnabled(BuildConfig.DEBUG)
                .memoryCache(LruCache(1))
                .loggingEnabled(true)
                .build()
            Picasso.setSingletonInstance(picasso)
        }
    }
}
