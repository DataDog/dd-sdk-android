/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.view.View

@Suppress("UNUSED_PARAMETER")
internal class SnapshotProducer {

    fun produce(rootView: View, scale: Float): Node? {
        // TODO: RUMM-2356
        return null
    }
}
