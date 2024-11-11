/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.view.View
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper

/**
 * In case you need to provide different configuration for a specific Android UI framework that
 * is not supported by our SR instrumentation layer (e.g Material elements)
 * you can implement this class.
 * @see [SessionReplayConfiguration.Builder.addExtensionSupport]
 */
interface ExtensionSupport {

    /**
     * Identifier for the extension.
     * @return the name of this extension.
     */
    fun name(): String

    /**
     * Use this method if you want to apply a custom [WireframeMapper] for a specific [View].
     * @return the list of [MapperTypeWrapper]
     */
    fun getCustomViewMappers(): List<MapperTypeWrapper<*>>

    /**
     * Implement this method if you need to return some specific implementations for the
     * [OptionSelectorDetector].
     * @return a list of custom [OptionSelectorDetector].
     */
    fun getOptionSelectorDetectors(): List<OptionSelectorDetector>

    /**
     * Implement this method if you need to add some specific mapper of drawable for extensions.
     * @return a list of custom [DrawableToColorMapper] implementation.
     */
    fun getCustomDrawableMapper(): List<DrawableToColorMapper>
}
