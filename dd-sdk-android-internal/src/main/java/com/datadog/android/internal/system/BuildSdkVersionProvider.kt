/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.system

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Wrapper around [android.os.Build.VERSION.SDK_INT] in order to simplify mocking in tests.
 */
@Suppress("UndocumentedPublicProperty")
interface BuildSdkVersionProvider {

    /**
     * Value of [android.os.Build.VERSION.SDK_INT].
     */
    val version: Int

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N)
    val isAtLeastN: Boolean

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    val isAtLeastO: Boolean

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    val isAtLeastP: Boolean

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    val isAtLeastQ: Boolean

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    val isAtLeastR: Boolean

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    val isAtLeastS: Boolean

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val isAtLeastTiramisu: Boolean

    companion object {

        /**
         * Default implementation which calls Build.VERSION under the hood.
         */
        val DEFAULT: BuildSdkVersionProvider = object : BuildSdkVersionProvider {

            @ChecksSdkIntAtLeast
            override val version: Int = Build.VERSION.SDK_INT

            @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N)
            override val isAtLeastN: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

            @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
            override val isAtLeastO: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

            @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
            override val isAtLeastP: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

            @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
            override val isAtLeastQ: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

            @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
            override val isAtLeastR: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

            @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
            override val isAtLeastS: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
            override val isAtLeastTiramisu: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        }
    }
}
