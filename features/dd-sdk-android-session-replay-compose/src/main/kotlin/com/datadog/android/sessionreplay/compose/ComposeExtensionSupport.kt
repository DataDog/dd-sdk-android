/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose

import android.view.View
import androidx.compose.ui.platform.ComposeView
import com.datadog.android.sessionreplay.ExtensionSupport
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.compose.internal.mappers.ComposeWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper

/**
 * Jetpack Compose extension support implementation to be used in the Session Replay
 * configuration.
 */
class ComposeExtensionSupport : ExtensionSupport {

    override fun getCustomViewMappers(): Map<SessionReplayPrivacy, Map<Class<*>, WireframeMapper<View, *>>> {
        return SessionReplayPrivacy.values()
            .associateWith { mapOf(composeViewClass to getComposeWireframeMapper(it)) }
            .toMap()
    }

    override fun getOptionSelectorDetectors(): List<OptionSelectorDetector> {
        return emptyList()
    }

    private fun getComposeWireframeMapper(privacy: SessionReplayPrivacy): WireframeMapper<View, *> {
        @Suppress("UNCHECKED_CAST")
        return ComposeWireframeMapper(privacy) as WireframeMapper<View, *>
    }

    companion object {
        private val composeViewClass: Class<*> = ComposeView::class.java
    }
}
