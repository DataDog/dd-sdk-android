/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.obfuscator

import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper

internal class FixedLengthStringObfuscator : StringObfuscator {
    override fun obfuscate(stringValue: String): String {
        return TextViewMapper.STATIC_MASK
    }
}
