/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.textdetection

import com.datadog.android.sessionreplay.ExtensionSupport
import com.datadog.android.sessionreplay.MapperTypeWrapper
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.recorder.privacy.TextDetector
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import java.util.concurrent.Executors

/**
 * On-device text detection extension support implementation to be used in the Session Replay
 * configuration. Adding this to a [com.datadog.android.sessionreplay.SessionReplayConfiguration]
 * lets the experimental composition-tree pipeline's pixel-fallback captures mask visible text
 * before upload; without it, those captures fail closed to a placeholder instead.
 */
class TextDetectionExtensionSupport : ExtensionSupport {

    private val textDetector: TextDetector = MlKitTextDetector(
        callbackExecutor = Executors.newSingleThreadExecutor(),
        timeoutScheduler = Executors.newSingleThreadScheduledExecutor()
    )

    override fun getCustomViewMappers(): List<MapperTypeWrapper<*>> = emptyList()

    override fun getOptionSelectorDetectors(): List<OptionSelectorDetector> = emptyList()

    override fun getCustomDrawableMapper(): List<DrawableToColorMapper> = emptyList()

    override fun getTextDetector(): TextDetector = textDetector

    override fun name(): String = TEXT_DETECTION_EXTENSION_SUPPORT_NAME

    internal companion object {
        internal const val TEXT_DETECTION_EXTENSION_SUPPORT_NAME = "TextDetectionExtensionSupport"
    }
}
