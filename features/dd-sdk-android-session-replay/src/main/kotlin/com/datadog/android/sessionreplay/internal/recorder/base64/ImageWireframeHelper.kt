/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator

// This should not have a callback but it should just create a placeholder for base64Serializer
// The base64Serializer dependency should be removed from here
// TODO: RUM-0000 Remove the base64Serializer dependency from here
internal class ImageWireframeHelper(
    private val imageCompression: ImageCompression = WebPImageCompression(),
    private val uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator,
    private val base64Serializer: Base64Serializer,
    private val viewUtilsInternal: ViewUtilsInternal = ViewUtilsInternal(),
    private val imageTypeResolver: ImageTypeResolver = ImageTypeResolver()
) {

    // Why is this function accepting an optional drawable ???
    // TODO: RUM-0000 Make the drawable non optional for this function
    @Suppress("ReturnCount")
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
        prefix: String = DRAWABLE_CHILD_NAME,
        callback: ImageWireframeHelperCallback? = null
    ): MobileSegment.Wireframe? {
        if (drawable == null) return null
        val id = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(view, prefix + currentWireframeIndex)
        val drawableProperties = resolveDrawableProperties(view, drawable)

        if (id == null || !drawableProperties.isValid()) return null

        val displayMetrics = view.resources.displayMetrics
        val applicationContext = view.context.applicationContext
        val mimeType = imageCompression.getMimeType()
        val density = displayMetrics.density

        // in case we suspect the image is PII, return a placeholder
        @Suppress("UnsafeCallOnNullableType") // drawable already checked for null in isValid
        if (imageTypeResolver.isDrawablePII(
                drawableProperties.drawable,
                drawableProperties.drawableWidth.densityNormalized(density),
                drawableProperties.drawableHeight.densityNormalized(density)
            )
        ) {
            return MobileSegment.Wireframe.PlaceholderWireframe(
                id,
                x,
                y,
                width,
                height,
                label = PLACEHOLDER_CONTENT_LABEL
            )
        }

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

        callback?.onStart()

        base64Serializer.handleBitmap(
            applicationContext = applicationContext,
            displayMetrics = displayMetrics,
            drawable = drawableProperties.drawable,
            drawableWidth = drawableProperties.drawableWidth,
            drawableHeight = drawableProperties.drawableHeight,
            imageWireframe = imageWireframe,
            object : Base64SerializerCallback {
                override fun onReady() {
                    callback?.onFinished()
                }
            }
        )

        return imageWireframe
    }

    @Suppress("NestedBlockDepth")
    internal fun createCompoundDrawableWireframes(
        view: TextView,
        mappingContext: MappingContext,
        prevWireframeIndex: Int,
        callback: ImageWireframeHelperCallback?
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
                    border = null,
                    callback = callback
                )?.let { resultWireframe ->
                    result.add(resultWireframe)
                }
            }
        }

        return result
    }

    private fun resolveDrawableProperties(view: View, drawable: Drawable): DrawableProperties {
        return when (drawable) {
            is LayerDrawable -> {
                if (drawable.numberOfLayers > 0) {
                    @Suppress("UnsafeThirdPartyFunctionCall") // Can't be out of bounds
                    resolveDrawableProperties(view, drawable.getDrawable(0))
                } else {
                    DrawableProperties(drawable, drawable.intrinsicWidth, drawable.intrinsicHeight)
                }
            }
            is InsetDrawable -> {
                val internalDrawable = drawable.drawable
                if (internalDrawable != null) {
                    resolveDrawableProperties(view, internalDrawable)
                } else {
                    DrawableProperties(drawable, drawable.intrinsicWidth, drawable.intrinsicHeight)
                }
            }
            is GradientDrawable -> DrawableProperties(drawable, view.width, view.height)
            else -> DrawableProperties(drawable, drawable.intrinsicWidth, drawable.intrinsicHeight)
        }
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

    private data class DrawableProperties(
        val drawable: Drawable,
        val drawableWidth: Int,
        val drawableHeight: Int
    ) {
        fun isValid(): Boolean {
            return drawableWidth > 0 && drawableHeight > 0
        }
    }

    internal companion object {
        @VisibleForTesting internal const val DRAWABLE_CHILD_NAME = "drawable"

        @VisibleForTesting internal const val PLACEHOLDER_CONTENT_LABEL = "Content Image"
    }
}
