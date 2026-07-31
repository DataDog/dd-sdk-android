/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.annotation.SuppressLint
import android.view.View
import android.view.Window
import com.datadog.android.api.InternalLogger

@SuppressLint("PrivateApi") // intentional: accessing mWindow via reflection to get the Window from a decor view
internal object WindowReflectionUtils {

    internal const val FAILED_TO_RETRIEVE_WINDOW_ERROR_MESSAGE =
        "SR WindowReflectionUtils failed to retrieve the Window from the decor view via reflection"

    private const val WINDOW_FIELD_NAME = "mWindow"

    fun getWindowFromDecorView(view: View, internalLogger: InternalLogger): Window? {
        return try {
            var currentClass: Class<*>? = view.javaClass
            var lastFieldMiss: NoSuchFieldException? = null
            while (currentClass != null) {
                try {
                    @Suppress("UnsafeThirdPartyFunctionCall") // exceptions caught by outer try-catch
                    return currentClass.getDeclaredField(WINDOW_FIELD_NAME)
                        .also { it.isAccessible = true }
                        .get(view) as? Window
                } catch (e: NoSuchFieldException) {
                    lastFieldMiss = e
                    currentClass = currentClass.superclass
                }
            }
            // every real decorView declares mWindow somewhere in its hierarchy, so exhausting it
            // without finding the field means hidden-API enforcement is blocking access on this
            // OS version/OEM (it surfaces as NoSuchFieldException, indistinguishable from "absent").
            lastFieldMiss?.let { logReflectionFailure(internalLogger, it) }
            null
        } catch (e: ReflectiveOperationException) {
            logReflectionFailure(internalLogger, e)
            null
        } catch (e: SecurityException) {
            logReflectionFailure(internalLogger, e)
            null
        }
    }

    private fun logReflectionFailure(internalLogger: InternalLogger, throwable: Throwable) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            { FAILED_TO_RETRIEVE_WINDOW_ERROR_MESSAGE },
            throwable,
            true
        )
    }
}
