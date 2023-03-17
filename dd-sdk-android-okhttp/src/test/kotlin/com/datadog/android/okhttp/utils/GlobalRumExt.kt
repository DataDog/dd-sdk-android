/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.utils

import com.datadog.android.rum.GlobalRum
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible

internal fun GlobalRum.reset() {
    this::class.memberFunctions
        .first { it.name == "reset" }
        .apply { this.isAccessible = true }
        .call(this::class.objectInstance)
}
