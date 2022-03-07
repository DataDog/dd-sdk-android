/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.utils

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.SecurityConfig

/**
 * Helper method to access security config while it's still private.
 * @param securityConfig the [SecurityConfig] to use
 */
fun Configuration.Builder.setSecurityConfig(
    securityConfig: SecurityConfig
): Configuration.Builder {
    val method = this.javaClass.declaredMethods.find { it.name == "setSecurityConfig" }
    method?.isAccessible = true
    return method?.invoke(this, securityConfig) as? Configuration.Builder ?: this
}
