/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sqlite

import android.database.DatabaseErrorHandler
import android.database.DefaultDatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.utils.config.DatadogSingletonTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogDatabaseErrorHandlerTest {

    lateinit var testedHandler: DatadogDatabaseErrorHandler

    @Mock
    lateinit var mockDefaultHandler: DatabaseErrorHandler

    @Mock
    lateinit var mockSqliteDatabase: SQLiteDatabase

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @StringForgery(regex = "[a-z]/[a-z]")
    lateinit var fakeDbPath: String

    @IntForgery
    var fakeDbVersion: Int = 0

    @BeforeEach
    fun `set up`() {
        testedHandler = DatadogDatabaseErrorHandler(defaultErrorHandler = mockDefaultHandler)
        whenever(mockSqliteDatabase.path).thenReturn(fakeDbPath)
        whenever(mockSqliteDatabase.version).thenReturn(fakeDbVersion)
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, datadog.mockInstance)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRumMonitor.clear()
    }

    @Test
    fun `M send an error event W intercepting a DB corruption`() {
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
    fun `M call the defaultHandler W intercepting a DB corruption`() {
        // WHEN
        testedHandler.onCorruption(mockSqliteDatabase)

        // THEN
        verify(mockDefaultHandler).onCorruption(mockSqliteDatabase)
        verifyNoMoreInteractions(mockDefaultHandler)
    }

    @Test
    fun `M initialise with DefaultDatabaseHandler instance W none provided`() {
        // WHEN
        testedHandler = DatadogDatabaseErrorHandler()

        // THEN
        assertThat(testedHandler.defaultErrorHandler)
            .isInstanceOf(DefaultDatabaseErrorHandler::class.java)
    }

    companion object {
        val datadog = DatadogSingletonTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(datadog)
        }
    }
}
