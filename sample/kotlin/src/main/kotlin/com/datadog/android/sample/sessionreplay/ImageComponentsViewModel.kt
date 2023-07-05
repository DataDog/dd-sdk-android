/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.sessionreplay

import android.graphics.drawable.Drawable
import android.view.View
import androidx.lifecycle.ViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.datadog.android.sample.R

internal class ImageComponentsViewModel : ViewModel() {
    internal fun fetchRemoteImage(url: String, view: View, callback: ImageLoadedCallback) {
        Glide.with(view)
            .load(url)
            .placeholder(R.drawable.ic_dd_icon_rgb)
            .fitCenter()
            .into(object : CustomTarget<Drawable>() {
                override fun onLoadCleared(placeholder: Drawable?) {}
                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    callback.onImageLoaded(resource)
                }
            })
    }
}
