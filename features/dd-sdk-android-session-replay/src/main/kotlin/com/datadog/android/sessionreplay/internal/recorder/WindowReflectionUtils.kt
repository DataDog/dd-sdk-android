/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.annotation.SuppressLint
import android.view.View
import android.view.Window

@SuppressLint("PrivateApi") // intentional: accessing mWindow via reflection to get the Window from a decor view
internal object WindowReflectionUtils {

    private const val WINDOW_FIELD_NAME = "mWindow"

    fun getWindowFromDecorView(view: View): Window? {
        return try {
            var currentClass: Class<*>? = view.javaClass
            while (currentClass != null) {
                try {
                    @Suppress("UnsafeThirdPartyFunctionCall") // exceptions caught by outer try-catch
                    return currentClass.getDeclaredField(WINDOW_FIELD_NAME)
                        .also { it.isAccessible = true }
                        .get(view) as? Window
                } catch (_: NoSuchFieldException) {
                    currentClass = currentClass.superclass
                }
            }
            null
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
}
