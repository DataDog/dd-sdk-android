/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

@Suppress("UndocumentedPublicClass")
abstract class BaseAsyncBackgroundWireframeMapper<T : View> internal constructor(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : BaseWireframeMapper<T, MobileSegment.Wireframe>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {

    private var uniqueIdentifierGenerator = DefaultViewIdentifierResolver

    /**
     * Maps the [View] into a list of [MobileSegment.Wireframe].
     */
    override fun map(
        view: T,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        val backgroundWireframe = resolveViewBackground(view, mappingContext, asyncJobStatusCallback)

        return backgroundWireframe?.let { listOf(it) } ?: emptyList()
    }

    private fun resolveViewBackground(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): MobileSegment.Wireframe? {
        val shapeStyle = view.background?.let { resolveShapeStyle(it, view.alpha) }

        val resources = view.resources
        val density = resources.displayMetrics.density
        val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, density)
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
            @Suppress("ThreadSafety") // TODO RUM-1462 caller thread of .map is unknown?
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
