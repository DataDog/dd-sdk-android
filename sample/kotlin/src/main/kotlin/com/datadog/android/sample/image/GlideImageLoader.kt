/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.image

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.datadog.android.sample.R

internal class GlideImageLoader : ImageLoader {

    private val factory = DrawableCrossFadeFactory.Builder().setCrossFadeEnabled(true).build()

    override val type: ImageLoaderType = ImageLoaderType.GLIDE

    override fun load(url: String, imageView: ImageView) {
        Glide.with(imageView)
            .load(url)
            .placeholder(R.drawable.ph_default)
            .transition(DrawableTransitionOptions.withCrossFade(factory))
            .transform(FailingTransformation())
            .error(R.drawable.ph_error)
            .into(imageView)
    }
}
