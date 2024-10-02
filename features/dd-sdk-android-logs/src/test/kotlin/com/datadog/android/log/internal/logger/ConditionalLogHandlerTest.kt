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
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ConditionalLogHandlerTest {

    lateinit var testedHandler: LogHandler

    @Mock
    lateinit var mockDelegateLogHandler: LogHandler

    @StringForgery
    lateinit var fakeMessage: String

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakeTags: Set<String>

    lateinit var fakeAttributes: Map<String, Any?>

    @IntForgery(min = 2, max = 8)
    var fakeLevel: Int = 0

    var fakeCondition = false

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
        fakeAttributes = forge.exhaustiveAttributes()

        testedHandler = ConditionalLogHandler(mockDelegateLogHandler) { _, _ ->
            fakeCondition
        }
    }

    @Test
    fun `M forward log W handleLog (throwable, condition true)`() {
        // Given
        fakeCondition = true

        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        // Then
        verify(mockDelegateLogHandler).handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )
    }

    @Test
    fun `M forward log on background thread W handleLog (throwable, condition true)`(
        @StringForgery threadName: String
    ) {
        // Given
        fakeCondition = true
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
        verify(mockDelegateLogHandler).handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )
    }

    @Test
    fun `M forward minimal log W handleLog (null throwable, condition true)`() {
        // Given
        fakeCondition = true

        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            emptyMap(),
            emptySet()
        )

        // Then
        verify(mockDelegateLogHandler).handleLog(
            fakeLevel,
            fakeMessage,
            null,
            emptyMap(),
            emptySet()
        )
    }

    @Test
    fun `M not forward log W handleLog (throwable, condition false)`() {
        // Given
        fakeCondition = false

        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        // Then
        verifyNoInteractions(mockDelegateLogHandler)
    }

    @Test
    fun `M not forward log on background thread W handleLog (throwable, condition false)`(
        @StringForgery threadName: String
    ) {
        // Given
        fakeCondition = false
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
        verifyNoInteractions(mockDelegateLogHandler)
    }

    @Test
    fun `M not forward minimal log W handleLog (null throwable, condition false)`() {
        // Given
        fakeCondition = false

        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            emptyMap(),
            emptySet()
        )

        // Then
        verifyNoInteractions(mockDelegateLogHandler)
    }

    @Test
    fun `M forward log W handleLog (stacktrace, condition true)`() {
        // Given
        fakeCondition = true

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
        verify(mockDelegateLogHandler).handleLog(
            fakeLevel,
            fakeMessage,
            fakeErrorKind,
            fakeErrorMessage,
            fakeErrorStackTrace,
            fakeAttributes,
            fakeTags
        )
    }

    @Test
    fun `M forward log on background thread W handleLog (stacktrace, condition true)`(
        @StringForgery threadName: String
    ) {
        // Given
        fakeCondition = true
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
        verify(mockDelegateLogHandler).handleLog(
            fakeLevel,
            fakeMessage,
            fakeErrorKind,
            fakeErrorMessage,
            fakeErrorStackTrace,
            fakeAttributes,
            fakeTags
        )
    }

    @Test
    fun `M forward minimal log W handleLog (null stacktrace, condition true)`() {
        // Given
        fakeCondition = true

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
        verify(mockDelegateLogHandler).handleLog(
            fakeLevel,
            fakeMessage,
            null,
            null,
            null,
            emptyMap(),
            emptySet()
        )
    }

    @Test
    fun `M not forward log W handleLog (stacktrace, condition false)`() {
        // Given
        fakeCondition = false

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
        verifyNoInteractions(mockDelegateLogHandler)
    }

    @Test
    fun `M not forward log on background thread W handleLog (stacktrace, condition false)`(
        @StringForgery threadName: String
    ) {
        // Given
        fakeCondition = false
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
        verifyNoInteractions(mockDelegateLogHandler)
    }

    @Test
    fun `M not forward minimal log W handleLog (null stacktrace, condition false)`() {
        // Given
        fakeCondition = false

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
        verifyNoInteractions(mockDelegateLogHandler)
    }
}
