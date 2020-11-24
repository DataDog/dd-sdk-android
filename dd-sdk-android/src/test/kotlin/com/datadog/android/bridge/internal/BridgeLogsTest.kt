/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.bridge.internal

import android.util.Log
import com.datadog.android.bridge.DdLogs
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.MapForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class BridgeLogsTest {

    lateinit var testedLogs: DdLogs

    @Mock
    lateinit var mockLogHandler: LogHandler

    @StringForgery
    lateinit var fakeMessage: String

    @MapForgery(
        key = AdvancedForgery(string = [StringForgery()]),
        value = AdvancedForgery(string = [StringForgery(StringForgeryType.HEXADECIMAL)])
    )
    lateinit var fakeContext: Map<String, String>

    @BeforeEach
    fun `set up`() {
        testedLogs = BridgeLogs(mockLogHandler)
    }

    @Test
    fun `M forward debug log W debug()`() {
        // When
        testedLogs.debug(fakeMessage, fakeContext)

        // Then
        verify(mockLogHandler).handleLog(Log.DEBUG, fakeMessage, attributes = fakeContext)
    }

    @Test
    fun `M forward info log W info()`() {
        // When
        testedLogs.info(fakeMessage, fakeContext)

        // Then
        verify(mockLogHandler).handleLog(Log.INFO, fakeMessage, attributes = fakeContext)
    }

    @Test
    fun `M forward warning log W warn()`() {
        // When
        testedLogs.warn(fakeMessage, fakeContext)

        // Then
        verify(mockLogHandler).handleLog(Log.WARN, fakeMessage, attributes = fakeContext)
    }

    @Test
    fun `M forward error log W error()`() {
        // When
        testedLogs.error(fakeMessage, fakeContext)

        // Then
        verify(mockLogHandler).handleLog(Log.ERROR, fakeMessage, attributes = fakeContext)
    }
}
