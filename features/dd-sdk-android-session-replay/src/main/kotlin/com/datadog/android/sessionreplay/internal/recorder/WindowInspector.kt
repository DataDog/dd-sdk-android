/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.view.inspector.WindowInspector
import java.lang.reflect.Field

@SuppressLint("PrivateApi")
internal object WindowInspector {

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private val GLOBAL_WM_CLASS by lazy {
        try {
            return@lazy Class.forName("android.view.WindowManagerGlobal")
        } catch (e: Throwable) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
            // sdkLogger.errorWithTelemetry("WindowInspector failed to retrieve the decor views", e)
        }
        return@lazy null
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private val GLOBAL_WM_INSTANCE by lazy {
        try {
            return@lazy GLOBAL_WM_CLASS?.getMethod("getInstance")?.invoke(null)
        } catch (e: Throwable) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
            // sdkLogger.errorWithTelemetry("WindowInspector failed to retrieve the decor views", e)
        }
        return@lazy null
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private val VIEWS_FIELD by lazy {
        try {
            return@lazy GLOBAL_WM_CLASS?.getDeclaredField("mViews")
        } catch (e: Throwable) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
            // sdkLogger.errorWithTelemetry("WindowInspector failed to retrieve the decor views", e)
        }
        return@lazy null
    }

    fun getGlobalWindowViews(): List<View> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WindowInspector.getGlobalWindowViews()
        } else {
            getGlobalWindowViewsLegacy(GLOBAL_WM_INSTANCE, VIEWS_FIELD)
        }
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    internal fun getGlobalWindowViewsLegacy(globalWmInstance: Any?, viewsField: Field?): List<View> {
        try {
            if (globalWmInstance != null && viewsField != null) {
                viewsField.isAccessible = true
                val decorViews = when (val views: Any? = viewsField.get(globalWmInstance)) {
                    is List<*> -> {
                        @Suppress("UnsafeThirdPartyFunctionCall") // mapNotNull is a safe call
                        views.mapNotNull { it as? View }
                    }
                    is Array<*> -> {
                        views.toList().mapNotNull { it as? View }
                    }
                    else -> {
                        emptyList()
                    }
                }
                return decorViews
            }
        } catch (e: Throwable) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
            // sdkLogger.errorWithTelemetry("WindowInspector failed to retrieve the decor views", e)
        }
        return emptyList()
    }
}
