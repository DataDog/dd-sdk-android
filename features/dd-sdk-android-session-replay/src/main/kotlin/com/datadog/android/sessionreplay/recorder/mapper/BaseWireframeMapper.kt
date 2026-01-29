/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.graphics.drawable.Drawable
import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.ViewIdentityProvider
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * A basic abstract [WireframeMapper] that provides some helpful utilities.
 *
 * It provides functions to:
 *  - resolve a stable id for any [View]
 *  - converts a [Drawable] into a [MobileSegment.ShapeStyle]
 *
 *  @param T the type of the [View] to map
 *  @property viewIdentifierResolver the [ViewIdentifierResolver] (to resolve a view or children stable id)
 *  @property colorStringFormatter the [ColorStringFormatter] to transform Color into HTML hex strings
 *  @property viewBoundsResolver the [ViewBoundsResolver] to get a view boundaries in density independent units
 *  @property drawableToColorMapper the [DrawableToColorMapper] to convert a background drawable into a solid color
 */
abstract class BaseWireframeMapper<in T : View>(
    protected val viewIdentifierResolver: ViewIdentifierResolver,
    protected val colorStringFormatter: ColorStringFormatter,
    protected val viewBoundsResolver: ViewBoundsResolver,
    protected val drawableToColorMapper: DrawableToColorMapper
) : WireframeMapper<T> {

    /**
     * Resolves the [View] unique id to be used in the mapped [MobileSegment.Wireframe].
     */
    protected fun resolveViewId(view: View): Long {
        return viewIdentifierResolver.resolveViewId(view)
    }

    /**
     * Resolves the stable identity for a view, used for heatmap correlation.
     * This identity is stable across sessions and is based on the view's canonical path.
     *
     * ## When to use this method
     *
     * Use `resolveViewIdentity(view, viewIdentityProvider)` when the wireframe directly corresponds
     * to a View in the Android hierarchy. This includes both interactive elements (buttons, etc.)
     * and non-interactive elements (text labels, images, etc.) - any View the user might tap on.
     *
     * Omit the identity (defaults to null) when the wireframe is synthetic - i.e., it doesn't
     * correspond to a real View:
     * - Visual sub-components (e.g., progress bar fill, seek bar thumb, picker dividers)
     * - System-level decorations (e.g., window background in DecorViewMapper)
     *
     * @param view the view to resolve identity for
     * @param viewIdentityProvider the provider for generating stable view identities
     * @return the stable identity hash, or null if the view's canonical path cannot be determined
     *         (e.g., detached view)
     */
    protected fun resolveViewIdentity(view: View, viewIdentityProvider: ViewIdentityProvider): String? {
        return viewIdentityProvider.resolveIdentity(view)
    }

    /**
     * Resolves the [MobileSegment.ShapeStyle] based on the [View] drawables.
     */
    protected fun resolveShapeStyle(
        drawable: Drawable,
        viewAlpha: Float,
        internalLogger: InternalLogger
    ): MobileSegment.ShapeStyle? {
        val color = drawableToColorMapper.mapDrawableToColor(drawable, internalLogger)
        return if (color != null) {
            MobileSegment.ShapeStyle(colorStringFormatter.formatColorAsHexString(color), viewAlpha)
        } else {
            null
        }
    }
}
