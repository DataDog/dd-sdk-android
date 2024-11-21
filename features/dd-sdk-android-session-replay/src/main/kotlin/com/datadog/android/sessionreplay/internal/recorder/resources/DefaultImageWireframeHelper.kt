/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import java.util.Locale

// This should not have a callback but it should just create a placeholder for resourceResolver
// The resourceResolver dependency should be removed from here
// TODO RUM-3795 Remove the resourceResolver dependency from here
internal class DefaultImageWireframeHelper(
    private val logger: InternalLogger,
    private val resourceResolver: ResourceResolver,
    private val viewIdentifierResolver: ViewIdentifierResolver,
    private val viewUtilsInternal: ViewUtilsInternal,
    private val imageTypeResolver: ImageTypeResolver
) : ImageWireframeHelper {

    @Suppress("ReturnCount", "LongMethod")
    @UiThread
    override fun createImageWireframeByBitmap(
        id: Long,
        globalBounds: GlobalBounds,
        bitmap: Bitmap,
        density: Float,
        isContextualImage: Boolean,
        imagePrivacy: ImagePrivacy,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        clipping: MobileSegment.WireframeClip?,
        shapeStyle: MobileSegment.ShapeStyle?,
        border: MobileSegment.ShapeBorder?
    ): MobileSegment.Wireframe {
        if (imagePrivacy == ImagePrivacy.MASK_ALL) {
            return createContentPlaceholderWireframe(
                id = id,
                globalBounds = globalBounds,
                density = density,
                label = MASK_ALL_CONTENT_LABEL
            )
        }

        // in case we suspect the image is PII, return a placeholder
        if (isContextualImage && ImagePrivacy.MASK_LARGE_ONLY == imagePrivacy) {
            return createContentPlaceholderWireframe(
                id = id,
                globalBounds = globalBounds,
                density = density,
                label = MASK_CONTEXTUAL_CONTENT_LABEL
            )
        }

        val imageWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = id,
            globalBounds.x,
            globalBounds.y,
            width = globalBounds.width,
            height = globalBounds.height,
            shapeStyle = shapeStyle,
            border = border,
            clip = clipping,
            isEmpty = true
        )

        asyncJobStatusCallback.jobStarted()

        resourceResolver.resolveResourceId(
            bitmap = bitmap,
            resourceResolverCallback = object : ResourceResolverCallback {
                override fun onSuccess(resourceId: String) {
                    populateResourceIdInWireframe(resourceId, imageWireframe)
                    asyncJobStatusCallback.jobFinished()
                }

                override fun onFailure() {
                    asyncJobStatusCallback.jobFinished()
                }
            }
        )
        return imageWireframe
    }

    @Suppress("ReturnCount", "LongMethod")
    @UiThread
    override fun createImageWireframeByDrawable(
        view: View,
        imagePrivacy: ImagePrivacy,
        currentWireframeIndex: Int,
        x: Long,
        y: Long,
        width: Int,
        height: Int,
        usePIIPlaceholder: Boolean,
        drawable: Drawable,
        drawableCopier: DrawableCopier,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        clipping: MobileSegment.WireframeClip?,
        shapeStyle: MobileSegment.ShapeStyle?,
        border: MobileSegment.ShapeBorder?,
        prefix: String?
    ): MobileSegment.Wireframe? {
        val id = viewIdentifierResolver.resolveChildUniqueIdentifier(view, prefix + currentWireframeIndex)
        val drawableProperties = resolveDrawableProperties(
            view = view,
            drawable = drawable,
            width = width,
            height = height
        )

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

        val density = displayMetrics.density
        val drawableWidthDp = drawableProperties.drawableWidth.densityNormalized(density).toLong()
        val drawableHeightDp = drawableProperties.drawableHeight.densityNormalized(density).toLong()

        if (imagePrivacy == ImagePrivacy.MASK_ALL) {
            return createContentPlaceholderWireframe(
                id = id,
                x = x,
                y = y,
                width = drawableWidthDp,
                height = drawableHeightDp,
                label = MASK_ALL_CONTENT_LABEL,
                clipping = clipping
            )
        }

        // in case we suspect the image is PII, return a placeholder
        if (shouldMaskContextualImage(imagePrivacy, usePIIPlaceholder, drawable, density)) {
            return createContentPlaceholderWireframe(
                id = id,
                x = x,
                y = y,
                width = drawableWidthDp,
                height = drawableHeightDp,
                label = MASK_CONTEXTUAL_CONTENT_LABEL,
                clipping = clipping
            )
        }

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
                isEmpty = true
            )

        asyncJobStatusCallback.jobStarted()

        resourceResolver.resolveResourceId(
            resources = resources,
            applicationContext = applicationContext,
            displayMetrics = displayMetrics,
            originalDrawable = drawableProperties.drawable,
            drawableCopier = drawableCopier,
            drawableWidth = width,
            drawableHeight = height,
            resourceResolverCallback = object : ResourceResolverCallback {
                override fun onSuccess(resourceId: String) {
                    populateResourceIdInWireframe(resourceId, imageWireframe)
                    asyncJobStatusCallback.jobFinished()
                }

                override fun onFailure() {
                    asyncJobStatusCallback.jobFinished()
                }
            }
        )

        return imageWireframe
    }

    @Suppress("NestedBlockDepth")
    @UiThread
    override fun createCompoundDrawableWireframes(
        textView: TextView,
        mappingContext: MappingContext,
        prevWireframeIndex: Int,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): MutableList<MobileSegment.Wireframe> {
        val result = mutableListOf<MobileSegment.Wireframe>()
        var wireframeIndex = prevWireframeIndex
        val density = mappingContext.systemInformation.screenDensity

        // CompoundDrawables returns an array of indexes in the following order:
        // left, top, right, bottom
        textView.compoundDrawables.forEachIndexed { compoundDrawableIndex, _ ->
            if (compoundDrawableIndex > CompoundDrawablePositions.values().size) {
                return@forEachIndexed
            }

            val compoundDrawablePosition = convertIndexToCompoundDrawablePosition(
                compoundDrawableIndex
            ) ?: return@forEachIndexed

            val drawable = textView.compoundDrawables[compoundDrawableIndex]

            if (drawable != null) {
                val drawableCoordinates = viewUtilsInternal.resolveCompoundDrawableBounds(
                    view = textView,
                    drawable = drawable,
                    pixelsDensity = density,
                    position = compoundDrawablePosition
                )

                createImageWireframeByDrawable(
                    view = textView,
                    imagePrivacy = mappingContext.imagePrivacy,
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
                    asyncJobStatusCallback = asyncJobStatusCallback
                )?.let { resultWireframe ->
                    result.add(resultWireframe)
                }
            }
        }

        return result
    }

    private fun resolveDrawableProperties(view: View, drawable: Drawable, width: Int, height: Int): DrawableProperties {
        return when (drawable) {
            is LayerDrawable -> {
                if (drawable.numberOfLayers > 0) {
                    @Suppress("UnsafeThirdPartyFunctionCall") // Can't be out of bounds
                    resolveDrawableProperties(view, drawable.getDrawable(0), width, height)
                } else {
                    DrawableProperties(drawable, drawable.intrinsicWidth, drawable.intrinsicHeight)
                }
            }

            is InsetDrawable -> {
                val internalDrawable = drawable.drawable
                if (internalDrawable != null) {
                    resolveDrawableProperties(
                        view = view,
                        drawable = internalDrawable,
                        width = width,
                        height = height
                    )
                } else {
                    DrawableProperties(drawable, drawable.intrinsicWidth, drawable.intrinsicHeight)
                }
            }

            else -> DrawableProperties(drawable, width, height)
        }
    }

    private fun createContentPlaceholderWireframe(
        id: Long,
        globalBounds: GlobalBounds,
        density: Float,
        label: String
    ): MobileSegment.Wireframe.PlaceholderWireframe {
        return MobileSegment.Wireframe.PlaceholderWireframe(
            id,
            globalBounds.x.densityNormalized(density),
            globalBounds.y.densityNormalized(density),
            globalBounds.width.densityNormalized(density),
            globalBounds.height.densityNormalized(density),
            label = label
        )
    }

    private fun createContentPlaceholderWireframe(
        id: Long,
        x: Long,
        y: Long,
        width: Long,
        height: Long,
        label: String,
        clipping: MobileSegment.WireframeClip?
    ): MobileSegment.Wireframe.PlaceholderWireframe {
        return MobileSegment.Wireframe.PlaceholderWireframe(
            id,
            x,
            y,
            width,
            height,
            label = label,
            clip = clipping
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

    private fun shouldMaskContextualImage(
        imagePrivacy: ImagePrivacy,
        usePIIPlaceholder: Boolean,
        drawable: Drawable,
        density: Float
    ): Boolean =
        imagePrivacy == ImagePrivacy.MASK_LARGE_ONLY &&
            usePIIPlaceholder &&
            imageTypeResolver.isDrawablePII(drawable, density)

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

    private fun populateResourceIdInWireframe(
        resourceId: String,
        wireframe: MobileSegment.Wireframe.ImageWireframe
    ) {
        wireframe.resourceId = resourceId
        wireframe.isEmpty = false
    }

    internal companion object {

        @VisibleForTesting
        internal const val MASK_CONTEXTUAL_CONTENT_LABEL = "Content Image"

        @VisibleForTesting
        internal const val MASK_ALL_CONTENT_LABEL = "Image"

        @VisibleForTesting
        internal const val APPLICATION_CONTEXT_NULL_ERROR = "Application context is null for view %s"

        @VisibleForTesting
        internal const val RESOURCES_NULL_ERROR = "Resources is null for view %s"
    }
}
