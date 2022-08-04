/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.processor

import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.Node
import com.datadog.android.sessionreplay.recorder.OrientationChanged

internal class SnapshotProcessor : Processor {
    override fun process(node: Node) {
        // TODO RUMM-2271
    }

    override fun process(event: OrientationChanged) {
        // TODO RUMM-2271
    }

    override fun process(touchData: MobileSegment.MobileIncrementalData.TouchData) {
        // TODO RUMM-2271
    }
}
