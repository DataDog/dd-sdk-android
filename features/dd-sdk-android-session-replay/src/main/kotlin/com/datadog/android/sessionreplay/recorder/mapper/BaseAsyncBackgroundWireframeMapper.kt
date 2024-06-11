/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.view.View
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * A basic abstract [WireframeMapper] that provides some helpful utilities to
 * resolve the background drawable of the [View] on a background thread, allowing tracking
 * actual images.
 *
 *  @param T the type of the [View] to map
 *  @param viewIdentifierResolver the [ViewIdentifierResolver] (to resolve a view or children stable id)
 *  @param colorStringFormatter the [ColorStringFormatter] to transform Color into HTML hex strings
 *  @param viewBoundsResolver the [ViewBoundsResolver] to get a view boundaries in density independent units
 *  @param drawableToColorMapper the [DrawableToColorMapper] to convert a background drawable into a solid color
 */
abstract class BaseAsyncBackgroundWireframeMapper<in T : View> internal constructor(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : BaseWireframeMapper<T>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {

    private var uniqueIdentifierGenerator = DefaultViewIdentifierResolver

    @UiThread
    override fun map(
        view: T,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        val backgroundWireframe = resolveViewBackground(view, mappingContext, asyncJobStatusCallback, internalLogger)

        return backgroundWireframe?.let { listOf(it) } ?: emptyList()
    }

    @UiThread
    private fun resolveViewBackground(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): MobileSegment.Wireframe? {
        val shapeStyle = view.background?.let { resolveShapeStyle(it, view.alpha, internalLogger) }

        val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.systemInformation.screenDensity)
        val width = view.width
        val height = view.height

        return if (shapeStyle == null) {
            resolveBackgroundAsImageWireframe(
                view = view,
                bounds = bounds,
                width = width,
                height = height,
                mappingContext = mappingContext,
                asyncJobStatusCallback = asyncJobStatusCallback
            )
        } else {
            resolveBackgroundAsShapeWireframe(
                view = view,
                bounds = bounds,
                width = width,
                height = height,
                shapeStyle = shapeStyle
            )
        }
    }

    private fun resolveBackgroundAsShapeWireframe(
        view: View,
        bounds: GlobalBounds,
        width: Int,
        height: Int,
        shapeStyle: MobileSegment.ShapeStyle?
    ): MobileSegment.Wireframe.ShapeWireframe? {
        val id = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(
            view,
            PREFIX_BACKGROUND_DRAWABLE
        ) ?: return null
        val resources = view.resources
        val density = resources.displayMetrics.density

        return MobileSegment.Wireframe.ShapeWireframe(
            id,
            x = bounds.x,
            y = bounds.y,
            width = width.densityNormalized(density).toLong(),
            height = height.densityNormalized(density).toLong(),
            shapeStyle = shapeStyle,
            border = null
        )
    }

    @UiThread
    private fun resolveBackgroundAsImageWireframe(
        view: View,
        bounds: GlobalBounds,
        width: Int,
        height: Int,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): MobileSegment.Wireframe? {
        val resources = view.resources

        val drawableCopy = view.background?.constantState?.newDrawable(resources)
        return if (drawableCopy != null) {
            mappingContext.imageWireframeHelper.createImageWireframe(
                view = view,
                currentWireframeIndex = 0,
                x = bounds.x,
                y = bounds.y,
                width = width,
                height = height,
                usePIIPlaceholder = false,
                drawable = drawableCopy,
                asyncJobStatusCallback = asyncJobStatusCallback,
                clipping = MobileSegment.WireframeClip(),
                shapeStyle = null,
                border = null,
                prefix = PREFIX_BACKGROUND_DRAWABLE
            )
        } else {
            null
        }
    }

    companion object {
        internal const val PREFIX_BACKGROUND_DRAWABLE = "backgroundDrawable"
    }
}
