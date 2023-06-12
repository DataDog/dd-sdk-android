/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.picture

import android.widget.ImageView
import androidx.lifecycle.ViewModel
import java.security.SecureRandom

internal class PictureViewModel : ViewModel() {

    val random = SecureRandom()
    var loader: ImageLoader = GlideImageLoader()

    fun loadPictureInto(picture: ImageView) {
        val url = if (random.nextBoolean()) {
            RANDOM_URL
        } else {
            FAILING_URL
        }
        loader.load(url, picture)
    }

    fun selectImageLoader(type: ImageLoaderType) {
        loader = buildImageLoader(type)
    }

    fun getImageLoader(): ImageLoaderType {
        return loader.type
    }

    private fun buildImageLoader(type: ImageLoaderType) = when (type) {
        ImageLoaderType.COIL -> CoilImageLoader()
        ImageLoaderType.FRESCO -> FrescoImageLoader()
        ImageLoaderType.GLIDE -> GlideImageLoader()
        ImageLoaderType.PICASSO -> PicassoImageLoader()
    }

    companion object {
        private const val RANDOM_URL = "https://picsum.photos/800/450"
        private const val FAILING_URL = "https://p1csum.photos/800/450"
    }
}
