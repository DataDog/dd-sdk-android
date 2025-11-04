/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.insights

import android.app.Application
import com.datadog.android.rum.RumConfiguration

/**
 * Safe to call in any build. In debuggable apps with the debug widget on the classpath,
 * it enables the overlay; otherwise it no-ops.
 */
fun RumConfiguration.Builder.enableDebugWidget(
    application: Application,
    enabled: Boolean = application.isDebuggable()
): RumConfiguration.Builder = apply {
    if (!enabled) return@apply

    runCatching {
        val clazz = Class.forName("com.datadog.android.insights.RumDebugWidget")
        val method = clazz.getMethod(
            "enable",
            Application::class.java,
            RumConfiguration.Builder::class.java
        )
        method.invoke(null, application, this)
    }
}

private fun Application.isDebuggable(): Boolean {
    val flags = applicationInfo.flags
    return (flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
