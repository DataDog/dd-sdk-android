/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.TextView
import androidx.annotation.VisibleForTesting
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.recorder.base64.ImageWireframeHelper
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.rules.MaskObfuscationRule
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.rules.TextValueObfuscationRule
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator

/**
 * A [WireframeMapper] implementation to map a [TextView] component and apply the
 * [SessionReplayPrivacy.MASK] masking rule.
 */
open class MaskTextViewMapper : TextViewMapper {
    constructor() : super(textValueObfuscationRule = MaskObfuscationRule())

    internal constructor(
        imageWireframeHelper: ImageWireframeHelper,
        uniqueIdentifierGenerator: UniqueIdentifierGenerator
    ) : super(
        imageWireframeHelper = imageWireframeHelper,
        uniqueIdentifierGenerator = uniqueIdentifierGenerator,
        textValueObfuscationRule = MaskObfuscationRule()
    )

    @VisibleForTesting
    internal constructor(
        imageWireframeHelper: ImageWireframeHelper,
        uniqueIdentifierGenerator: UniqueIdentifierGenerator,
        textValueObfuscationRule: TextValueObfuscationRule
    ) : super(imageWireframeHelper, uniqueIdentifierGenerator, textValueObfuscationRule)
}
