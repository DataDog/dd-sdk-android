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
import android.util.DisplayMetrics
import android.view.View
import com.datadog.android.sessionreplay.internal.AsyncImageProcessingCallback
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.base64.Base64Serializer
import com.datadog.android.sessionreplay.internal.recorder.base64.ImageCompression
import com.datadog.android.sessionreplay.internal.recorder.base64.WebPImageCompression
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

@Suppress("UndocumentedPublicClass")
abstract class BaseWireframeMapper<T : View, S : MobileSegment.Wireframe>(
    private val stringUtils: StringUtils = StringUtils,
    private val viewUtils: ViewUtils = ViewUtils,
    private val webPImageCompression: ImageCompression = WebPImageCompression(),
    private val uniqueIdentiferGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator,
    // TODO: REPLAY-1856 find a way to remove base64 dependency from the constructor
    private val base64Serializer: Base64Serializer = Base64Serializer.Builder().build()
) : WireframeMapper<T, S>, AsyncImageProcessingCallback {

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

    /**
     * Resolve a unique identifier for a view.
     */
    protected fun resolveChildDrawableUniqueIdentifier(view: View): Long? =
        uniqueIdentiferGenerator.resolveChildUniqueIdentifier(view, DRAWABLE_CHILD_NAME)

    /**
     * Resolve a mimetype from an extension.
     */
    protected fun getWebPMimeType(): String? =
        webPImageCompression.getMimeType()

    /**
     * Resolve drawable and update image wireframe.
     */
    protected fun handleBitmap(
        displayMetrics: DisplayMetrics,
        drawable: Drawable,
        imageWireframe: MobileSegment.Wireframe.ImageWireframe
    ) =
        base64Serializer.handleBitmap(
            displayMetrics,
            drawable,
            imageWireframe
        )

    internal fun registerAsyncImageProcessingCallback(
        asyncImageProcessingCallback: AsyncImageProcessingCallback
    ) {
        base64Serializer.registerAsyncLoadingCallback(asyncImageProcessingCallback)
    }

    override fun startProcessingImage() {}
    override fun finishProcessingImage() {}

    companion object {
        internal const val OPAQUE_ALPHA_VALUE: Int = 255
        private const val DRAWABLE_CHILD_NAME = "drawable"
    }
}
