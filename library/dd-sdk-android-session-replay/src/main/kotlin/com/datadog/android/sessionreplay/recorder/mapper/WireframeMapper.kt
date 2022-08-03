/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.model.MobileSegment

internal interface WireframeMapper<T : View, S : MobileSegment.Wireframe> {
    fun map(view: T, pixelsDensity: Float): S
}
