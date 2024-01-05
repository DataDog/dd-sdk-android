/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CombinedLogHandlerTest {

    lateinit var testedHandler: LogHandler

    lateinit var mockDevLogHandlers: Array<LogHandler>

    lateinit var fakeServiceName: String
    lateinit var fakeLoggerName: String
    lateinit var fakeMessage: String
    lateinit var fakeTags: Set<String>
    lateinit var fakeAttributes: Map<String, Any?>

    var fakeLevel: Int = 0

    @Forgery
    lateinit var fakeThrowable: Throwable

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockDevLogHandlers = forge.aList { mock<LogHandler>() }.toTypedArray()
        fakeServiceName = forge.anAlphabeticalString()
        fakeLoggerName = forge.anAlphabeticalString()
        fakeMessage = forge.anAlphabeticalString()
        fakeLevel = forge.anInt(2, 8)
        fakeAttributes = forge.aMap { anAlphabeticalString() to anInt() }
        fakeTags = forge.aList { anAlphabeticalString() }.toSet()

        testedHandler = CombinedLogHandler(*mockDevLogHandlers)
    }

    @Test
    fun `forwards log`() {
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        mockDevLogHandlers.forEach {
            verify(it).handleLog(fakeLevel, fakeMessage, fakeThrowable, fakeAttributes, fakeTags)
        }
    }

    @Test
    fun `forwards log on background thread`(forge: Forge) {
        val threadName = forge.anAlphabeticalString()
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

        thread.start()
        countDownLatch.await(1, TimeUnit.SECONDS)

        mockDevLogHandlers.forEach {
            verify(it).handleLog(fakeLevel, fakeMessage, fakeThrowable, fakeAttributes, fakeTags)
        }
    }

    @Test
    fun `forwards minimal log`() {
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            emptyMap(),
            emptySet()
        )

        mockDevLogHandlers.forEach {
            verify(it).handleLog(fakeLevel, fakeMessage, null, emptyMap(), emptySet())
        }
    }

    @Test
    fun `forwards log with error strings`(
        @StringForgery errorKind: String,
        @StringForgery errorMessage: String,
        @StringForgery errorStack: String
    ) {
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            errorKind,
            errorMessage,
            errorStack,
            fakeAttributes,
            fakeTags
        )

        mockDevLogHandlers.forEach {
            verify(it).handleLog(
                fakeLevel,
                fakeMessage,
                errorKind,
                errorMessage,
                errorStack,
                fakeAttributes,
                fakeTags
            )
        }
    }

    @Test
    fun `forwards log with error strings on background thread`(
        @StringForgery errorKind: String,
        @StringForgery errorMessage: String,
        @StringForgery errorStack: String,
        forge: Forge
    ) {
        val threadName = forge.anAlphabeticalString()
        val countDownLatch = CountDownLatch(1)
        val thread = Thread(
            {
                testedHandler.handleLog(
                    fakeLevel,
                    fakeMessage,
                    errorKind,
                    errorMessage,
                    errorStack,
                    fakeAttributes,
                    fakeTags
                )
                countDownLatch.countDown()
            },
            threadName
        )

        thread.start()
        countDownLatch.await(1, TimeUnit.SECONDS)

        mockDevLogHandlers.forEach {
            verify(it).handleLog(
                fakeLevel,
                fakeMessage,
                errorKind,
                errorMessage,
                errorStack,
                fakeAttributes,
                fakeTags
            )
        }
    }
}
