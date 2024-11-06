/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose

import androidx.compose.ui.platform.ComposeView
import com.datadog.android.sessionreplay.ExtensionSupport
import com.datadog.android.sessionreplay.MapperTypeWrapper
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.SemanticsWireframeMapper
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * Jetpack Compose extension support implementation to be used in the Session Replay
 * configuration.
 */
@ExperimentalSessionReplayApi
class ComposeExtensionSupport : ExtensionSupport {

    private val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
    private val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver
    private val drawableToColorMapper: DrawableToColorMapper = DrawableToColorMapper.getDefault()

    override fun getCustomViewMappers(): List<MapperTypeWrapper<*>> {
        return listOf(
            MapperTypeWrapper(
                ComposeView::class.java,
                SemanticsWireframeMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            )
        )
    }

    override fun getOptionSelectorDetectors(): List<OptionSelectorDetector> {
        return emptyList()
    }

    override fun getCustomDrawableMapper(): List<DrawableToColorMapper> {
        return emptyList()
    }
}
