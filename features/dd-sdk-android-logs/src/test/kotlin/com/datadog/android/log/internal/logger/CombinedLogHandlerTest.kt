/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.android.tests.elmyr.exhaustiveAttributes
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CombinedLogHandlerTest {

    lateinit var testedHandler: LogHandler

    lateinit var mockDelegateLogHandlers: Array<LogHandler>

    @StringForgery
    lateinit var fakeMessage: String

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakeTags: Set<String>

    lateinit var fakeAttributes: Map<String, Any?>

    @IntForgery(min = 2, max = 8)
    var fakeLevel: Int = 0

    @Forgery
    lateinit var fakeThrowable: Throwable

    @StringForgery
    lateinit var fakeErrorKind: String

    @StringForgery
    lateinit var fakeErrorMessage: String

    @StringForgery
    lateinit var fakeErrorStackTrace: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockDelegateLogHandlers = forge.aList { mock<LogHandler>() }.toTypedArray()
        fakeAttributes = forge.exhaustiveAttributes()

        testedHandler = CombinedLogHandler(*mockDelegateLogHandlers)
    }

    @Test
    fun `M forward log to all delegates W handleLog {throwable}`() {
        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        // Then
        mockDelegateLogHandlers.forEach {
            verify(it).handleLog(fakeLevel, fakeMessage, fakeThrowable, fakeAttributes, fakeTags)
        }
    }

    @Test
    fun `M forward log to all delegates W handleLog {throwable, background thread}`(
        @StringForgery(StringForgeryType.ALPHABETICAL) threadName: String
    ) {
        // Given
        val countDownLatch = CountDownLatch(1)
        val thread = Thread(
            {
                testedHandler.handleLog(
                    fakeLevel,
                    fakeMessage,
                    fakeThrowable,
                    fakeAttributes,
                    fakeTags
                )
                countDownLatch.countDown()
            },
            threadName
        )

        // When
        thread.start()
        countDownLatch.await(1, TimeUnit.SECONDS)

        // Then
        mockDelegateLogHandlers.forEach {
            verify(it).handleLog(fakeLevel, fakeMessage, fakeThrowable, fakeAttributes, fakeTags)
        }
    }

    @Test
    fun `M forward log to all delegates W handleLog {null throwable}`() {
        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            emptyMap(),
            emptySet()
        )

        // Then
        mockDelegateLogHandlers.forEach {
            verify(it).handleLog(fakeLevel, fakeMessage, null, emptyMap(), emptySet())
        }
    }

    @Test
    fun `M forward log to all delegates W handleLog {stacktrace}`() {
        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeErrorKind,
            fakeErrorMessage,
            fakeErrorStackTrace,
            fakeAttributes,
            fakeTags
        )

        // Then
        mockDelegateLogHandlers.forEach {
            verify(it).handleLog(
                fakeLevel,
                fakeMessage,
                fakeErrorKind,
                fakeErrorMessage,
                fakeErrorStackTrace,
                fakeAttributes,
                fakeTags
            )
        }
    }

    @Test
    fun `M forward log to all delegates W handleLog {stacktrace, background thread}`(
        @StringForgery(StringForgeryType.ALPHABETICAL) threadName: String
    ) {
        // Given
        val countDownLatch = CountDownLatch(1)
        val thread = Thread(
            {
                testedHandler.handleLog(
                    fakeLevel,
                    fakeMessage,
                    fakeErrorKind,
                    fakeErrorMessage,
                    fakeErrorStackTrace,
                    fakeAttributes,
                    fakeTags
                )
                countDownLatch.countDown()
            },
            threadName
        )

        // When
        thread.start()
        countDownLatch.await(1, TimeUnit.SECONDS)

        // Then
        mockDelegateLogHandlers.forEach {
            verify(it).handleLog(
                fakeLevel,
                fakeMessage,
                fakeErrorKind,
                fakeErrorMessage,
                fakeErrorStackTrace,
                fakeAttributes,
                fakeTags
            )
        }
    }

    @Test
    fun `M forward log to all delegates W handleLog {null stacktrace}`() {
        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            null,
            null,
            emptyMap(),
            emptySet()
        )

        // Then
        mockDelegateLogHandlers.forEach {
            verify(it).handleLog(fakeLevel, fakeMessage, null, null, null, emptyMap(), emptySet())
        }
    }
}
