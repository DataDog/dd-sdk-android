/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.os.Build
import android.widget.CompoundButton
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.utils.densityNormalized
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

internal abstract class CheckableCompoundButtonMapper<T : CompoundButton>(
    textWireframeMapper: TextViewMapper<T>,
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper,
    private val internalLogger: InternalLogger
) : CheckableTextViewMapper<T>(
    textWireframeMapper,
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {

    // region CheckableTextViewMapper

    @UiThread
    override fun resolveCheckableBounds(view: T, pixelsDensity: Float): GlobalBounds {
        val viewGlobalBounds = viewBoundsResolver.resolveViewGlobalBounds(view, pixelsDensity)
        val checkBoxHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.buttonDrawable?.intrinsicHeight?.toLong()?.densityNormalized(pixelsDensity)
                ?: DEFAULT_CHECKABLE_HEIGHT_IN_DP
        } else {
            DEFAULT_CHECKABLE_HEIGHT_IN_DP
        }
        return GlobalBounds(
            x = viewGlobalBounds.x,
            y = viewGlobalBounds.y + (viewGlobalBounds.height - checkBoxHeight) / 2,
            width = checkBoxHeight,
            height = checkBoxHeight
        )
    }

    override fun getCheckableDrawable(view: T): Drawable? {
        val originCheckableDrawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // drawable from [CompoundButton] can not be retrieved according to the state,
            // so here two hardcoded indexes are used to retrieve "checked" and "not checked" drawables.
            val checkableDrawableIndex = if (view.isChecked) {
                CHECK_BOX_CHECKED_DRAWABLE_INDEX
            } else {
                CHECK_BOX_NOT_CHECKED_DRAWABLE_INDEX
            }
            view.buttonDrawable?.let {
                (it.constantState as? DrawableContainer.DrawableContainerState)?.getChild(
                    checkableDrawableIndex
                )
            } ?: kotlin.run {
                internalLogger.log(
                    level = InternalLogger.Level.ERROR,
                    targets = listOf(
                        InternalLogger.Target.MAINTAINER,
                        InternalLogger.Target.TELEMETRY
                    ),
                    messageBuilder = { NULL_BUTTON_DRAWABLE_MSG },
                    additionalProperties = mapOf(
                        "replay.compound.view" to view.javaClass.canonicalName
                    )
                )
                null
            }
        } else {
            // view.buttonDrawable is not available below API 23, so reflection is used to retrieve it.
            try {
                @Suppress("UnsafeThirdPartyFunctionCall")
                // Exceptions have been caught.
                mButtonDrawableField?.get(view) as? Drawable
            } catch (e: IllegalAccessException) {
                internalLogger.log(
                    level = InternalLogger.Level.ERROR,
                    targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    messageBuilder = { GET_DRAWABLE_FAIL_MESSAGE },
                    throwable = e
                )
                null
            } catch (e: IllegalArgumentException) {
                internalLogger.log(
                    level = InternalLogger.Level.ERROR,
                    targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    messageBuilder = { GET_DRAWABLE_FAIL_MESSAGE },
                    throwable = e
                )
                null
            }
        }
        return originCheckableDrawable
    }

    override fun cloneCheckableDrawable(view: T, drawable: Drawable): Drawable? {
        return drawable.constantState?.newDrawable(view.resources)?.apply {
            // Set state to make the drawable have correct tint.
            setState(view.drawableState)
            // Set tint list to drawable if the button has declared `buttonTint` attribute.
            view.buttonTintList?.let {
                setTintList(it)
            }
        }
    }

    // endregion

    companion object {
        internal const val DEFAULT_CHECKABLE_HEIGHT_IN_DP = 32L
        internal const val GET_DRAWABLE_FAIL_MESSAGE =
            "Failed to get buttonDrawable from the checkable compound button."
        internal const val NULL_BUTTON_DRAWABLE_MSG =
            "ButtonDrawable of the compound button is null"

        // Reflects the field at the initialization of the class instead of reflecting it for every wireframe generation
        @Suppress("PrivateApi", "SwallowedException", "TooGenericExceptionCaught")
        internal val mButtonDrawableField = try {
            CompoundButton::class.java.getDeclaredField("mButtonDrawable").apply {
                isAccessible = true
            }
        } catch (e: NoSuchFieldException) {
            null
        } catch (e: SecurityException) {
            null
        } catch (e: NullPointerException) {
            null
        }
    }
}
