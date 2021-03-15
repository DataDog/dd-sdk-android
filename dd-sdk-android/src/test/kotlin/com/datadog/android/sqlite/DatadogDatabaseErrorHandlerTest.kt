/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rx

import android.database.DatabaseErrorHandler
import android.database.DefaultDatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.sqlite.DatadogDatabaseErrorHandler
import com.datadog.tools.unit.getStaticValue
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatadogDatabaseErrorHandlerTest {

    lateinit var testedHandler: DatadogDatabaseErrorHandler

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockDefaultHandler: DatabaseErrorHandler

    @Mock
    lateinit var mockSqliteDatabase: SQLiteDatabase

    @RegexForgery("[a-z]/[a-z]")
    lateinit var fakeDbPath: String

    @IntForgery
    var fakeDbVersion: Int = 0

    @BeforeEach
    fun `set up`() {
        GlobalRum.registerIfAbsent(mockRumMonitor)
        testedHandler = DatadogDatabaseErrorHandler(mockDefaultHandler)
        whenever(mockSqliteDatabase.path).thenReturn(fakeDbPath)
        whenever(mockSqliteDatabase.version).thenReturn(fakeDbVersion)
    }

    @AfterEach
    fun `tear down`() {
        val isRegistered: AtomicBoolean = GlobalRum::class.java.getStaticValue("isRegistered")
        isRegistered.set(false)
    }

    @Test
    fun `M send an error event W intercepting a DB corruption`(forge: Forge) {
        // WHEN
        testedHandler.onCorruption(mockSqliteDatabase)

        // THEN
        val argumentCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockRumMonitor).addError(
            eq(
                String.format(
                    Locale.US,
                    DatadogDatabaseErrorHandler.DATABASE_CORRUPTION_ERROR_MESSAGE,
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

    @Test
    fun `M call the defaultHandler W intercepting a DB corruption`(forge: Forge) {
        // WHEN
        testedHandler.onCorruption(mockSqliteDatabase)

        // THEN
        verify(mockDefaultHandler).onCorruption(mockSqliteDatabase)
        verifyZeroInteractions(mockDefaultHandler)
    }

    @Test
    fun `M intialise with DefaultDatabaseHandler instance W none provided`(forge: Forge) {
        // WHEN
        testedHandler = DatadogDatabaseErrorHandler()

        // THEN
        assertThat(testedHandler.defaultErrorHandler)
            .isInstanceOf(DefaultDatabaseErrorHandler::class.java)
    }
}
