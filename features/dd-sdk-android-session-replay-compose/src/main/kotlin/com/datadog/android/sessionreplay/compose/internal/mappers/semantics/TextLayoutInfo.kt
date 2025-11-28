/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.datadog.android.sessionreplay.model.MobileSegment

internal data class TextLayoutInfo(
    val text: String,
    val color: ULong,
    val fontSize: Long,
    val fontFamily: FontFamily?,
    val textAlign: TextAlign? = TextAlign.Start,
    val textOverflow: MobileSegment.TruncationMode? = null
)
