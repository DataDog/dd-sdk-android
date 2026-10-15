/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedShapeStyle
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter

/** Copies foreground decorations independently of the content that paints beneath them. */
internal class ViewForegroundCapture(
    private val internalLogger: InternalLogger,
    private val rasterizer: ViewForegroundRasterizer = DefaultViewForegroundRasterizer(internalLogger),
    private val colorResolver: ForegroundColorResolver = DefaultForegroundColorResolver(internalLogger),
    private val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter,
    private val pixelCapture: ViewPixelCapture = ViewPixelCapture(internalLogger)
) {
    @UiThread
    @Suppress("ReturnCount") // Missing, transparent and privacy-restricted decorations contribute nothing.
    fun capture(view: View, context: CapturedMappingContext): CapturedForeground? {
        val foreground = view.foreground ?: return null
        val bounds = context.bounds ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        // A separate owner prevents collisions with the view's background shape or image identity.
        val identity = context.identityFactory.layer(context.ownerIdentity, FOREGROUND_LAYER_ID)
        if (foreground.javaClass == ColorDrawable::class.java && view.hasFullBoundsForeground()) {
            val color = colorResolver.resolve(foreground as ColorDrawable) ?: return null
            if (color ushr ALPHA_SHIFT == 0) return null
            return CapturedForeground(
                identity,
                CapturedWireframe.Shape(
                    context.identityFactory.shapeWireframe(identity),
                    bounds,
                    style = CapturedShapeStyle(backgroundColor = colorStringFormatter.formatColorAsHexString(color))
                )
            )
        }

        val wireframe = pixelCapture.captureDecoration(view, context.copy(ownerIdentity = identity)) {
            rasterizer.rasterize(view)
        } ?: return null
        return CapturedForeground(identity, wireframe)
    }

    private fun View.hasFullBoundsForeground(): Boolean =
        foregroundGravity == Gravity.FILL && paddingLeft == 0 && paddingTop == 0 &&
            paddingRight == 0 && paddingBottom == 0 && scrollX == 0 && scrollY == 0

    private companion object {
        const val FOREGROUND_LAYER_ID = "foreground"
        const val ALPHA_SHIFT = 24
    }
}

internal fun interface ViewForegroundRasterizer {
    fun rasterize(view: View): Bitmap?
}

/** Android resolves foreground gravity, padding, layout direction and current drawable state here. */
internal class DefaultViewForegroundRasterizer(private val internalLogger: InternalLogger) : ViewForegroundRasterizer {
    @UiThread
    @Suppress("TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall") // Draw failures caught below.
    override fun rasterize(view: View): Bitmap? {
        var bitmap: Bitmap? = null
        return try {
            val captured = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            bitmap = captured
            val canvas = Canvas(captured)
            canvas.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
            view.onDrawForeground(canvas)
            captured
        } catch (e: Exception) {
            bitmap?.recycle()
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.TELEMETRY,
                { "Failed to rasterize view foreground" },
                e
            )
            null
        }
    }
}

internal fun interface ForegroundColorResolver {
    fun resolve(drawable: ColorDrawable): Int?
}

/** Sampling a uniform drawable preserves tint and drawable alpha without multiplying the view alpha. */
internal class DefaultForegroundColorResolver(private val internalLogger: InternalLogger) : ForegroundColorResolver {
    @UiThread
    @Suppress("TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall") // Draw failures caught below.
    override fun resolve(drawable: ColorDrawable): Int? {
        var bitmap: Bitmap? = null
        return try {
            val captured = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            bitmap = captured
            val originalBounds = drawable.copyBounds()
            try {
                drawable.setBounds(0, 0, 1, 1)
                drawable.draw(Canvas(captured))
                captured.getPixel(0, 0)
            } finally {
                drawable.bounds = originalBounds
            }
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.TELEMETRY,
                { "Failed to resolve foreground color" },
                e
            )
            null
        } finally {
            bitmap?.recycle()
        }
    }
}
