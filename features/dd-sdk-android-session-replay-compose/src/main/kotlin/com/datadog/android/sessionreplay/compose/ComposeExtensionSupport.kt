/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.sessionreplay.compose

import androidx.compose.ui.platform.AndroidComposeView
import androidx.compose.ui.platform.ComposeView
import com.datadog.android.sessionreplay.ExtensionSupport
import com.datadog.android.sessionreplay.MapperTypeWrapper
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.AndroidComposeViewMapper
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.ComposeViewMapper
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.RootSemanticsNodeMapper
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
class ComposeExtensionSupport : ExtensionSupport {

    private val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
    private val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver
    private val drawableToColorMapper: DrawableToColorMapper = DrawableToColorMapper.getDefault()
    private val rootSemanticsNodeMapper = RootSemanticsNodeMapper(colorStringFormatter)

    override fun getCustomViewMappers(): List<MapperTypeWrapper<*>> {
        return listOf(
            MapperTypeWrapper(
                ComposeView::class.java,
                ComposeViewMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper,
                    rootSemanticsNodeMapper = rootSemanticsNodeMapper
                )
            ),
            MapperTypeWrapper(
                AndroidComposeView::class.java,
                AndroidComposeViewMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper,
                    rootSemanticsNodeMapper
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

    override fun name(): String =
        COMPOSE_EXTENSION_SUPPORT_NAME

    internal companion object {
        internal const val COMPOSE_EXTENSION_SUPPORT_NAME = "ComposeExtensionSupport"
    }
}
