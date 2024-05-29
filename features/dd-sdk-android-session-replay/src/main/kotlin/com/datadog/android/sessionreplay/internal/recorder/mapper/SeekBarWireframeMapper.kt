/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.os.Build.VERSION
import android.widget.SeekBar
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.BaseAsyncBackgroundWireframeMapper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import com.datadog.android.sessionreplay.utils.PARTIALLY_OPAQUE_ALPHA_VALUE
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import kotlin.math.max

internal open class SeekBarWireframeMapper(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : BaseAsyncBackgroundWireframeMapper<SeekBar>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {
    @UiThread
    override fun map(
        view: SeekBar,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        val wireframes = mutableListOf<MobileSegment.Wireframe>()

        // add background if needed
        wireframes.addAll(super.map(view, mappingContext, asyncJobStatusCallback, internalLogger))

        val screenDensity = mappingContext.systemInformation.screenDensity
        val viewPaddedBounds = viewBoundsResolver.resolveViewPaddedBounds(view, screenDensity)

        val trackHeight = TRACK_HEIGHT_IN_PX.densityNormalized(screenDensity)
        val trackBounds = GlobalBounds(
            x = viewPaddedBounds.x,
            y = viewPaddedBounds.y + (viewPaddedBounds.height - trackHeight) / 2,
            width = viewPaddedBounds.width,
            height = trackHeight
        )

        val defaultColor = getDefaultColor(view)
        val trackColor = getColor(view.progressTintList, view.drawableState) ?: defaultColor
        val thumbColor = getColor(view.thumbTintList, view.drawableState) ?: defaultColor

        buildNonActiveTrackWireframe(view, trackBounds, trackColor)?.let(wireframes::add)

        if (mappingContext.privacy == SessionReplayPrivacy.ALLOW) {
            val normalizedProgress = if (VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                normalizedProgressAndroidO(view)
            } else {
                normalizedProgressLegacy(view)
            }
            buildActiveTrackWireframe(view, trackBounds, normalizedProgress, trackColor)?.let(wireframes::add)
            buildThumbWireframe(
                view = view,
                trackBounds = trackBounds,
                normalizedProgress = normalizedProgress,
                trackHeight = trackHeight,
                screenDensity = screenDensity,
                thumbColor = thumbColor
            )?.let(wireframes::add)
        }

        return wireframes
    }

    private fun buildNonActiveTrackWireframe(
        view: SeekBar,
        trackBounds: GlobalBounds,
        trackColor: Int
    ): MobileSegment.Wireframe? {
        val nonActiveTrackId = viewIdentifierResolver.resolveChildUniqueIdentifier(view, NON_ACTIVE_TRACK_KEY_NAME)
            ?: return null
        val backgroundColor = colorStringFormatter.formatColorAndAlphaAsHexString(
            trackColor,
            PARTIALLY_OPAQUE_ALPHA_VALUE
        )
        return MobileSegment.Wireframe.ShapeWireframe(
            id = nonActiveTrackId,
            x = trackBounds.x,
            y = trackBounds.y,
            width = trackBounds.width,
            height = trackBounds.height,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = backgroundColor,
                opacity = view.alpha
            )
        )
    }

    private fun buildActiveTrackWireframe(
        view: SeekBar,
        trackBounds: GlobalBounds,
        normalizedProgress: Float,
        trackColor: Int
    ): MobileSegment.Wireframe? {
        val activeTrackId = viewIdentifierResolver.resolveChildUniqueIdentifier(view, ACTIVE_TRACK_KEY_NAME)
            ?: return null
        val backgroundColor = colorStringFormatter.formatColorAndAlphaAsHexString(
            trackColor,
            OPAQUE_ALPHA_VALUE
        )
        return MobileSegment.Wireframe.ShapeWireframe(
            id = activeTrackId,
            x = trackBounds.x,
            y = trackBounds.y,
            width = (trackBounds.width * normalizedProgress).toLong(),
            height = trackBounds.height,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = backgroundColor,
                opacity = view.alpha
            )
        )
    }

    private fun buildThumbWireframe(
        view: SeekBar,
        trackBounds: GlobalBounds,
        normalizedProgress: Float,
        trackHeight: Long,
        screenDensity: Float,
        thumbColor: Int
    ): MobileSegment.Wireframe? {
        val thumbId = viewIdentifierResolver.resolveChildUniqueIdentifier(view, THUMB_KEY_NAME)
            ?: return null
        val backgroundColor = colorStringFormatter.formatColorAndAlphaAsHexString(thumbColor, OPAQUE_ALPHA_VALUE)

        val thumbWidth = view.thumb.bounds.width().densityNormalized(screenDensity).toLong()
        val thumbHeight = view.thumb.bounds.height().densityNormalized(screenDensity).toLong()
        return MobileSegment.Wireframe.ShapeWireframe(
            id = thumbId,
            x = (trackBounds.x + (trackBounds.width * normalizedProgress).toLong() - (thumbWidth / 2)),
            y = trackBounds.y + (trackHeight / 2) - (thumbHeight / 2),
            width = thumbWidth,
            height = thumbHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = backgroundColor,
                opacity = view.alpha,
                cornerRadius = max(thumbWidth / 2, thumbHeight / 2)
            )
        )
    }

    private fun normalizedProgressLegacy(view: SeekBar): Float {
        val range = view.max.toFloat()
        return if (view.max == 0) {
            0f
        } else {
            view.progress.toFloat() / range
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun normalizedProgressAndroidO(view: SeekBar): Float {
        val range = view.max.toFloat() - view.min.toFloat()
        return if (range == 0f) {
            0f
        } else {
            (view.progress - view.min) / range
        }
    }

    private fun getColor(colorStateList: ColorStateList?, state: IntArray): Int? {
        return colorStateList?.getColorForState(state, colorStateList.defaultColor)
    }

    private fun getDefaultColor(view: SeekBar): Int {
        val uiModeFlags = view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (uiModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            NIGHT_MODE_COLOR
        } else {
            DAY_MODE_COLOR
        }
    }

    companion object {
        internal const val NIGHT_MODE_COLOR = 0xffffff // White
        internal const val DAY_MODE_COLOR = 0 // Black
        internal const val ACTIVE_TRACK_KEY_NAME = "seekbar_active_track"
        internal const val NON_ACTIVE_TRACK_KEY_NAME = "seekbar_non_active_track"
        internal const val THUMB_KEY_NAME = "seekbar_thumb"

        internal const val THUMB_SHAPE_CORNER_RADIUS = 10
        internal const val TRACK_HEIGHT_IN_PX = 8L
    }
}
