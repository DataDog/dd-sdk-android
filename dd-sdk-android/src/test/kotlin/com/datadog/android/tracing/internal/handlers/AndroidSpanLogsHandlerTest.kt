/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.handlers

import android.util.Log
import com.datadog.android.log.Logger
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import datadog.opentracing.DDSpan
import datadog.trace.api.DDTags
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.log.Fields
import java.util.concurrent.TimeUnit
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
@ForgeConfiguration(Configurator::class)
internal class AndroidSpanLogsHandlerTest {

    lateinit var underTest: AndroidSpanLogsHandler
    @Mock
    lateinit var mockedLogger: Logger
    @Mock
    lateinit var mockedSpan: DDSpan

    @BeforeEach
    fun `set up`() {
        underTest = AndroidSpanLogsHandler(
            mockedLogger
        )
    }

    @Test
    fun `when logging will delegate to the wrapped logger`(forge: Forge) {
        // given
        val logMessage = forge.anAlphabeticalString()

        // when
        underTest.log(logMessage, mockedSpan)

        // then
        verify(mockedLogger).v(logMessage)
        verifyZeroInteractions(mockedSpan)
    }

    @Test
    fun `when logging with timestamp will delegate to the wrapped logger`(forge: Forge) {
        // given
        val logMessage = forge.anAlphabeticalString()
        val logTimestampInMicroSeconds = forge.aLong()
        val logTimestampInMillis = TimeUnit.MICROSECONDS.toMillis(logTimestampInMicroSeconds)

        // when
        underTest.log(logTimestampInMicroSeconds, logMessage, mockedSpan)

        // then
        verify(mockedLogger).internalLog(
            eq(Log.VERBOSE),
            eq(logMessage),
            eq(null),
            eq(emptyMap()),
            eq(logTimestampInMillis)
        )
        verifyZeroInteractions(mockedSpan)
    }

    @Test
    fun `when logging fields will delegate to the wrapped logger`(forge: Forge) {
        // given
        val logFields = forge.aMap {
            forge.anAlphabeticalString() to forge.anAlphabeticalString()
        }

        // when
        underTest.log(logFields, mockedSpan)

        // then
        verify(mockedLogger).v(AndroidSpanLogsHandler.TRACE_LOG_MESSAGE, null, logFields)
        verifyZeroInteractions(mockedSpan)
    }

    @Test
    fun `when logging fields with timestamp will delegate to the wrapped logger`(forge: Forge) {
        // given
        val logFields = forge.aMap {
            forge.anAlphabeticalString() to forge.anAlphabeticalString()
        }
        val logTimestampInMicroSeconds = forge.aLong()
        val logTimestampInMillis = TimeUnit.MICROSECONDS.toMillis(logTimestampInMicroSeconds)
        // when
        underTest.log(logTimestampInMicroSeconds, logFields, mockedSpan)

        // then
        verify(mockedLogger).internalLog(
            Log.VERBOSE,
            AndroidSpanLogsHandler.TRACE_LOG_MESSAGE,
            null,
            logFields,
            logTimestampInMillis
        )
        verifyZeroInteractions(mockedSpan)
    }

    @Test
    fun `when logging fields with error and timestamp will correctly handle the error`(
        forge: Forge
    ) {
        // given
        val throwableErrorMessage = forge.anAlphabeticalString()
        val differentErrorMessage = throwableErrorMessage + forge.anAlphabeticalString(size = 2)
        val throwable = Throwable(throwableErrorMessage)
        val logFields = mapOf(
            Fields.ERROR_OBJECT to throwable,
            Fields.MESSAGE to differentErrorMessage
        )
        val logTimestampInMicroSeconds = forge.aLong()
        // when
        underTest.log(logTimestampInMicroSeconds, logFields, mockedSpan)

        // then
        verify(mockedSpan).setErrorMeta(throwable)
        verifyNoMoreInteractions(mockedSpan)
    }

    @Test
    fun `when logging fields without error but error message and timestamp will set the message`(
        forge: Forge
    ) {
        // given
        val throwableErrorMessage = forge.anAlphabeticalString()
        val logFields = mapOf(
            Fields.MESSAGE to throwableErrorMessage
        )
        val logTimestampInMicroSeconds = forge.aLong()
        // when
        underTest.log(logTimestampInMicroSeconds, logFields, mockedSpan)

        // then
        verify(mockedSpan).setTag(DDTags.ERROR_MSG, throwableErrorMessage)
        verifyNoMoreInteractions(mockedSpan)
    }

    @Test
    fun `when logging fields with error will correctly handle the error`(
        forge: Forge
    ) {
        // given
        val throwableErrorMessage = forge.anAlphabeticalString()
        val differentErrorMessage = throwableErrorMessage + forge.anAlphabeticalString(size = 2)
        val throwable = Throwable(throwableErrorMessage)
        val logFields = mapOf(
            Fields.ERROR_OBJECT to throwable,
            Fields.MESSAGE to differentErrorMessage
        )
        // when
        underTest.log(logFields, mockedSpan)

        // then
        verify(mockedSpan).setErrorMeta(throwable)
        verifyNoMoreInteractions(mockedSpan)
    }

    @Test
    fun `when logging fields without error but error message  will set the message`(
        forge: Forge
    ) {
        // given
        val throwableErrorMessage = forge.anAlphabeticalString()
        val logFields = mapOf(
            Fields.MESSAGE to throwableErrorMessage
        )

        // when
        underTest.log(logFields, mockedSpan)

        // then
        verify(mockedSpan).setTag(DDTags.ERROR_MSG, throwableErrorMessage)
        verifyNoMoreInteractions(mockedSpan)
    }
}
