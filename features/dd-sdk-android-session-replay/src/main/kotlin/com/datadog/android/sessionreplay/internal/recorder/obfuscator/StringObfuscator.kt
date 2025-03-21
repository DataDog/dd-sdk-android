/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.obfuscator

import android.os.Build
import com.datadog.android.lint.InternalApi

/**
 * Obfuscates string of text in session replay.
 *
 * DO NOT USE this class or its methods if you are not working on the internals of the Datadog SDK
 * or one of the cross platform frameworks.
 */
@InternalApi
interface StringObfuscator {

    /**
     * Obfuscates string of text in session replay.
     *
     * For Datadog internal use only.
     */
    @InternalApi
    fun obfuscate(stringValue: String): String

    companion object {
        internal const val CHARACTER_MASK = 'x'

        /**
         * Gets the instance of [StringObfuscator].
         *
         * For Datadog internal use only.
         */
        @InternalApi
        fun getStringObfuscator(): StringObfuscator {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                AndroidNStringObfuscator()
            } else {
                LegacyStringObfuscator()
            }
        }
    }
}
