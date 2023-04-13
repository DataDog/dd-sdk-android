/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.TextView
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.DefaultStringObfuscator
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.FixedLengthStringObfuscator
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.StringObfuscator

/**
 * A [WireframeMapper] implementation to map a [TextView] component and apply the
 * [SessionReplayPrivacy.MASK_ALL] masking rule.
 */
class MaskAllTextViewMapper : TextWireframeMapper {
    constructor() : super(DefaultStringObfuscator(), FixedLengthStringObfuscator())

    internal constructor(
        defaultStringObfuscator: StringObfuscator,
        staticStringObfuscator: StringObfuscator
    ) :
        super(defaultStringObfuscator, staticStringObfuscator)
}
