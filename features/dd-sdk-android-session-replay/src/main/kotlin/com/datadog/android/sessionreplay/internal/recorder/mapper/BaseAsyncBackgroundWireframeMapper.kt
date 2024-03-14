/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.internal.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.internal.recorder.resources.ImageWireframeHelper
import com.datadog.android.sessionreplay.internal.recorder.resources.ImageWireframeHelperCallback
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

@Suppress("UndocumentedPublicClass")
abstract class BaseAsyncBackgroundWireframeMapper<T : View>(
    stringUtils: StringUtils = StringUtils,
    viewUtils: ViewUtils = ViewUtils
) : BaseWireframeMapper<T, MobileSegment.Wireframe>(stringUtils, viewUtils) {

    // Why is this nullable ???
    // TODO: RUM-0000 Make the ImageWireframeHelper non nullable
    private var imageWireframeHelper: ImageWireframeHelper? = null
    private var uniqueIdentifierGenerator = UniqueIdentifierGenerator

    internal constructor(
        imageWireframeHelper: ImageWireframeHelper,
        uniqueIdentifierGenerator: UniqueIdentifierGenerator
    ) : this() {
        this.imageWireframeHelper = imageWireframeHelper
        this.uniqueIdentifierGenerator = uniqueIdentifierGenerator
    }

    /**
     * Maps the [View] into a list of [MobileSegment.Wireframe].
     */
    override fun map(
        view: T,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        val wireframes = mutableListOf<MobileSegment.Wireframe>()

        resolveViewBackground(view, asyncJobStatusCallback)?.let {
            wireframes.add(it)
        }

        return wireframes
    }

    private fun resolveViewBackground(
        view: View,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): MobileSegment.Wireframe? {
        val (shapeStyle, border) = view.background?.resolveShapeStyleAndBorder(view.alpha)
            ?: (null to null)

        val resources = view.resources
        val density = resources.displayMetrics.density
        val bounds = resolveViewGlobalBounds(view, density)
        val width = view.width
        val height = view.height

        return if (border == null && shapeStyle == null) {
            resolveBackgroundAsImageWireframe(
                view = view,
                bounds = bounds,
                width = width,
                height = height,
                asyncJobStatusCallback = asyncJobStatusCallback
            )
        } else {
            resolveBackgroundAsShapeWireframe(
                view = view,
                bounds = bounds,
                width = width,
                height = height,
                shapeStyle = shapeStyle,
                border = border
            )
        }
    }

    private fun resolveBackgroundAsShapeWireframe(
        view: View,
        bounds: GlobalBounds,
        width: Int,
        height: Int,
        shapeStyle: MobileSegment.ShapeStyle?,
        border: MobileSegment.ShapeBorder?
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
            border = border
        )
    }

    private fun resolveBackgroundAsImageWireframe(
        view: View,
        bounds: GlobalBounds,
        width: Int,
        height: Int,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): MobileSegment.Wireframe? {
        val resources = view.resources

        val drawableCopy = view.background?.constantState?.newDrawable(resources)
        @Suppress("ThreadSafety") // TODO REPLAY-1861 caller thread of .map is unknown?
        return imageWireframeHelper?.createImageWireframe(
            view = view,
            0,
            x = bounds.x,
            y = bounds.y,
            width,
            height,
            clipping = MobileSegment.WireframeClip(),
            drawable = drawableCopy,
            shapeStyle = null,
            border = null,
            prefix = PREFIX_BACKGROUND_DRAWABLE,
            usePIIPlaceholder = false,
            imageWireframeHelperCallback = object : ImageWireframeHelperCallback {
                override fun onFinished() {
                    asyncJobStatusCallback.jobFinished()
                }

                override fun onStart() {
                    asyncJobStatusCallback.jobStarted()
                }
            }
        )
    }

    companion object {
        private const val PREFIX_BACKGROUND_DRAWABLE = "backgroundDrawable"
    }
}
