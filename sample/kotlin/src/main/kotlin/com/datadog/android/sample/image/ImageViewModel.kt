/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.image

import android.widget.ImageView
import androidx.lifecycle.ViewModel

internal class ImageViewModel : ViewModel() {

    private var loader: ImageLoader = GlideImageLoader()
    private var tapIndex = 0
    private var currentImageUrl = ""

    fun loadImageInto(imageView: ImageView) {
        val url = when (tapIndex) {
            0 -> {
                currentImageUrl = "$IMAGE_URL?t=${System.currentTimeMillis()}"
                currentImageUrl
            }
            1 -> currentImageUrl
            else -> FAILING_IMAGE_URL
        }
        loader.load(url, imageView)
        tapIndex = (tapIndex + 1) % CYCLE_SIZE
    }

    fun selectImageLoader(type: ImageLoaderType) {
        loader = buildImageLoader(type)
    }

    fun getImageLoader(): ImageLoaderType {
        return loader.type
    }

    private fun buildImageLoader(type: ImageLoaderType) = when (type) {
        ImageLoaderType.COIL -> CoilImageLoader()
        ImageLoaderType.COIL3 -> Coil3ImageLoader()
        ImageLoaderType.FRESCO -> FrescoImageLoader()
        ImageLoaderType.GLIDE -> GlideImageLoader()
        ImageLoaderType.PICASSO -> PicassoImageLoader()
    }

    companion object {
        private const val CYCLE_SIZE = 3
        private const val IMAGE_URL = "https://picsum.photos/800/450"
        private const val FAILING_IMAGE_URL = "https://picsum.photos/id/99999999/800/450"
    }
}
