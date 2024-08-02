/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.system

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import com.datadog.android.lint.InternalApi

/**
 * Wrapper around [Build.VERSION.SDK_INT] in order to simplify mocking in tests.
 *
 * FOR INTERNAL USAGE ONLY. THIS INTERFACE CONTENT MAY CHANGE WITHOUT NOTICE.
 */
@InternalApi
interface BuildSdkVersionProvider {

    /**
     * Value of [Build.VERSION.SDK_INT].
     */
    val version: Int

    companion object {

        /**
         * Default implementation which calls Build.VERSION under the hood.
         */
        val DEFAULT: BuildSdkVersionProvider = object : BuildSdkVersionProvider {

            @ChecksSdkIntAtLeast
            override val version: Int = Build.VERSION.SDK_INT
        }
    }
}
