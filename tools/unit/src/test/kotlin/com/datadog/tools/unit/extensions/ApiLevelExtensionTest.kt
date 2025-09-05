/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.extensions

import android.os.Build
import com.datadog.tools.unit.annotations.TestTargetApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ApiLevelExtension::class)
class ApiLevelExtensionTest {

    @Test
    fun `default API is 0`() {
        assertThat(Build.VERSION.SDK_INT)
            .isEqualTo(0)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `sets API to M`() {
        assertThat(Build.VERSION.SDK_INT)
            .isEqualTo(Build.VERSION_CODES.M)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.N)
    fun `sets API to Nougat`() {
        assertThat(Build.VERSION.SDK_INT)
            .isEqualTo(Build.VERSION_CODES.N)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `sets API to Oreo`() {
        assertThat(Build.VERSION.SDK_INT)
            .isEqualTo(Build.VERSION_CODES.O)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.P)
    fun `sets API to P`() {
        assertThat(Build.VERSION.SDK_INT)
            .isEqualTo(Build.VERSION_CODES.P)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.Q)
    fun `sets API to Q`() {
        assertThat(Build.VERSION.SDK_INT)
            .isEqualTo(Build.VERSION_CODES.Q)
    }
}
