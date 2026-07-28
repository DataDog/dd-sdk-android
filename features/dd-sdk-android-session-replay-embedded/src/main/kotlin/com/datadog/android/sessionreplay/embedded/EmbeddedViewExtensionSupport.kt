/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.embedded

import android.view.View
import com.datadog.android.sessionreplay.ExtensionSupport
import com.datadog.android.sessionreplay.MapperTypeWrapper
import com.datadog.android.sessionreplay.embedded.internal.EmbeddedViewWireframeMapper
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * Generic Session Replay extension support for views rendered by another, embedded rendering
 * engine (e.g. Flutter, React Native, or any other hybrid/cross-platform SDK running inside a
 * native Android screen).
 *
 * Any view whose fully-qualified class name is in [embeddedViewClassNames] is recorded as an
 * embedded content placeholder wireframe (its subtree is not walked any further), so that the
 * Datadog player can composite that engine's own recording into the correct position within the
 * native layout.
 *
 * Each class name is looked up by reflection at runtime: this module has no compile or runtime
 * dependency on any specific embedded engine, so a class name that isn't present on the classpath
 * is simply skipped.
 *
 * @param embeddedViewClassNames the fully-qualified class names of the embedded engine's view(s)
 * to detect, e.g. `"io.flutter.embedding.android.FlutterView"`.
 */
class EmbeddedViewExtensionSupport(private val embeddedViewClassNames: List<String>) : ExtensionSupport {

    constructor(vararg embeddedViewClassNames: String) : this(embeddedViewClassNames.toList())

    private val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
    private val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver
    private val drawableToColorMapper: DrawableToColorMapper = DrawableToColorMapper.getDefault()

    override fun getCustomViewMappers(): List<MapperTypeWrapper<*>> {
        val embeddedViewWireframeMapper = EmbeddedViewWireframeMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )
        return embeddedViewClassNames.mapNotNull { className ->
            resolveViewClass(className)?.let { MapperTypeWrapper(it, embeddedViewWireframeMapper) }
        }
    }

    override fun getOptionSelectorDetectors(): List<OptionSelectorDetector> = emptyList()

    override fun getCustomDrawableMapper(): List<DrawableToColorMapper> = emptyList()

    override fun name(): String = EMBEDDED_VIEW_EXTENSION_SUPPORT_NAME

    @Suppress("SwallowedException", "UnsafeThirdPartyFunctionCall")
    private fun resolveViewClass(className: String): Class<View>? {
        return try {
            @Suppress("UNCHECKED_CAST")
            Class.forName(className) as Class<View>
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    internal companion object {
        internal const val EMBEDDED_VIEW_EXTENSION_SUPPORT_NAME = "EmbeddedViewExtensionSupport"
    }
}
