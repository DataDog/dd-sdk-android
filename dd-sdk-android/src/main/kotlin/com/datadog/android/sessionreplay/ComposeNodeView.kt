/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.view.View
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Remeasurement

class ComposeNodeView(
    val id: Int,
    val parentId:Int,
    val layoutInfo: A,
    val b:B,
    val c:C
)