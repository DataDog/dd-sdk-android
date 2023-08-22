/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core

import android.os.Build
import android.util.Log

internal class LogcatLogHandler(
    internal val tag: String,
    internal val predicate: (level: Int) -> Boolean = { true }
) {

    fun canLog(level: Int): Boolean {
        return predicate(level)
    }

    fun log(level: Int, message: String, throwable: Throwable?) {
        if (!predicate.invoke(level)) return

        val tag = resolveTag()
        Log.println(level, tag, message)
        if (throwable != null) {
            Log.println(
                level,
                tag,
                Log.getStackTraceString(throwable)
            )
        }
    }

    private fun resolveTag(): String {
        return if (tag.length >= MAX_TAG_LENGTH &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.N
        ) {
            @Suppress("UnsafeThirdPartyFunctionCall")
            // substring can't throw IndexOutOfBounds, we checked the length
            tag.substring(0, MAX_TAG_LENGTH)
        } else {
            tag
        }
    }

    companion object {
        private const val MAX_TAG_LENGTH = 23
    }
}
