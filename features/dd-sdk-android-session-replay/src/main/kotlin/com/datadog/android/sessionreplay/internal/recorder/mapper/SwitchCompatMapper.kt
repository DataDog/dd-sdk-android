/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import androidx.annotation.UiThread
import androidx.appcompat.widget.SwitchCompat
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.utils.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.recorder.resources.DrawableCopier
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

internal open class SwitchCompatMapper(
    private val textWireframeMapper: TextViewMapper<SwitchCompat>,
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : CheckableWireframeMapper<SwitchCompat>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {

    // region CheckableWireframeMapper

    @UiThread
    override fun resolveMainWireframes(
        view: SwitchCompat,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        return textWireframeMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
    }

    private fun createSwitchCompatDrawableWireFrames(
        view: SwitchCompat,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        var index = 0
        val thumbWireframe = createThumbWireframe(view, index, mappingContext, asyncJobStatusCallback)
        if (thumbWireframe != null) {
            index++
        }
        val trackWireframe = createTrackWireframe(view, index, mappingContext, asyncJobStatusCallback)
        return listOfNotNull(trackWireframe, thumbWireframe)
    }

    private fun createTrackWireframe(
        view: SwitchCompat,
        prevIndex: Int,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): MobileSegment.Wireframe? {
        val trackBounds = resolveTrackBounds(
            view,
            mappingContext.systemInformation.screenDensity
        )
        return trackBounds?.let {
            val trackDrawable = view.trackDrawable
            val drawableCopier = DrawableCopier { originalDrawable, resources ->
                originalDrawable.constantState?.newDrawable(resources)?.apply {
                    setState(view.trackDrawable.state)
                    bounds = view.trackDrawable.bounds
                    view.trackTintList?.let { tintList ->
                        setTintList(tintList)
                    }
                }
            }
            return mappingContext.imageWireframeHelper.createImageWireframeByDrawable(
                view = view,
                imagePrivacy = mapInputPrivacyToImagePrivacy(mappingContext.textAndInputPrivacy),
                currentWireframeIndex = prevIndex + 1,
                x = it.x.densityNormalized(mappingContext.systemInformation.screenDensity)
                    .toLong(),
                y = it.y.densityNormalized(mappingContext.systemInformation.screenDensity)
                    .toLong(),
                width = it.width,
                height = it.height,
                drawable = trackDrawable,
                drawableCopier = drawableCopier,
                shapeStyle = null,
                border = null,
                usePIIPlaceholder = true,
                customResourceIdCacheKey = null,
                asyncJobStatusCallback = asyncJobStatusCallback
            )
        }
    }

    private fun createThumbWireframe(
        view: SwitchCompat,
        prevIndex: Int,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): MobileSegment.Wireframe? {
        val thumbBounds = resolveThumbBounds(
            view,
            mappingContext.systemInformation.screenDensity
        )

        val thumbDrawable = view.thumbDrawable
        val drawableCopier =
            DrawableCopier { originalDrawable, resources ->
                originalDrawable.constantState?.newDrawable(resources)?.apply {
                    setState(view.thumbDrawable.state)
                    bounds = view.thumbDrawable.bounds
                    view.thumbTintList?.let {
                        setTintList(it)
                    }
                }
            }
        return thumbBounds?.let {
            mappingContext.imageWireframeHelper.createImageWireframeByDrawable(
                view = view,
                imagePrivacy = mapInputPrivacyToImagePrivacy(mappingContext.textAndInputPrivacy),
                currentWireframeIndex = prevIndex + 1,
                x = it.x.densityNormalized(mappingContext.systemInformation.screenDensity)
                    .toLong(),
                y = it.y.densityNormalized(mappingContext.systemInformation.screenDensity)
                    .toLong(),
                width = thumbDrawable.intrinsicWidth,
                height = thumbDrawable.intrinsicHeight,
                drawable = thumbDrawable,
                drawableCopier = drawableCopier,
                shapeStyle = null,
                border = null,
                usePIIPlaceholder = true,
                clipping = null,
                customResourceIdCacheKey = null,
                asyncJobStatusCallback = asyncJobStatusCallback
            )
        }
    }

    private fun resolveThumbBounds(view: SwitchCompat, pixelsDensity: Float): GlobalBoundsInPx? {
        val viewGlobalBounds = viewBoundsResolver.resolveViewGlobalBounds(view, pixelsDensity)
        val thumbDimensions = resolveThumbSizeInPx(view) ?: return null
        val thumbLeft = (viewGlobalBounds.x * pixelsDensity).toInt() +
            view.thumbDrawable.bounds.left
        val thumbTop = (viewGlobalBounds.y * pixelsDensity).toInt() +
            view.thumbDrawable.bounds.top
        return GlobalBoundsInPx(
            x = thumbLeft,
            y = thumbTop,
            width = thumbDimensions.first,
            height = thumbDimensions.second
        )
    }

    private fun resolveTrackBounds(view: SwitchCompat, pixelsDensity: Float): GlobalBoundsInPx? {
        val viewGlobalBounds = viewBoundsResolver.resolveViewGlobalBounds(view, pixelsDensity)
        val trackSize = resolveTrackSizeInPx(view) ?: return null
        return view.trackDrawable?.let {
            GlobalBoundsInPx(
                x = (viewGlobalBounds.x * pixelsDensity).toInt() + it.bounds.left,
                y = (viewGlobalBounds.y * pixelsDensity).toInt() + it.bounds.top,
                width = trackSize.first,
                height = trackSize.second
            )
        }
    }

    @UiThread
    override fun resolveCheckable(
        view: SwitchCompat,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        return createSwitchCompatDrawableWireFrames(view, mappingContext, asyncJobStatusCallback)
    }

    @UiThread
    override fun resolveMaskedCheckable(
        view: SwitchCompat,
        mappingContext: MappingContext
    ): List<MobileSegment.Wireframe>? {
        val pixelsDensity = mappingContext.systemInformation.screenDensity
        val wireframes = mutableListOf<MobileSegment.Wireframe>()
        val trackBounds = resolveTrackBounds(view, pixelsDensity) ?: return null
        val checkableColor = resolveCheckableColor(view)

        val trackId = viewIdentifierResolver.resolveChildUniqueIdentifier(view, TRACK_KEY_NAME)
        if (trackId != null) {
            val trackShapeStyle = resolveTrackShapeStyle(view, checkableColor)
            val trackWireframe = MobileSegment.Wireframe.ShapeWireframe(
                id = trackId,
                x = trackBounds.x.densityNormalized(pixelsDensity).toLong(),
                y = trackBounds.y.densityNormalized(pixelsDensity).toLong(),
                width = trackBounds.width.densityNormalized(pixelsDensity).toLong(),
                height = trackBounds.height.densityNormalized(pixelsDensity).toLong(),
                border = null,
                shapeStyle = trackShapeStyle
            )
            wireframes.add(trackWireframe)
        }

        return wireframes
    }

    // endregion

    // region Internal

    private fun resolveCheckableColor(view: SwitchCompat): String {
        return colorStringFormatter.formatColorAndAlphaAsHexString(view.currentTextColor, OPAQUE_ALPHA_VALUE)
    }

    private fun resolveTrackShapeStyle(view: SwitchCompat, checkBoxColor: String): MobileSegment.ShapeStyle {
        return MobileSegment.ShapeStyle(
            backgroundColor = checkBoxColor,
            view.alpha
        )
    }

    private fun resolveThumbSizeInPx(view: SwitchCompat): Pair<Width, Height>? {
        return view.thumbDrawable?.let {
            Pair(it.intrinsicWidth, it.intrinsicHeight)
        }
    }

    private fun resolveTrackSizeInPx(view: SwitchCompat): Pair<Width, Height>? {
        return view.trackDrawable?.let {
            // NinePatchDrawable optical size depends on its size
            Pair(it.bounds.width(), it.bounds.height())
        }
    }

    /**
     * Similar to [GlobalBounds] but in pixel.
     */
    data class GlobalBoundsInPx(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )

    // endregion

    companion object {
        internal const val THUMB_KEY_NAME = "thumb"
        internal const val TRACK_KEY_NAME = "track"
    }
}

private typealias Width = Int
private typealias Height = Int
