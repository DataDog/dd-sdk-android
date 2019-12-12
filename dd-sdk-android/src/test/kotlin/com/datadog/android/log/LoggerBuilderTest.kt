/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log

import android.content.Context
import com.datadog.android.Datadog
import com.datadog.android.log.internal.net.NetworkInfoProvider
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings()
internal class LoggerBuilderTest {

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @Mock
    lateinit var mockContext: Context

    lateinit var packageName: String

    @BeforeEach
    fun `set up Datadog`(forge: Forge) {
        packageName = forge.anAlphabeticalString()
        val mockContext: Context = mock()
        whenever(mockContext.applicationContext) doReturn mockContext
        whenever(mockContext.packageName) doReturn packageName

        Datadog.initialize(mockContext, forge.anHexadecimalString())
    }

    @AfterEach
    fun `tear down Datadog`() {
        Datadog.stop()
    }

    @Test
    fun `builder without custom settings uses defaults`() {
        val logger = Logger.Builder()
            .build()

        assertThat(logger.serviceName).isEqualTo(Logger.DEFAULT_SERVICE_NAME)
        assertThat(logger.datadogLogsEnabled).isTrue()
        assertThat(logger.logcatLogsEnabled).isFalse()
        assertThat(logger.networkInfoProvider).isNull()
        assertThat(logger.loggerName).isEqualTo(packageName)
    }

    @Test
    fun `builder can set a ServiceName`(@Forgery forge: Forge) {
        val serviceName = forge.anAlphabeticalString()

        val logger = Logger.Builder()
            .setServiceName(serviceName)
            .build()

        assertThat(logger.serviceName).isEqualTo(serviceName)
    }

    @Test
    fun `builder can enable or disable datadog logs`(@Forgery forge: Forge) {
        val datadogLogsEnabled = forge.aBool()

        val logger = Logger.Builder()
            .setDatadogLogsEnabled(datadogLogsEnabled)
            .build()

        assertThat(logger.datadogLogsEnabled).isEqualTo(datadogLogsEnabled)
    }

    @Test
    fun `builder can enable or disable logcat logs`(@Forgery forge: Forge) {
        val logcatLogsEnabled = forge.aBool()

        val logger = Logger.Builder()
            .setLogcatLogsEnabled(logcatLogsEnabled)
            .build()

        assertThat(logger.logcatLogsEnabled).isEqualTo(logcatLogsEnabled)
    }

    @Test
    fun `builder can enable network info`(@Forgery forge: Forge) {
        val networkInfoEnabled = forge.aBool()

        val logger = Logger.Builder()
            .setNetworkInfoEnabled(networkInfoEnabled)
            .withNetworkInfoProvider(mockNetworkInfoProvider)
            .build()

        if (networkInfoEnabled) {
            assertThat(logger.networkInfoProvider).isEqualTo(mockNetworkInfoProvider)
        } else {
            assertThat(logger.networkInfoProvider).isNull()
        }
    }

    @Test
    fun `buider can set the logger name`(@Forgery forge: Forge) {
        val loggerName = forge.anAlphabeticalString()

        val logger = Logger.Builder()
            .setLoggerName(loggerName)
            .build()

        assertThat(logger.loggerName).isEqualTo(loggerName)
    }
}
