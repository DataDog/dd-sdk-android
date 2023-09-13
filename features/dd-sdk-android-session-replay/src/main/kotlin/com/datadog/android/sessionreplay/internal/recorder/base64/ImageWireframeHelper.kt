/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator

internal class ImageWireframeHelper(
    private val imageCompression: ImageCompression = WebPImageCompression(),
    private val uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator,
    private val base64Serializer: Base64Serializer,
    private val viewUtilsInternal: ViewUtilsInternal = ViewUtilsInternal()
) {
    @MainThread
    internal fun createImageWireframe(
        view: View,
        currentWireframeIndex: Int,
        x: Long,
        y: Long,
        width: Long,
        height: Long,
        drawable: Drawable? = null,
        shapeStyle: MobileSegment.ShapeStyle? = null,
        border: MobileSegment.ShapeBorder? = null,
        prefix: String = DRAWABLE_CHILD_NAME
    ): MobileSegment.Wireframe.ImageWireframe? {
        val id = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(view, prefix + currentWireframeIndex)

        @Suppress("ComplexCondition")
        if (
            drawable == null ||
            id == null ||
            drawable.intrinsicWidth <= 0 ||
            drawable.intrinsicHeight <= 0
        ) {
            return null
        }

        val displayMetrics = view.resources.displayMetrics
        val applicationContext = view.context.applicationContext
        val mimeType = imageCompression.getMimeType()

        val imageWireframe =
            MobileSegment.Wireframe.ImageWireframe(
                id = id,
                x = x,
                y = y,
                width = width,
                height = height,
                shapeStyle = shapeStyle,
                border = border,
                base64 = "",
                mimeType = mimeType,
                isEmpty = true
            )

        base64Serializer.handleBitmap(
            applicationContext = applicationContext,
            displayMetrics = displayMetrics,
            drawable = drawable,
            imageWireframe = imageWireframe
        )

        return imageWireframe
    }

    @Suppress("NestedBlockDepth")
    internal fun createCompoundDrawableWireframes(
        view: TextView,
        mappingContext: MappingContext,
        prevWireframeIndex: Int
    ): MutableList<MobileSegment.Wireframe> {
        val result = mutableListOf<MobileSegment.Wireframe>()
        var wireframeIndex = prevWireframeIndex
        val density = mappingContext.systemInformation.screenDensity

        // CompoundDrawables returns an array of indexes in the following order:
        // left, top, right, bottom
        view.compoundDrawables.forEachIndexed { compoundDrawableIndex, _ ->
            if (compoundDrawableIndex > CompoundDrawablePositions.values().size) {
                return@forEachIndexed
            }

            val compoundDrawablePosition = convertIndexToCompoundDrawablePosition(
                compoundDrawableIndex
            ) ?: return@forEachIndexed

            val drawable = view.compoundDrawables[compoundDrawableIndex]

            if (drawable != null) {
                val drawableCoordinates = viewUtilsInternal.resolveCompoundDrawableBounds(
                    view = view,
                    drawable = drawable,
                    pixelsDensity = density,
                    position = compoundDrawablePosition
                )

                @Suppress("ThreadSafety") // TODO REPLAY-1861 caller thread of .map is unknown?
                createImageWireframe(
                    view = view,
                    currentWireframeIndex = ++wireframeIndex,
                    x = drawableCoordinates.x,
                    y = drawableCoordinates.y,
                    width = drawable.intrinsicWidth
                        .densityNormalized(density).toLong(),
                    height = drawable.intrinsicHeight
                        .densityNormalized(density).toLong(),
                    drawable = drawable,
                    shapeStyle = null,
                    border = null
                )?.let { resultWireframe ->
                    result.add(resultWireframe)
                }
            }
        }

        return result
    }

    @Suppress("MagicNumber")
    private fun convertIndexToCompoundDrawablePosition(compoundDrawableIndex: Int): CompoundDrawablePositions? {
        return when (compoundDrawableIndex) {
            0 -> CompoundDrawablePositions.LEFT
            1 -> CompoundDrawablePositions.TOP
            2 -> CompoundDrawablePositions.RIGHT
            3 -> CompoundDrawablePositions.BOTTOM
            else -> null
        }
    }

    internal enum class CompoundDrawablePositions {
        LEFT,
        TOP,
        RIGHT,
        BOTTOM
    }

    internal companion object {
        @VisibleForTesting internal const val DRAWABLE_CHILD_NAME = "drawable"
    }
}
