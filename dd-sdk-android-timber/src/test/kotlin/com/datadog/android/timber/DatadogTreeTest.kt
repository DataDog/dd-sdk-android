/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.timber

import android.content.Context
import com.datadog.android.Datadog
import com.datadog.android.log.Logger
import com.datadog.tools.unit.annotations.SystemOutStream
import com.datadog.tools.unit.extensions.SystemOutputExtension
import com.datadog.tools.unit.invokeMethod
import com.datadog.tools.unit.lastLine
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import timber.log.Timber

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(SystemOutputExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogTreeTest {

    lateinit var testedTree: Timber.Tree

    lateinit var fakeServiceName: String
    lateinit var fakeLoggerName: String
    lateinit var fakePackageName: String
    lateinit var fakeMessage: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeServiceName = forge.anAlphabeticalString()
        fakeLoggerName = forge.anAlphabeticalString()
        fakePackageName = forge.anAlphabeticalString()
        fakeMessage = forge.anAlphabeticalString()

        val mockContext: Context = mock()
        whenever(mockContext.applicationContext) doReturn mockContext
        whenever(mockContext.packageName) doReturn fakePackageName

        Datadog.initialize(mockContext, forge.anHexadecimalString())

        val builder = Logger.Builder()
            .setServiceName(fakeServiceName)
            .setLoggerName(fakeLoggerName)
            .setLogcatLogsEnabled(true)
            .setDatadogLogsEnabled(false)
            .setNetworkInfoEnabled(false)
        val logger = builder.build()

        testedTree = DatadogTree(logger)
        Timber.plant(testedTree)
    }

    @AfterEach
    fun `set up`() {
        Datadog.invokeMethod("stop")
    }

    @Test
    fun `tree logs message with verbose level`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        Timber.v(fakeMessage)

        verifyLogSideEffects("V", outputStream)
    }

    @Test
    fun `tree logs message with debug level`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        Timber.d(fakeMessage)

        verifyLogSideEffects("D", outputStream)
    }

    @Test
    fun `tree logs message with info level`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        Timber.i(fakeMessage)

        verifyLogSideEffects("I", outputStream)
    }

    @Test
    fun `tree logs message with warning level`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        Timber.w(fakeMessage)

        verifyLogSideEffects("W", outputStream)
    }

    @Test
    fun `tree logs message with error level`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        Timber.e(fakeMessage)

        verifyLogSideEffects("E", outputStream)
    }

    @Test
    fun `tree logs message with assert level`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        Timber.wtf(fakeMessage)

        verifyLogSideEffects("A", outputStream)
    }

    // region Internal

    private fun verifyLogSideEffects(
        logCatPrefix: String,
        outputStream: ByteArrayOutputStream
    ) {
        assertThat(outputStream.lastLine())
            .isEqualTo("$logCatPrefix/$fakeServiceName: $fakeMessage")
    }

    // endregion
}
