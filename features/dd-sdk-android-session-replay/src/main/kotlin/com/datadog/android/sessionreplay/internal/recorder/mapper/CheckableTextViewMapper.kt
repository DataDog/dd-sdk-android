/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.drawable.Drawable
import android.widget.Checkable
import android.widget.TextView
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
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

internal abstract class CheckableTextViewMapper<T>(
    private val textWireframeMapper: TextViewMapper<T>,
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : CheckableWireframeMapper<T>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) where T : TextView, T : Checkable {

    // region CheckableWireframeMapper

    @UiThread
    override fun resolveMainWireframes(
        view: T,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        return textWireframeMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
    }

    @UiThread
    override fun resolveCheckable(
        view: T,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        return listOfNotNull(
            createCheckableDrawableWireFrames(
                view,
                mappingContext,
                asyncJobStatusCallback
            )
        )
    }

    @UiThread
    override fun resolveMaskedCheckable(view: T, mappingContext: MappingContext): List<MobileSegment.Wireframe>? {
        // TODO RUM-5118: Decide how to display masked checkbox, Currently use old unchecked shape wireframe,
        val checkableId = viewIdentifierResolver.resolveChildUniqueIdentifier(
            view,
            CHECKABLE_KEY_NAME
        ) ?: return null
        val checkBoxColor = resolveCheckableColor(view)
        val checkBoxBounds = resolveCheckableBounds(
            view,
            mappingContext.systemInformation.screenDensity
        )
        val shapeBorder = resolveNotCheckedShapeBorder(view, checkBoxColor)
        val shapeStyle = resolveNotCheckedShapeStyle(view, checkBoxColor)
        return listOf(
            MobileSegment.Wireframe.ShapeWireframe(
                id = checkableId,
                x = checkBoxBounds.x,
                y = checkBoxBounds.y,
                width = checkBoxBounds.width,
                height = checkBoxBounds.height,
                border = shapeBorder,
                shapeStyle = shapeStyle
            )
        )
    }

    // endregion

    // region CheckableTextViewMapper

    @UiThread
    abstract fun resolveCheckableBounds(view: T, pixelsDensity: Float): GlobalBounds

    @UiThread
    private fun createCheckableDrawableWireFrames(
        view: T,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): MobileSegment.Wireframe? {
        // Checkbox drawable has animation transition which produces intermediate wireframe, to avoid that, the final
        // state drawable "checked" or "unchecked" needs to be extracted to generate the correct wireframes.
        return getCheckableDrawable(view)?.let {
            val checkBoxBounds = resolveCheckableBounds(
                view,
                mappingContext.systemInformation.screenDensity
            )
            val drawableCopier = DrawableCopier { _, _ -> cloneCheckableDrawable(view, it) }
            mappingContext.imageWireframeHelper.createImageWireframeByDrawable(
                view = view,
                imagePrivacy = mapInputPrivacyToImagePrivacy(mappingContext.textAndInputPrivacy),
                currentWireframeIndex = 0,
                x = checkBoxBounds.x,
                y = checkBoxBounds.y,
                width = it.intrinsicWidth,
                height = it.intrinsicHeight,
                drawableCopier = drawableCopier,
                drawable = it,
                shapeStyle = null,
                border = null,
                usePIIPlaceholder = true,
                clipping = MobileSegment.WireframeClip(),
                customResourceIdCacheKey = null,
                asyncJobStatusCallback = asyncJobStatusCallback
            )
        }
    }

    abstract fun cloneCheckableDrawable(view: T, drawable: Drawable): Drawable?

    @UiThread
    abstract fun getCheckableDrawable(view: T): Drawable?

    protected open fun resolveCheckableColor(view: T): String {
        return colorStringFormatter.formatColorAndAlphaAsHexString(view.currentTextColor, OPAQUE_ALPHA_VALUE)
    }

    protected open fun resolveNotCheckedShapeStyle(view: T, checkBoxColor: String): MobileSegment.ShapeStyle? {
        return null
    }

    protected open fun resolveNotCheckedShapeBorder(view: T, checkBoxColor: String): MobileSegment.ShapeBorder? {
        return MobileSegment.ShapeBorder(
            color = checkBoxColor,
            width = CHECKABLE_BORDER_WIDTH
        )
    }

    // endregion

    companion object {
        internal const val CHECKABLE_KEY_NAME = "checkable"
        internal const val CHECKABLE_BORDER_WIDTH = 1L
        internal const val CHECK_BOX_CHECKED_DRAWABLE_INDEX = 0
        internal const val CHECK_BOX_NOT_CHECKED_DRAWABLE_INDEX = 1
    }
}
