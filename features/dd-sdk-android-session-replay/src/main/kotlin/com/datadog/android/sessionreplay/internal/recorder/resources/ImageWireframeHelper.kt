/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import java.util.Locale

// This should not have a callback but it should just create a placeholder for resourcesSerializer
// The resourcesSerializer dependency should be removed from here
// TODO: RUM-0000 Remove the resourcesSerializer dependency from here
internal class ImageWireframeHelper(
    private val logger: InternalLogger,
    private val resourcesSerializer: ResourcesSerializer,
    private val imageCompression: ImageCompression = WebPImageCompression(),
    private val uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator,
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
        width: Int,
        height: Int,
        usePIIPlaceholder: Boolean,
        clipping: MobileSegment.WireframeClip? = null,
        drawable: Drawable? = null,
        shapeStyle: MobileSegment.ShapeStyle? = null,
        border: MobileSegment.ShapeBorder? = null,
        imageWireframeHelperCallback: ImageWireframeHelperCallback,
        prefix: String? = DRAWABLE_CHILD_NAME
    ): MobileSegment.Wireframe? {
        if (drawable == null) return null
        val id = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(view, prefix + currentWireframeIndex)
        val drawableProperties = resolveDrawableProperties(view, drawable)

        if (id == null || !drawableProperties.isValid()) return null

        val resources = view.resources

        if (resources == null) {
            logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { RESOURCES_NULL_ERROR.format(Locale.US, view.javaClass.canonicalName) }
            )

            return null
        }

        val displayMetrics = resources.displayMetrics
        val applicationContext = view.context.applicationContext

        if (applicationContext == null) {
            logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.TELEMETRY,
                { APPLICATION_CONTEXT_NULL_ERROR.format(Locale.US, view.javaClass.canonicalName) }
            )

            return null
        }

        val mimeType = imageCompression.getMimeType()
        val density = displayMetrics.density

        // in case we suspect the image is PII, return a placeholder
        if (usePIIPlaceholder && imageTypeResolver.isDrawablePII(drawable, density)) {
            return createContentPlaceholderWireframe(view, id, density)
        }

        val drawableWidthDp = width.densityNormalized(density).toLong()
        val drawableHeightDp = height.densityNormalized(density).toLong()

        val imageWireframe =
            MobileSegment.Wireframe.ImageWireframe(
                id = id,
                x,
                y,
                width = drawableWidthDp,
                height = drawableHeightDp,
                shapeStyle = shapeStyle,
                border = border,
                clip = clipping,
                mimeType = mimeType,
                isEmpty = true
            )

        imageWireframeHelperCallback.onStart()

        resourcesSerializer.handleBitmap(
            resources = resources,
            applicationContext = applicationContext,
            displayMetrics = displayMetrics,
            drawable = drawableProperties.drawable,
            drawableWidth = width,
            drawableHeight = height,
            imageWireframe = imageWireframe,
            resourcesSerializerCallback = object : ResourcesSerializerCallback {
                override fun onReady() {
                    imageWireframeHelperCallback.onFinished()
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
        imageWireframeHelperCallback: ImageWireframeHelperCallback
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
                    width = drawable.intrinsicWidth,
                    height = drawable.intrinsicHeight,
                    drawable = drawable,
                    shapeStyle = null,
                    border = null,
                    usePIIPlaceholder = true,
                    clipping = MobileSegment.WireframeClip(),
                    imageWireframeHelperCallback = imageWireframeHelperCallback
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

    private fun createContentPlaceholderWireframe(
        view: View,
        id: Long,
        density: Float
    ): MobileSegment.Wireframe.PlaceholderWireframe {
        val coordinates = IntArray(2)
        // this will always have size >= 2
        @Suppress("UnsafeThirdPartyFunctionCall")
        view.getLocationOnScreen(coordinates)
        val viewX = coordinates[0].densityNormalized(density).toLong()
        val viewY = coordinates[1].densityNormalized(density).toLong()

        return MobileSegment.Wireframe.PlaceholderWireframe(
            id,
            viewX,
            viewY,
            view.width.densityNormalized(density).toLong(),
            view.height.densityNormalized(density).toLong(),
            label = PLACEHOLDER_CONTENT_LABEL
        )
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
        internal const val DRAWABLE_CHILD_NAME = "drawable"

        @VisibleForTesting internal const val PLACEHOLDER_CONTENT_LABEL = "Content Image"

        @VisibleForTesting internal const val APPLICATION_CONTEXT_NULL_ERROR =
            "Application context is null for view %s"

        @VisibleForTesting internal const val RESOURCES_NULL_ERROR =
            "Resources is null for view %s"
    }
}
