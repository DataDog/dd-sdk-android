/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.util.Log as AndroidLog
import com.datadog.android.log.Log
import com.datadog.android.log.internal.file.LogFileWriter
import com.google.gson.JsonObject
import com.google.gson.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
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
internal abstract class LogStrategyTest {

    lateinit var testedLogWriter: LogWriter
    lateinit var testedLogReader: LogReader

    lateinit var fakeLog: Log
    lateinit var fakeLogs: List<Log>

    // region Setup

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeLog = createFakeLog(forge)
        fakeLogs = forge.aList { createFakeLog(this) }

        val persistingStrategy = getStrategy()

        testedLogWriter = persistingStrategy.getLogWriter()
        testedLogReader = persistingStrategy.getLogReader()
    }

    abstract fun getStrategy(): LogStrategy

    // endregion

    // region Tests

    @Test
    fun `writes full log as json`() {

        testedLogWriter.writeLog(fakeLog)
        val log = testedLogReader.readNextLog()

        val jsonObject = JsonParser.parseString(log).asJsonObject
        assertLogMatches(jsonObject, fakeLog)
    }

    @Test
    fun `writes minimal log as json`() {
        val minimalLog = fakeLog.copy(
            timestamp = null,
            userAgent = null,
            throwable = null
        )

        testedLogWriter.writeLog(minimalLog)
        val log = testedLogReader.readNextLog()

        val jsonObject = JsonParser.parseString(log).asJsonObject

        assertThat(jsonObject)
            .hasField(LogFileWriter.TAG_MESSAGE, fakeLog.message)
            .hasField(LogFileWriter.TAG_SERVICE_NAME, fakeLog.serviceName)
            .hasField(LogFileWriter.TAG_STATUS, levels[fakeLog.level])
            .hasNoField(LogFileWriter.TAG_USER_AGENT_SDK)
            .hasNoField(LogFileWriter.TAG_DATE)
    }

    @Test
    fun `writes batch of logs`() {
        fakeLogs.forEach {
            testedLogWriter.writeLog(it)
        }

        val batch = testedLogReader.readNextBatch()

        batch.forEachIndexed { i, log ->
            val jsonObject = JsonParser.parseString(log).asJsonObject
            assertLogMatches(jsonObject, fakeLogs[i])
        }
    }

    // endregion

    // region Internal

    private fun assertLogMatches(jsonObject: JsonObject, log: Log) {
        assertThat(jsonObject)
            .hasField(LogFileWriter.TAG_MESSAGE, log.message)
            .hasField(LogFileWriter.TAG_SERVICE_NAME, log.serviceName)
            .hasField(LogFileWriter.TAG_STATUS, levels[log.level])
            .hasField(LogFileWriter.TAG_USER_AGENT_SDK, log.userAgent)
            .hasStringField(LogFileWriter.TAG_DATE, nullable = false)
    }

    private fun createFakeLog(forge: Forge): Log {
        return Log(
            serviceName = forge.anAlphabeticalString(),
            message = forge.anAlphabeticalString(),
            userAgent = forge.anAlphabeticalString(),
            level = forge.anElementFrom(
                AndroidLog.VERBOSE, AndroidLog.DEBUG, AndroidLog.INFO, AndroidLog.WARN,
                AndroidLog.ERROR, AndroidLog.ASSERT
            ),
            timestamp = forge.aLong(),
            throwable = null
        )
    }

    // endregion
    companion object {
        private val levels = arrayOf(
            "0", "1", "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "ERROR"
        )
    }
}
