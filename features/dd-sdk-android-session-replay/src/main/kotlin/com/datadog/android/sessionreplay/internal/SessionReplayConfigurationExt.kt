/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.core.internal.remote.model.RemoteConfiguration
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy

/**
 * Applies a [RemoteConfiguration] to this [SessionReplayConfiguration], returning a new instance
 * with the remote values overlaid on top of the developer-supplied configuration.
 *
 * Fields absent from the remote payload (null) are left unchanged. Only the `sessionReplay`
 * section of the remote configuration is consumed here; other sections (rum, profiling, trace) are
 * handled by their respective feature modules.
 */
internal fun SessionReplayConfiguration.applyRemoteConfiguration(
    rc: RemoteConfiguration?
): SessionReplayConfiguration {
    val sr = rc?.sessionReplay ?: return this
    return copy(
        sampleRate = sr.sampleRate?.toFloat() ?: sampleRate,
        textAndInputPrivacy = sr.textAndInputPrivacy?.toSdkPrivacy() ?: textAndInputPrivacy,
        imagePrivacy = sr.imagePrivacy?.toSdkPrivacy() ?: imagePrivacy,
        touchPrivacy = sr.touchPrivacy?.toSdkPrivacy() ?: touchPrivacy
    )
}

private fun RemoteConfiguration.TextAndInputPrivacy.toSdkPrivacy(): TextAndInputPrivacy =
    when (this) {
        RemoteConfiguration.TextAndInputPrivacy.MASK_SENSITIVE_INPUTS ->
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
        RemoteConfiguration.TextAndInputPrivacy.MASK_ALL_INPUTS ->
            TextAndInputPrivacy.MASK_ALL_INPUTS
        RemoteConfiguration.TextAndInputPrivacy.MASK_ALL ->
            TextAndInputPrivacy.MASK_ALL
    }

private fun RemoteConfiguration.ImagePrivacy.toSdkPrivacy(): ImagePrivacy =
    when (this) {
        RemoteConfiguration.ImagePrivacy.MASK_NONE -> ImagePrivacy.MASK_NONE
        RemoteConfiguration.ImagePrivacy.MASK_LARGE_ONLY -> ImagePrivacy.MASK_LARGE_ONLY
        RemoteConfiguration.ImagePrivacy.MASK_ALL -> ImagePrivacy.MASK_ALL
    }

private fun RemoteConfiguration.TouchPrivacy.toSdkPrivacy(): TouchPrivacy =
    when (this) {
        RemoteConfiguration.TouchPrivacy.SHOW -> TouchPrivacy.SHOW
        RemoteConfiguration.TouchPrivacy.HIDE -> TouchPrivacy.HIDE
    }
