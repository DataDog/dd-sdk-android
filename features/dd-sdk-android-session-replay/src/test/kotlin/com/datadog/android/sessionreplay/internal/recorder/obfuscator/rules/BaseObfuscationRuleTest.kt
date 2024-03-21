/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.obfuscator.rules

import android.widget.TextView
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.FixedLengthStringObfuscator
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.LegacyStringObfuscator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import org.mockito.Mock
import org.mockito.kotlin.whenever

internal abstract class BaseObfuscationRuleTest {

    @Forgery
    protected lateinit var fakeMappingContext: MappingContext

    @StringForgery
    protected lateinit var fakeTextValue: String

    @Mock
    protected lateinit var mockTextValueResolver: TextValueResolver

    @Mock
    protected lateinit var mockTextView: TextView

    @Mock
    protected lateinit var mockTextTypeResolver: TextTypeResolver

    @Mock
    protected lateinit var mockFixedLengthStringObfuscator: FixedLengthStringObfuscator

    @Mock
    protected lateinit var mockDefaultStringObfuscator: LegacyStringObfuscator

    @StringForgery
    protected lateinit var fakeFixedLengthMask: String

    @StringForgery
    protected lateinit var fakeDefaultMask: String

    protected fun setUp() {
        whenever(mockFixedLengthStringObfuscator.obfuscate(fakeTextValue))
            .thenReturn(fakeFixedLengthMask)
        whenever(mockDefaultStringObfuscator.obfuscate(fakeTextValue))
            .thenReturn(fakeDefaultMask)
        whenever(mockTextValueResolver.resolveTextValue(mockTextView)).thenReturn(fakeTextValue)
    }
}
