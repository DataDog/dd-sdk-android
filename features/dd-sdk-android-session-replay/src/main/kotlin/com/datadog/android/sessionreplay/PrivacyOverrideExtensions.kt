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
