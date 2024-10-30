/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

//noinspection SuspiciousImport
import android.R
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.InternalLogger

/**
 * Drawable utility object needed in the Session Replay Wireframe Mappers.
 * This class is meant for internal usage so please use it carefully as it might change in time.
 */
@RequiresApi(Build.VERSION_CODES.M)
open class AndroidMDrawableToColorMapper(
    extensionMappers: List<DrawableToColorMapper> = emptyList()
) : LegacyDrawableToColorMapper(extensionMappers) {

    override fun resolveRippleDrawable(drawable: RippleDrawable, internalLogger: InternalLogger): Int? {
        // A ripple drawable can have a layer marked as mask, and which is not drawn
        // We can reuse the LayerDrawable by passing a way to filter the mask layer
        val maskLayerIndex = drawable.findIndexByLayerId(R.id.mask)
        return resolveLayerDrawable(drawable, internalLogger) { idx, _ -> idx != maskLayerIndex }
    }

    override fun resolveInsetDrawable(drawable: InsetDrawable, internalLogger: InternalLogger): Int? {
        return drawable.drawable?.let { mapDrawableToColor(it, internalLogger) }
    }
}
