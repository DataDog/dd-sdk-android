/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewTreeObserver
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.SessionReplayPrivacy

internal fun interface OnDrawListenerProducer {
    fun create(
        decorViews: List<View>,
        privacy: SessionReplayPrivacy,
        imagePrivacy: ImagePrivacy
    ): ViewTreeObserver.OnDrawListener
}
