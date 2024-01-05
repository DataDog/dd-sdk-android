/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.core

import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.datadog.android.core.allowThreadDiskReads
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("TestFunctionName")
@MediumTest
@RunWith(AndroidJUnit4::class)
class StrictModeTest {

    @After
    fun tearDown() {
        // Restore default StrictMode policy
        StrictMode.setThreadPolicy(
            ThreadPolicy.Builder()
                .permitAll()
                .penaltyLog()
                .build()
        )
    }

    @Test
    fun M_disable_disk_read_checks_W_allowThreadDiskReads() {
        // Given
        StrictMode.setThreadPolicy(
            ThreadPolicy.Builder()
                .detectAll()
                .penaltyDeath()
                .build()
        )

        // When
        var error: RuntimeException? = null
        val sdcardExists = try {
            allowThreadDiskReads {
                val sdcard = File("/sdcard")
                sdcard.exists()
            }
        } catch (e: RuntimeException) {
            error = e
            false
        }

        // Then
        assertThat(error).isNull()
        assertThat(sdcardExists).isTrue()
    }

    @Test
    fun M_disable_disk_read_checks_temporarily_W_allowThreadDiskReads() {
        // Given
        StrictMode.setThreadPolicy(
            ThreadPolicy.Builder()
                .detectAll()
                .penaltyDeath()
                .build()
        )

        // When
        var error: RuntimeException? = null
        val sdcardExists = try {
            allowThreadDiskReads {
                // do nothing
            }
            val sdcard = File("/sdcard")
            sdcard.exists()
        } catch (e: RuntimeException) {
            error = e
            null
        }

        // Then
        assertThat(error).isNotNull()
        assertThat(sdcardExists).isNull()
    }
}
