/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.android.Datadog
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.resolveTagName
import com.datadog.tools.unit.annotations.SystemErrorStream
import com.datadog.tools.unit.annotations.SystemOutStream
import com.datadog.tools.unit.assertj.ByteArrayOutputStreamAssert.Companion.assertThat
import com.datadog.tools.unit.extensions.SystemStreamExtension
import com.datadog.tools.unit.setFieldValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(SystemStreamExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogcatLogHandlerTest {

    lateinit var testedHandler: LogHandler

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
        fakeServiceName = forge.anAlphabeticalString()
        fakeLoggerName = forge.anAlphabeticalString()
        fakeMessage = forge.anAlphabeticalString()
        fakeLevel = forge.anInt(2, 8)
        fakeAttributes = forge.aMap { anAlphabeticalString() to anInt() }
        fakeTags = forge.aList { anAlphabeticalString() }.toSet()

        testedHandler = LogcatLogHandler(fakeServiceName)
    }

    @Test
    fun `outputs log`(
        @SystemOutStream outputStream: ByteArrayOutputStream,
        @SystemErrorStream errorStream: ByteArrayOutputStream
    ) {
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        val expectedTag = resolveTagName(this, fakeServiceName)
        assertThat(outputStream)
            .hasLogLine(fakeLevel, expectedTag, fakeMessage, Datadog.isDebug)
    }

    @Test
    fun `outputs log on background thread`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream,
        @SystemErrorStream errorStream: ByteArrayOutputStream
    ) {
        val threadName = forge.anAlphabeticalString()
        val countDownLatch = CountDownLatch(1)
        testedHandler = LogcatLogHandler(fakeServiceName)
        val runnable = Runnable {
            testedHandler.handleLog(
                fakeLevel,
                fakeMessage,
                fakeThrowable,
                fakeAttributes,
                fakeTags
            )
            countDownLatch.countDown()
        }
        val thread = Thread(runnable, threadName)
        val expectedTag = resolveTagName(runnable, fakeServiceName)

        thread.start()
        countDownLatch.await(1, TimeUnit.SECONDS)

        assertThat(outputStream)
            .hasLogLine(fakeLevel, expectedTag, fakeMessage, Datadog.isDebug)
    }

    @Test
    fun `outputs minimal log`(
        @SystemOutStream outputStream: ByteArrayOutputStream,
        @SystemErrorStream errorStream: ByteArrayOutputStream
    ) {
        val expectedTag = resolveTagName(this, fakeServiceName)

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            emptyMap(),
            emptySet()
        )

        assertThat(outputStream)
            .hasLogLine(fakeLevel, expectedTag, fakeMessage, Datadog.isDebug)
    }

    @Test
    fun `uses caller name as tag if inDebug`(
        @SystemOutStream outputStream: ByteArrayOutputStream,
        @SystemErrorStream errorStream: ByteArrayOutputStream
    ) {
        // given
        val isDebug = Datadog.isDebug
        Datadog.setFieldValue("isDebug", true)

        // when
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            emptyMap(),
            emptySet()
        )

        // then
        val expectedTag = resolveTagName(this, fakeServiceName)

        assertThat(outputStream)
            .hasLogLine(fakeLevel, expectedTag, fakeMessage, Datadog.isDebug)
        assertThat(errorStream)
            .isEmpty()
        Datadog.setFieldValue("isDebug", isDebug)
    }
}
