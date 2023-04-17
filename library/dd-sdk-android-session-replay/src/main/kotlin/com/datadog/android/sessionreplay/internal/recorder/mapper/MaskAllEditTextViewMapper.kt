/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.EditText
import androidx.annotation.VisibleForTesting
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.FixedLengthStringObfuscator
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

/**
 * A [WireframeMapper] implementation to map a [EditText] component in case the
 * [SessionReplayPrivacy.MASK_ALL] or [SessionReplayPrivacy.MASK_USER_INPUT]
 * rule was used in the configuration.
 * In this case the mapper will mask all the [EditText] values with a static mask: [***].
 */
internal class MaskAllEditTextViewMapper : EditTextViewMapper {

    internal constructor() : super(textViewMapper = TextViewMapper(FixedLengthStringObfuscator()))

    @VisibleForTesting
    internal constructor(
        textViewMapper: TextViewMapper,
        uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator,
        viewUtils: ViewUtils = ViewUtils,
        stringUtils: StringUtils = StringUtils
    ) : super(textViewMapper, uniqueIdentifierGenerator, viewUtils, stringUtils)
}
