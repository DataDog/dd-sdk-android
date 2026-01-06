/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.annotation.SuppressLint
import android.view.View
import android.view.inspector.WindowInspector
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.system.BuildSdkVersionProvider
import java.lang.NullPointerException
import java.lang.reflect.Field

@SuppressLint("PrivateApi")
internal object WindowInspector {

    private const val FAILED_TO_RETRIEVE_DECOR_VIEWS_ERROR_MESSAGE =
        "SR WindowInspector failed to retrieve the decor views"

    @Suppress("SwallowedException", "TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall")
    private val GLOBAL_WM_CLASS by lazy {
        return@lazy Class.forName("android.view.WindowManagerGlobal")
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall")
    private val GLOBAL_WM_INSTANCE by lazy {
        return@lazy GLOBAL_WM_CLASS?.getMethod("getInstance")?.invoke(null)
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall")
    private val VIEWS_FIELD by lazy {
        return@lazy GLOBAL_WM_CLASS?.getDeclaredField("mViews")
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    fun getGlobalWindowViews(
        internalLogger: InternalLogger,
        buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
    ): List<View> {
        return try {
            if (buildSdkVersionProvider.isAtLeastQ) {
                // this can throw also maybe but we don't know what type exactly. In order to be
                // safe as this function is being called on the MainThread every time we want to
                // take a snapshot we will catch all the `Throwable`s in a single catch block.
                WindowInspector.getGlobalWindowViews()
            } else {
                getGlobalWindowViewsLegacy(GLOBAL_WM_INSTANCE, VIEWS_FIELD)
            }
        } catch (e: Throwable) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.TELEMETRY,
                { FAILED_TO_RETRIEVE_DECOR_VIEWS_ERROR_MESSAGE },
                e,
                true
            )
            emptyList()
        }
    }

    @Suppress("SwallowedException", "UnsafeThirdPartyFunctionCall")
    @Throws(
        NoSuchFieldException::class,
        NullPointerException::class,
        SecurityException::class,
        LinkageError::class,
        ClassNotFoundException::class,
        ExceptionInInitializerError::class
    )
    internal fun getGlobalWindowViewsLegacy(
        globalWmInstance: Any?,
        viewsField: Field?
    ): List<View> {
        if (globalWmInstance != null && viewsField != null) {
            viewsField.isAccessible = true
            val decorViews = when (val views: Any? = viewsField.get(globalWmInstance)) {
                is List<*> -> {
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
        return emptyList()
    }
}
