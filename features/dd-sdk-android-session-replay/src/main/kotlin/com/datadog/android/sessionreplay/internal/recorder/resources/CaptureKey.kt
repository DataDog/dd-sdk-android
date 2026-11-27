/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import com.datadog.android.sessionreplay.recorder.resources.DefaultDrawableCopier
import com.datadog.android.sessionreplay.recorder.resources.DrawableCopier
import java.lang.ref.WeakReference
import java.util.Objects

/**
 * What decides the image a capture makes, so that two captures with equal keys make the same image.
 */
internal sealed class CaptureKey {

    /** Whether the work holds an application drawable strongly until it is processed. */
    abstract val holdsDrawableStrongly: Boolean

    /** A bitmap, copied when its capture is admitted: the same pixels in the same format. */
    data class OfBitmap(val signature: String) : CaptureKey() {
        override val holdsDrawableStrongly: Boolean = false
    }

    /**
     * A drawable, which the worker reads when it starts: the very same drawable, copied by the same copier, at the same
     * size. The objects themselves are compared, since anything about a drawable can change, and a key only says
     * whether two captures started with the same objects.
     */
    class OfDrawable(
        drawable: Drawable,
        private val copier: DrawableCopier,
        private val resources: Resources,
        private val displayMetrics: DisplayMetrics,
        private val width: Int,
        private val height: Int,
        private val customResourceIdCacheKey: String?,
        override val holdsDrawableStrongly: Boolean
    ) : CaptureKey() {

        // The queued work holds the drawable, strongly or weakly, so the key does not extend its life.
        private val drawableReference = WeakReference(drawable)
        private val hash = Objects.hash(
            System.identityHashCode(drawable),
            width,
            height,
            customResourceIdCacheKey,
            holdsDrawableStrongly
        )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is OfDrawable) return false
            val drawable = drawableReference.get() ?: return false
            return drawable === other.drawableReference.get() && hasSameCopier(other) && hasSameSettings(other)
        }

        override fun hashCode(): Int = hash

        // The default copier has no state, so any two of them copy the same way.
        private fun hasSameCopier(other: OfDrawable): Boolean =
            copier === other.copier || (copier is DefaultDrawableCopier && other.copier is DefaultDrawableCopier)

        private fun hasSameSettings(other: OfDrawable): Boolean =
            resources === other.resources &&
                displayMetrics === other.displayMetrics &&
                width == other.width &&
                height == other.height &&
                customResourceIdCacheKey == other.customResourceIdCacheKey &&
                holdsDrawableStrongly == other.holdsDrawableStrongly
    }
}
