/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Application
import android.content.pm.ApplicationInfo

/**
 * Enables the RUM Debug Widget for the given [application].
 *
 * By default, the widget is only enabled in debug builds (when the application is debuggable).
 * To enable it in release builds (e.g., for local testing), set [allowInRelease] to `true` and
 * add the `dd-sdk-android-rum-debug-widget` module as `implementation` (not `debugImplementation`)
 * in your `build.gradle` file.
 *
 * @param application The application where to enable the RUM Debug Widget.
 * @param allowInRelease Set to `true` to enable the widget in release builds. Defaults to `false`
 * to prevent accidental exposure in production.
 * @return The same [RumConfiguration.Builder] instance.
 */
fun RumConfiguration.Builder.enableRumDebugWidget(
    application: Application,
    allowInRelease: Boolean = false
): RumConfiguration.Builder = apply {
    val isApplicationDebuggable = (application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!isApplicationDebuggable && !allowInRelease) return@apply

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
