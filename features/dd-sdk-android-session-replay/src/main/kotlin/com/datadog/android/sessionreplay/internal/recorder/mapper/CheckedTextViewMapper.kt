/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.widget.CheckedTextView
import androidx.annotation.UiThread
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

internal open class CheckedTextViewMapper(
    textWireframeMapper: TextViewMapper<CheckedTextView>,
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : CheckableTextViewMapper<CheckedTextView>(
    textWireframeMapper,
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {

    // region CheckableTextViewMapper

    @UiThread
    override fun resolveCheckableColor(view: CheckedTextView): String {
        val color = view.checkMarkTintList?.defaultColor ?: view.currentTextColor
        return colorStringFormatter.formatColorAndAlphaAsHexString(color, OPAQUE_ALPHA_VALUE)
    }

    @UiThread
    override fun resolveCheckableBounds(view: CheckedTextView, pixelsDensity: Float): GlobalBounds {
        val viewGlobalBounds = viewBoundsResolver.resolveViewGlobalBounds(view, pixelsDensity)
        val textViewPaddingRight =
            view.totalPaddingRight.toLong().densityNormalized(pixelsDensity)
        var checkBoxHeight = 0L
        val checkMarkDrawable = view.checkMarkDrawable
        if (checkMarkDrawable != null && checkMarkDrawable.intrinsicHeight > 0) {
            val height = checkMarkDrawable.intrinsicHeight -
                view.totalPaddingTop -
                view.totalPaddingBottom
            checkBoxHeight = height.toLong().densityNormalized(pixelsDensity)
        }

        return GlobalBounds(
            x = viewGlobalBounds.x + viewGlobalBounds.width - textViewPaddingRight,
            y = viewGlobalBounds.y,
            width = checkBoxHeight,
            height = checkBoxHeight

        )
    }

    override fun getCheckableDrawable(view: CheckedTextView): Drawable? {
        // drawable from [CheckedTextView] can not be retrieved according to the state,
        // so here two hardcoded indexes are used to retrieve "checked" and "not checked" drawables.
        val checkableDrawableIndex = if (view.isChecked) {
            CHECK_BOX_CHECKED_DRAWABLE_INDEX
        } else {
            CHECK_BOX_NOT_CHECKED_DRAWABLE_INDEX
        }

        return (view.checkMarkDrawable?.constantState as? DrawableContainer.DrawableContainerState)?.getChild(
            checkableDrawableIndex
        )?.constantState?.newDrawable(view.resources)?.apply {
            // Set state to make the drawable have correct tint according to the state.
            setState(view.drawableState)
            // Set tint list to drawable if the button has declared `checkMarkTint` attribute.
            view.checkMarkTintList?.let {
                setTintList(view.checkMarkTintList)
            }
        }
    }
    // endregion
}
