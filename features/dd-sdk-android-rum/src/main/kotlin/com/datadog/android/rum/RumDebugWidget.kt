/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Application
import android.content.pm.ApplicationInfo

/**
 * Enables the RUM Debug Widget for the given [application] if the [enabled] flag is set to `true`.
 *
 * @param application The application where to enable the RUM Debug Widget.
 * @param enabled `true` to enable the RUM Debug Widget, `false` otherwise. By default, it is enabled only if the application is debuggable.
 * @return The same [RumConfiguration.Builder] instance.
 */
fun RumConfiguration.Builder.enableRumDebugWidget(
    application: Application,
    enabled: Boolean = application.isDebuggable()
): RumConfiguration.Builder = apply {
    if (!enabled) return@apply

    @Suppress("UnsafeThirdPartyFunctionCall")
    runCatching {
        val clazz = Class.forName("com.datadog.android.insights.internal.RumDebugWidget")
        val method = clazz.getMethod(
            "enable",
            Application::class.java,
            RumConfiguration.Builder::class.java
        )
        method.invoke(null, application, this)
    }
}

/**
 * Tells whether the application is debuggable or not.
 *
 * @return `true` if the application is debuggable, `false` otherwise.
 */
fun Application.isDebuggable(): Boolean {
    return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
