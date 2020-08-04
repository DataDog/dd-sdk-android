/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.picture

import android.widget.ImageView
import androidx.lifecycle.ViewModel
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.datadog.android.sample.R

class PictureViewModel : ViewModel() {

    private val factory = DrawableCrossFadeFactory.Builder().setCrossFadeEnabled(true).build()

    fun loadPictureInto(picture: ImageView) {
        GlideApp.with(picture)
            .load(RANDOM_URL)
            .placeholder(R.drawable.ph_default)
            .transition(withCrossFade(factory))
            .transform(FailingTransformation())
            .error(R.drawable.ph_error)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(picture)
    }

    companion object {
        const val RANDOM_URL = "https://source.unsplash.com/random/800x450"
    }
}
