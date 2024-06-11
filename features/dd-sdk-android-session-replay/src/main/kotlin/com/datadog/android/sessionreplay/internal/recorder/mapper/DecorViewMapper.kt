/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.NoOpAsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import java.util.Locale

internal class DecorViewMapper(
    private val viewWireframeMapper: ViewWireframeMapper,
    private val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
) : WireframeMapper<View> {

    @UiThread
    override fun map(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        val wireframes = viewWireframeMapper.map(view, mappingContext, NoOpAsyncJobStatusCallback(), internalLogger)
            .toMutableList()
        if (mappingContext.systemInformation.themeColor != null) {
            // we add the background color from the theme to the decorView
            addShapeStyleFromThemeIfNeeded(
                mappingContext.systemInformation.themeColor,
                wireframes,
                view
            )
        }
        val decorClassName = view.javaClass.name
        // we will add the window wireframe here which comes in handy whenever we have to record a
        // pop - up. Usually the pop - up gets displayed in a smaller decorView in a upper Window.
        // the window behind is blurred so we need this extra Wireframe here to achieve that
        // effect in the player. We will only do this for `DialogWindow@DecorView` and not for
        // `PopUpWindow@PopUpDecorView` types in order to follow what the system does. We do not have
        // a way to access that type because it is private in the Android system so we will try to assert
        // the class name in this case. Quite a nasty workaround but given the limitations is the best
        // we could find. We know that the system classes are not obfuscated by Proguard so in case
        // this class name will be changed in the future we will see it in our replays and fix it.
        if (!decorClassName.lowercase(Locale.US)
                .endsWith(POP_UP_DECOR_VIEW_CLASS_NAME_SUFFIX)
        ) {
            viewIdentifierResolver.resolveChildUniqueIdentifier(
                view,
                WINDOW_KEY_NAME
            )?.let {
                val windowWireframe = MobileSegment.Wireframe.ShapeWireframe(
                    id = it,
                    x = 0,
                    y = 0,
                    width = mappingContext.systemInformation.screenBounds.width,
                    height = mappingContext.systemInformation.screenBounds.height,
                    shapeStyle = MobileSegment.ShapeStyle(
                        backgroundColor = WINDOW_WIREFRAME_COLOR,
                        opacity = WINDOW_WIREFRAME_OPACITY
                    )
                )
                wireframes.add(0, windowWireframe)
            }
        }
        return wireframes
    }

    private fun addShapeStyleFromThemeIfNeeded(
        themeColor: String,
        wireframes: MutableList<MobileSegment.Wireframe>,
        view: View
    ) {
        val rootNonEmptyWireframe = wireframes.filterIsInstance<MobileSegment.Wireframe.ShapeWireframe>()
            .firstOrNull { it.shapeStyle != null }

        // we add a shapeStyle based on the Theme color in case the
        // root wireframe does not have a ShapeStyle
        if (rootNonEmptyWireframe == null) {
            val shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = themeColor,
                opacity = view.alpha
            )
            for (i in 0 until wireframes.size) {
                val oldWireframe = wireframes[i]
                if (oldWireframe is MobileSegment.Wireframe.ShapeWireframe) {
                    wireframes[i] = oldWireframe.copy(shapeStyle = shapeStyle)
                }
            }
        }
    }

    companion object {
        internal const val POP_UP_DECOR_VIEW_CLASS_NAME_SUFFIX = "popupdecorview"
        internal const val WINDOW_WIREFRAME_COLOR = "#000000FF"
        internal const val WINDOW_WIREFRAME_OPACITY = 0.6f
        internal const val WINDOW_KEY_NAME = "window"
    }
}
