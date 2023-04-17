/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.TextView
import androidx.annotation.VisibleForTesting
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.DefaultStringObfuscator
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.StringObfuscator

/**
 * A [WireframeMapper] implementation to map a [TextView] component and apply the
 * [SessionReplayPrivacy.MASK_ALL] masking rule.
 * In this case any [TextView] for which the input type is considered sensible (password, email
 * address, postal address, numeric password) will be masked with the static mask: [***].
 * All the other text fields will be masked with the [DefaultStringObfuscator] which replaces
 * all characters with 'x' and preserves the text length value.
 */
class MaskAllTextViewMapper : TextViewMapper {
    constructor() : super(DefaultStringObfuscator())

    @VisibleForTesting
    internal constructor(
        defaultStringObfuscator: StringObfuscator
    ) : super(defaultStringObfuscator)
}
