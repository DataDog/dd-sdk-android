/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.ListView
import androidx.annotation.UiThread
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedShapeStyle
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCapture
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver

/** List chrome is separate from row content, so native row mappers retain their normal privacy rules. */
internal class CapturedListViewMapper(
    private val backgroundMapper: CapturedViewMapper<View>,
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver,
    private val selectorRasterizer: ListSelectorRasterizer = DefaultListSelectorRasterizer()
) : CapturedViewMapper<ListView> {
    @UiThread
    override fun map(view: ListView, mappingContext: CapturedMappingContext): CapturedViewMapperResult {
        val background = backgroundMapper.map(view, mappingContext)
        val wireframes = (background as? CapturedViewMapperResult.Wireframes)?.wireframes.orEmpty().toMutableList()
        captureSelector(view, mappingContext)?.let(wireframes::add)
        captureDividers(view, mappingContext, wireframes)
        // ListView draws its edge effects and dividers itself; drawing the whole list would also
        // include every row, turning independently mappable text into an opaque pixel placeholder.
        return CapturedViewMapperResult.Wireframes(wireframes, isTerminal = false)
    }

    @UiThread
    @Suppress("ReturnCount") // Privacy and missing selector chrome exit before allocating a resource.
    private fun captureSelector(view: ListView, context: CapturedMappingContext): CapturedWireframe.Pixel? {
        val masked = context.textAndInputPrivacy != TextAndInputPrivacy.MASK_SENSITIVE_INPUTS ||
            PixelCaptureEligibility.placeholderLabelForBackground(context.imagePrivacy) != null
        if (masked || !context.shouldContinue()) return null
        val selector = selectorRasterizer.rasterize(view) ?: return null
        val density = if (context.screenDensity > 0f) context.screenDensity else 1f
        val location = viewBoundsResolver.resolveViewGlobalBounds(view, density)
        val owner = context.identityFactory.layer(context.ownerIdentity, "list-selector")
        val identity = context.identityFactory.imageWireframe(owner)
        context.pendingPixelCaptureSink.register(
            PendingPixelCapture(identity, owner, selector.bitmap, isTextFree = true)
        )
        return CapturedWireframe.Pixel(
            identity,
            CapturedBounds(
                location.x + (selector.bounds.left / density).toLong(),
                location.y + (selector.bounds.top / density).toLong(),
                (selector.bounds.width() / density).toLong(),
                (selector.bounds.height() / density).toLong()
            ),
            resource = PixelResource.Unresolved
        )
    }

    @UiThread
    @Suppress("UnsafeThirdPartyFunctionCall") // Child indices are bounded by childCount on the owning UI thread.
    private fun captureDividers(
        view: ListView,
        context: CapturedMappingContext,
        wireframes: MutableList<CapturedWireframe>
    ) {
        val divider = view.divider as? ColorDrawable ?: return
        val dividerHeight = view.dividerHeight
        if (dividerHeight <= 0 || view.childCount == 0) return
        val density = if (context.screenDensity > 0f) context.screenDensity else 1f
        val bounds = context.bounds ?: viewBoundsResolver.resolveViewGlobalBounds(view, density).let {
            CapturedBounds(it.x, it.y, it.width, it.height)
        }
        val left = bounds.x + (view.paddingLeft / density).toLong()
        val width = ((view.width - view.paddingLeft - view.paddingRight) / density).toLong().coerceAtLeast(0L)
        val style = CapturedShapeStyle(
            backgroundColor = DefaultColorStringFormatter.formatColorAsHexString(divider.color)
        )
        for (index in 0 until view.childCount) {
            val position = view.firstVisiblePosition + index
            if (!shouldCaptureDivider(view, position)) continue
            view.getChildAt(index)?.let { child ->
                val y = bounds.y + ((child.bottom - view.scrollY) / density).toLong()
                val owner = context.identityFactory.layer(context.ownerIdentity, "list-divider-$position")
                wireframes += CapturedWireframe.Shape(
                    context.identityFactory.shapeWireframe(owner),
                    CapturedBounds(left, y, width, (dividerHeight / density).toLong().coerceAtLeast(1L)),
                    style = style
                )
            }
        }
    }

    @UiThread
    @Suppress("UnsafeThirdPartyFunctionCall") // These platform getters only read the list's divider flags.
    private fun shouldCaptureDivider(view: ListView, position: Int): Boolean {
        val isHeader = position < view.headerViewsCount
        val touchesFooter = position + 1 >= view.count - view.footerViewsCount
        val enabled = (!isHeader || view.areHeaderDividersEnabled()) &&
            (!touchesFooter || view.areFooterDividersEnabled())
        return position < view.count - 1 && enabled
    }
}
