/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log

import android.content.Context
import com.datadog.android.Datadog
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class LoggerBuilderTest {

    @Test
    fun `builder without custom settings uses defaults`() {
        val logger = Logger.Builder()
            .build()

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

        val logger = Logger.Builder()
            .setServiceName(serviceName)
            .build()

        assertThat(logger.serviceName).isEqualTo(serviceName)
    }

    @Test
    fun `builder can enable or disable timestamps`(@Forgery forge: Forge) {
        val timestampsEnabled = forge.aBool()

        val logger = Logger.Builder()
            .setTimestampsEnabled(timestampsEnabled)
            .build()

        assertThat(logger.timestampsEnabled).isEqualTo(timestampsEnabled)
    }

    @Test
    fun `builder can enable or disable user agent`(@Forgery forge: Forge) {
        val userAgentEnabled = forge.aBool()
        val systemUserAgent = forge.anAlphabeticalString()
        System.setProperty("http.agent", systemUserAgent)

        val logger = Logger.Builder()
            .setUserAgentEnabled(userAgentEnabled)
            .build()

        assertThat(logger.userAgentEnabled).isEqualTo(userAgentEnabled)
        assertThat(logger.userAgent).isEqualTo(systemUserAgent)
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
            .build()
        // TODO check broadcastReceiver is registered

        assertThat(logger.networkInfoEnabled).isEqualTo(networkInfoEnabled)
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun `set up Datadog`(forge: Forge) {
            val mockContext: Context = mock()
            whenever(mockContext.applicationContext) doReturn mockContext
            Datadog.initialize(mockContext, forge.anHexadecimalString())
        }
    }
}
