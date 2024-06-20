/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.logger

import com.datadog.android.api.InternalLogger
import com.datadog.android.trace.utils.verifyLog
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
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
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = Configurator::class)
internal class DatadogCoreTracerLoggerTest {

    private lateinit var testedLogger: DatadogCoreTracerLogger

    @StringForgery
    lateinit var fakeMessage: String

    private lateinit var expectedMessage: String

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakeLoggerName: String

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private val fakeArg1 = Object()
    private val fakeArg2 = Object()
    lateinit var fakeArgs: Array<Any>

    private lateinit var fakeThrowable: Throwable

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeArgs = forge.aList(size = forge.aSmallInt()) { Object() }.toTypedArray()
        fakeThrowable = forge.getForgery()
        expectedMessage = String.format(Locale.US, "%s: %s", fakeLoggerName, fakeMessage)
        testedLogger =
            DatadogCoreTracerLogger(fakeLoggerName, mockInternalLogger)
    }

    // region debug logs

    @Test
    fun `M send a debug log W debug`() {
        // When
        testedLogger.debug(fakeMessage)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.MAINTAINER,
            expectedMessage
        )
    }

    @Test
    fun `M send a debug log W debug { throwable }`() {
        // When
        testedLogger.debug(fakeMessage, fakeThrowable)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            expectedMessage,
            fakeThrowable
        )
    }

    @Test
    fun `M send a debug log W debug { arg1, arg2 }`(forge: Forge) {
        // Given
        val fakeMessageFormat = resolveFakeArgsMessageFormat(forge, fakeArg1, fakeArg2)
        val expectedMessage = resolveExpectedArgsMessage(fakeMessageFormat, fakeArg1, fakeArg2)

        // When
        testedLogger.debug(fakeMessageFormat, fakeArg1, fakeArg2)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.MAINTAINER,
            expectedMessage
        )
    }

    @Test
    fun `M send a debug log W debug { array of Args }`(forge: Forge) {
        // Given
        val fakeMessageFormat = resolveFakeArgsMessageFormat(forge, *fakeArgs)
        val expectedMessage = resolveExpectedArgsMessage(fakeMessageFormat, *fakeArgs)

        // When
        testedLogger.debug(fakeMessageFormat, *fakeArgs)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.MAINTAINER,
            expectedMessage
        )
    }

    // endregion

    // region info logs

    @Test
    fun `M send an info log W info`() {
        // When
        testedLogger.info(fakeMessage)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            expectedMessage
        )
    }

    @Test
    fun `M send an info log W info { throwable }`() {
        // When
        testedLogger.info(fakeMessage, fakeThrowable)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            expectedMessage,
            fakeThrowable
        )
    }

    @Test
    fun `M send an info log W debug { arg1, arg2 }`(forge: Forge) {
        // Given
        val fakeMessageFormat = resolveFakeArgsMessageFormat(forge, fakeArg1, fakeArg2)
        val expectedMessage = resolveExpectedArgsMessage(fakeMessageFormat, fakeArg1, fakeArg2)

        // When
        testedLogger.info(fakeMessageFormat, fakeArg1, fakeArg2)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            expectedMessage
        )
    }

    @Test
    fun `M send an info log W debug { array of Args }`(forge: Forge) {
        // Given
        val fakeMessageFormat = resolveFakeArgsMessageFormat(forge, *fakeArgs)
        val expectedMessage = resolveExpectedArgsMessage(fakeMessageFormat, *fakeArgs)

        // When
        testedLogger.info(fakeMessageFormat, *fakeArgs)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            expectedMessage
        )
    }

    // endregion

    // region warn logs

    @Test
    fun `M send an warn log W warn`() {
        // When
        testedLogger.warn(fakeMessage)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            expectedMessage
        )
    }

    @Test
    fun `M send a warn log W warn { throwable }`() {
        // When
        testedLogger.warn(fakeMessage, fakeThrowable)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            expectedMessage,
            fakeThrowable
        )
    }

    @Test
    fun `M send a warn log W warn { arg1, arg2 }`(forge: Forge) {
        // Given
        val fakeMessageFormat = resolveFakeArgsMessageFormat(forge, fakeArg1, fakeArg2)
        val expectedMessage = resolveExpectedArgsMessage(fakeMessageFormat, fakeArg1, fakeArg2)

        // When
        testedLogger.warn(fakeMessageFormat, fakeArg1, fakeArg2)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            expectedMessage
        )
    }

    @Test
    fun `M send a warn log W warn { array of Args }`(forge: Forge) {
        // Given
        val fakeMessageFormat = resolveFakeArgsMessageFormat(forge, *fakeArgs)
        val expectedMessage = resolveExpectedArgsMessage(fakeMessageFormat, *fakeArgs)

        // When
        testedLogger.warn(fakeMessageFormat, *fakeArgs)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            expectedMessage
        )
    }

    // endregion

    // region error logs

    @Test
    fun `M send an error log W error`() {
        // When
        testedLogger.error(fakeMessage)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            expectedMessage
        )
    }

    @Test
    fun `M send an error log W error { throwable }`() {
        // When
        testedLogger.error(fakeMessage, fakeThrowable)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            expectedMessage,
            fakeThrowable
        )
    }

    @Test
    fun `M send an error log W error { arg1, arg2 }`(forge: Forge) {
        // Given
        val fakeMessageFormat = resolveFakeArgsMessageFormat(forge, fakeArg1, fakeArg2)
        val expectedMessage = resolveExpectedArgsMessage(fakeMessageFormat, fakeArg1, fakeArg2)

        // When
        testedLogger.error(fakeMessageFormat, fakeArg1, fakeArg2)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            expectedMessage
        )
    }

    @Test
    fun `M send an error log W error { array of Args }`(forge: Forge) {
        // Given
        val fakeMessageFormat = resolveFakeArgsMessageFormat(forge, *fakeArgs)
        val expectedMessage = resolveExpectedArgsMessage(fakeMessageFormat, *fakeArgs)

        // When
        testedLogger.error(fakeMessageFormat, *fakeArgs)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            expectedMessage
        )
    }

    // endregion

    // region Internal

    private fun resolveFakeArgsMessageFormat(forge: Forge, vararg args: Any): String {
        return args.joinToString(separator = " ") { forge.anAlphabeticalString() + " {}" }
    }

    private fun resolveExpectedArgsMessage(format: String, vararg args: Any): String {
        val sanitizedFormat = format.replace("{}", "%s")
        return String.format(Locale.US, "$fakeLoggerName: $sanitizedFormat", *args)
    }

    // endregion
}
