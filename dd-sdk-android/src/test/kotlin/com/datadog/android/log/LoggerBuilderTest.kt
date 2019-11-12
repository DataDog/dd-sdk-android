/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log

import android.content.Context
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class LoggerBuilderTest {

    @Mock
    lateinit var mockContext: Context

    @BeforeEach
    fun `set up mock`() {
        whenever(mockContext.applicationContext) doReturn mockContext
    }

    @Test
    fun `builder requires a ClientToken`(@Forgery forge: Forge) {
        val token = forge.anHexadecimalString()

        val logger = Logger.Builder(mockContext, token).build()

        assertThat(logger.clientToken).isEqualTo(token)
    }

    @Test
    fun `builder without custom settings uses defaults`(@Forgery forge: Forge) {
        val logger = Logger.Builder(mockContext, forge.anHexadecimalString()).build()

        assertThat(logger.serviceName).isEqualTo(Logger.DEFAULT_SERVICE_NAME)
        assertThat(logger.timestampsEnabled).isTrue()
        assertThat(logger.userAgentEnabled).isTrue()
        assertThat(logger.datadogLogsEnabled).isTrue()
        assertThat(logger.logcatLogsEnabled).isFalse()
        assertThat(logger.networkInfoEnabled).isFalse()
    }

    @Test
    fun `builder can set a ServiceName`(@Forgery forge: Forge) {
        val serviceName = forge.anAlphabeticalString()

        val logger = Logger.Builder(mockContext, forge.anHexadecimalString())
            .setServiceName(serviceName)
            .build()

        assertThat(logger.serviceName).isEqualTo(serviceName)
    }

    @Test
    fun `builder can enable or disable timestamps`(@Forgery forge: Forge) {
        val timestampsEnabled = forge.aBool()

        val logger = Logger.Builder(mockContext, forge.anHexadecimalString())
            .setTimestampsEnabled(timestampsEnabled)
            .build()

        assertThat(logger.timestampsEnabled).isEqualTo(timestampsEnabled)
    }

    @Test
    fun `builder can enable or disable user agent`(@Forgery forge: Forge) {
        val userAgentEnabled = forge.aBool()

        val logger = Logger.Builder(mockContext, forge.anHexadecimalString())
            .setUserAgentEnabled(userAgentEnabled)
            .build()

        assertThat(logger.userAgentEnabled).isEqualTo(userAgentEnabled)
    }

    @Test
    fun `builder can enable or disable datadog logs`(@Forgery forge: Forge) {
        val datadogLogsEnabled = forge.aBool()

        val logger = Logger.Builder(mockContext, forge.anHexadecimalString())
            .setDatadogLogsEnabled(datadogLogsEnabled)
            .build()

        assertThat(logger.datadogLogsEnabled).isEqualTo(datadogLogsEnabled)
    }

    @Test
    fun `builder can enable or disable logcat logs`(@Forgery forge: Forge) {
        val logcatLogsEnabled = forge.aBool()

        val logger = Logger.Builder(mockContext, forge.anHexadecimalString())
            .setLogcatLogsEnabled(logcatLogsEnabled)
            .build()

        assertThat(logger.logcatLogsEnabled).isEqualTo(logcatLogsEnabled)
    }

    @Test
    fun `builder can enable network infos`(@Forgery forge: Forge, @Mock context: Context) {
        val networkInfoEnabled = forge.aBool()

        val logger = Logger.Builder(mockContext, forge.anHexadecimalString())
            .setNetworkInfoEnabled(networkInfoEnabled)
            .build()
        // TODO check broadcastReceiver is registered

        assertThat(logger.networkInfoEnabled).isEqualTo(networkInfoEnabled)
    }
}
