/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.LayerDrawable
import com.datadog.android.sessionreplay.internal.recorder.safeGetDrawable

internal class ResourceDrawableKeyGenerator : DrawableKeyGenerator {

    override fun generateKeyFromDrawable(drawable: Drawable): String =
        generatePrefix(drawable) + System.identityHashCode(drawable)

    private fun generatePrefix(drawable: Drawable): String {
        return when (drawable) {
            is DrawableContainer -> getPrefixForDrawableContainer(drawable)
            is LayerDrawable -> getPrefixForLayerDrawable(drawable)
            else -> ""
        }
    }

    private fun getPrefixForDrawableContainer(drawable: DrawableContainer): String {
        if (drawable !is AnimationDrawable) {
            return drawable.state.joinToString(separator = "", postfix = "-")
        }

        return ""
    }

    private fun getPrefixForLayerDrawable(drawable: LayerDrawable): String {
        val sb = StringBuilder()
        for (index in 0 until drawable.numberOfLayers) {
            val layer = drawable.safeGetDrawable(index)
            val layerHash = System.identityHashCode(layer).toString()
            sb.append(layerHash)
            sb.append("-")
        }
        return sb.toString()
    }
}
