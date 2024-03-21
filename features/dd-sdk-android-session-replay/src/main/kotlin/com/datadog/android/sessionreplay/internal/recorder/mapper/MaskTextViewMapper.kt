/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.TextView
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.rules.MaskObfuscationRule
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * A [WireframeMapper] implementation to map a [TextView] component and apply the
 * [SessionReplayPrivacy.MASK] masking rule.
 */
open class MaskTextViewMapper(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : TextViewMapper(
    MaskObfuscationRule(),
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
)
