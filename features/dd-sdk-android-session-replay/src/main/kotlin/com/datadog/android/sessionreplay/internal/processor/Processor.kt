/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.model.MobileSegment

internal interface Processor {

    fun processScreenSnapshots(nodes: List<Node>, systemInformation: SystemInformation)

    fun processTouchEventsRecords(touchEventsRecords: List<MobileSegment.MobileRecord>)
}
