/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rx

import androidx.sqlite.db.SupportSQLiteDatabase
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.sqldelight.DatadogSqliteCallback
import com.datadog.android.v2.api.SdkCore
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatadogSqliteCallbackTest {

    lateinit var testedSqliteCallback: DatadogSqliteCallback

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockSqliteDatabase: SupportSQLiteDatabase

    @StringForgery(regex = "[a-z]/[a-z]")
    lateinit var fakeDbPath: String

    @IntForgery
    var fakeDbVersion: Int = 0

    @BeforeEach
    fun `set up`() {
        GlobalRum.registerIfAbsent(mockSdkCore, mockRumMonitor)
        testedSqliteCallback = DatadogSqliteCallback(mock(), mockSdkCore)
        whenever(mockSqliteDatabase.path).thenReturn(fakeDbPath)
        whenever(mockSqliteDatabase.version).thenReturn(fakeDbVersion)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum::class.java.getDeclaredMethod("reset").apply {
            isAccessible = true
            invoke(null)
        }
    }

    @Test
    fun `M send an error event W intercepting a DB corruption`() {
        // WHEN
        testedSqliteCallback.onCorruption(mockSqliteDatabase)

        // THEN
        val argumentCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockRumMonitor).addError(
            eq(
                String.format(
                    Locale.US,
                    DatadogSqliteCallback.DATABASE_CORRUPTION_ERROR_MESSAGE,
                    fakeDbPath
                )
            ),
            eq(RumErrorSource.SOURCE),
            eq(null),
            argumentCaptor.capture()
        )
        assertThat(argumentCaptor.allValues).containsExactly(
            mapOf(
                RumAttributes.ERROR_DATABASE_PATH to fakeDbPath,
                RumAttributes.ERROR_DATABASE_VERSION to fakeDbVersion
            )
        )
    }
}
