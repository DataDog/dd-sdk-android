/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.extension

import android.view.Choreographer
import com.datadog.tools.unit.setStaticValue
import org.mockito.kotlin.mock

fun mockChoreographerInstance(mock: Choreographer = mock()) {
    Choreographer::class.java.setStaticValue(
        "sThreadInstance",
        object : ThreadLocal<Choreographer>() {
            override fun initialValue(): Choreographer {
                return mock
            }
        }
    )
}
