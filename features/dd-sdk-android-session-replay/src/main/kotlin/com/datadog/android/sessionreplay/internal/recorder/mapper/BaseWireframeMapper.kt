/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import com.datadog.android.sessionreplay.internal.AsyncImageProcessingCallback
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.base64.Base64Serializer
import com.datadog.android.sessionreplay.internal.recorder.base64.ImageWireframeHelper
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

@Suppress("UndocumentedPublicClass")
abstract class BaseWireframeMapper<T : View, S : MobileSegment.Wireframe>(
    private val stringUtils: StringUtils = StringUtils,
    private val viewUtils: ViewUtils = ViewUtils
) : WireframeMapper<T, MobileSegment.Wireframe>, AsyncImageProcessingCallback {
    private var base64Serializer: Base64Serializer? = null
    private var imageWireframeHelper: ImageWireframeHelper? = null
    private var uniqueIdentifierGenerator = UniqueIdentifierGenerator

    internal constructor(
        base64Serializer: Base64Serializer,
        imageWireframeHelper: ImageWireframeHelper,
        uniqueIdentifierGenerator: UniqueIdentifierGenerator
    ) : this() {
        this.base64Serializer = base64Serializer
        this.imageWireframeHelper = imageWireframeHelper
        this.uniqueIdentifierGenerator = uniqueIdentifierGenerator
    }

    /**
     * Maps the [View] into a list of [MobileSegment.Wireframe].
     */
    override fun map(view: T, mappingContext: MappingContext): List<MobileSegment.Wireframe> {
        val wireframes = mutableListOf<MobileSegment.Wireframe>()

        resolveViewBackground(view)?.let {
            wireframes.add(it)
        }

        return wireframes
    }

    /**
     * Resolves the [View] unique id to be used in the mapped [MobileSegment.Wireframe].
     */
    protected fun resolveViewId(view: View): Long {
        // we will use the System.identityHashcode in here which always returns the default
        // hashcode value whether or not a child class overrides this.
        return System.identityHashCode(view).toLong()
    }

    /**
     * Takes color and the alpha value and returns a string formatted color in RGBA format
     * (e.g. #000000FF).
     */
    protected fun colorAndAlphaAsStringHexa(color: Int, alphaAsHexa: Int): String {
        return stringUtils.formatColorAndAlphaAsHexa(color, alphaAsHexa)
    }

    /**
     * Resolves the [View] bounds. These dimensions are already normalized according with
     * the provided [pixelsDensity]. By Global we mean that the View position will not be relative
     * to its parent but to the Device screen.
     */
    protected fun resolveViewGlobalBounds(view: View, pixelsDensity: Float):
        GlobalBounds {
        // RUMM-0000 return an array of primitives here instead of creating an object.
        // This method is being called too often every time we take a screen snapshot
        // and we might want to avoid creating too many instances.
        return viewUtils.resolveViewGlobalBounds(view, pixelsDensity)
    }

    /**
     * Resolves the [MobileSegment.ShapeStyle] and [MobileSegment.ShapeBorder] based on the [View]
     * drawables.
     */
    protected fun Drawable.resolveShapeStyleAndBorder(viewAlpha: Float):
        Pair<MobileSegment.ShapeStyle?, MobileSegment.ShapeBorder?>? {
        return if (this is ColorDrawable) {
            val color = colorAndAlphaAsStringHexa(color, alpha)
            MobileSegment.ShapeStyle(color, viewAlpha) to null
        } else if (this is RippleDrawable && numberOfLayers >= 1) {
            getDrawable(0).resolveShapeStyleAndBorder(viewAlpha)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && this is InsetDrawable) {
            drawable?.resolveShapeStyleAndBorder(viewAlpha)
        } else {
            // We cannot handle this drawable so we will use a border to delimit its container
            // bounds.
            // TODO: RUMM-0000 In case the background drawable could not be handled we should
            // instead resolve it as an ImageWireframe.
            null to null
        }
    }

    internal fun registerAsyncImageProcessingCallback(
        asyncImageProcessingCallback: AsyncImageProcessingCallback
    ) {
        base64Serializer?.registerAsyncLoadingCallback(asyncImageProcessingCallback)
    }

    private fun resolveViewBackground(
        view: View
    ): MobileSegment.Wireframe? {
        val (shapeStyle, border) = view.background?.resolveShapeStyleAndBorder(view.alpha)
            ?: (null to null)

        val resources = view.resources
        val density = resources.displayMetrics.density
        val bounds = resolveViewGlobalBounds(view, density)
        val width = view.width.densityNormalized(density).toLong()
        val height = view.height.densityNormalized(density).toLong()

        return if (border == null && shapeStyle == null) {
            resolveBackgroundAsImageWireframe(
                view = view,
                bounds = bounds,
                width = width,
                height = height
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
        width: Long,
        height: Long,
        shapeStyle: MobileSegment.ShapeStyle?,
        border: MobileSegment.ShapeBorder?
    ): MobileSegment.Wireframe.ShapeWireframe? {
        val id = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(view, PREFIX_BACKGROUND_DRAWABLE)
            ?: return null

        return MobileSegment.Wireframe.ShapeWireframe(
            id,
            x = bounds.x,
            y = bounds.y,
            width = width,
            height = height,
            shapeStyle = shapeStyle,
            border = border
        )
    }

    private fun resolveBackgroundAsImageWireframe(
        view: View,
        bounds: GlobalBounds,
        width: Long,
        height: Long
    ): MobileSegment.Wireframe? {
        @Suppress("ThreadSafety") // TODO REPLAY-1861 caller thread of .map is unknown?
        return imageWireframeHelper?.createImageWireframe(
            view = view,
            0,
            x = bounds.x,
            y = bounds.y,
            width,
            height,
            view.background,
            shapeStyle = null,
            border = null,
            prefix = PREFIX_BACKGROUND_DRAWABLE
        )
    }

    override fun startProcessingImage() {}
    override fun finishProcessingImage() {}

    companion object {
        internal const val OPAQUE_ALPHA_VALUE: Int = 255
        private const val PREFIX_BACKGROUND_DRAWABLE = "backgroundDrawable"
    }
}
