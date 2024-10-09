/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.view.View

/**
 * Allows setting a view to be "hidden" in the hierarchy in Session Replay.
 * When hidden the view will be replaced with a placeholder in the replay and
 * no attempt will be made to record it's children.
 *
 * @param hide pass `true` to hide the view, or `false` to remove the override
 */
fun View.setSessionReplayHidden(hide: Boolean) {
    if (hide) {
        this.setTag(R.id.datadog_hidden, true)
    } else {
        this.setTag(R.id.datadog_hidden, null)
    }
}

/**
 * Allows overriding the image privacy for a view in Session Replay.
 *
 * @param privacy the new privacy level to use for the view
 * or null to remove the override.
 */
fun View.setSessionReplayImagePrivacy(privacy: ImagePrivacy?) {
    if (privacy == null) {
        this.setTag(R.id.datadog_image_privacy, null)
    } else {
        this.setTag(R.id.datadog_image_privacy, privacy.toString())
    }
}

/**
 * Allows overriding the text and input privacy for a view in Session Replay.
 *
 * @param privacy the new privacy level to use for the view
 * or null to remove the override.
 */
fun View.setSessionReplayTextAndInputPrivacy(privacy: TextAndInputPrivacy?) {
    if (privacy == null) {
        this.setTag(R.id.datadog_text_and_input_privacy, null)
    } else {
        this.setTag(R.id.datadog_text_and_input_privacy, privacy.toString())
    }
}
