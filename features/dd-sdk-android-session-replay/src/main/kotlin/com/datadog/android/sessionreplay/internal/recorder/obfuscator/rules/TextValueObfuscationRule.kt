/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.obfuscator.rules

import android.widget.TextView
import com.datadog.android.sessionreplay.recorder.MappingContext

/**
 * Will be used to apply the masking strategy for a [TextView] following the rules defined
 * in the document: [Privacy Options Rules](https://datadoghq.atlassian.net/wiki/spaces/RUMP/pages/2945942237/Privacy+Options+in+Mobile+Session+Replay).
 */
internal interface TextValueObfuscationRule {

    fun resolveObfuscatedValue(textView: TextView, mappingContext: MappingContext): String
}
