/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.TextView
import com.datadog.android.sessionreplay.SessionReplayPrivacy

/**
 * A [WireframeMapper] implementation to map a [TextView] component and apply the
 * [SessionReplayPrivacy.MASK_ALL] masking rule.
 */
class MaskAllTextViewMapper : TextWireframeMapper {

    private val stringObfuscator: StringObfuscator

    constructor() {
        stringObfuscator = StringObfuscator()
    }

    internal constructor(stringObfuscator: StringObfuscator) {
        this@MaskAllTextViewMapper.stringObfuscator = stringObfuscator
    }

    override fun resolveTextValue(textView: TextView): String {
        return stringObfuscator.obfuscate(super.resolveTextValue(textView))
    }
}
