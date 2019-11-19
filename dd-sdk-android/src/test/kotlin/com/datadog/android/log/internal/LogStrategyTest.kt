/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import com.datadog.android.log.Configurator
import com.google.gson.JsonObject
import com.google.gson.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.lang.IllegalStateException
import java.util.Date
import org.assertj.core.api.Assertions.assertThat
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
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal abstract class LogStrategyTest {

    lateinit var testedLogWriter: LogWriter
    lateinit var testedLogReader: LogReader

    lateinit var fakeLogs: List<Log>

    // region Setup

    @BeforeEach
    fun `set up`(forge: Forge, @Forgery fakeLog: Log) {
        fakeLogs = forge.aList(size = 512) { fakeLog.copy(message = anAlphabeticalString()) }

        val persistingStrategy = getStrategy()

        testedLogWriter = persistingStrategy.getLogWriter()
        testedLogReader = persistingStrategy.getLogReader()
    }

    abstract fun getStrategy(): LogStrategy

    abstract fun waitForNextBatch()

    // endregion

    // region Writer Tests

    @Test
    fun `writes full log as json`(@Forgery fakeLog: Log) {

        testedLogWriter.writeLog(fakeLog)
        waitForNextBatch()
        val batch = testedLogReader.readNextBatch()!!
        val log = batch.second.first()

        val jsonObject = JsonParser.parseString(log).asJsonObject
        assertLogMatches(jsonObject, fakeLog)
    }

    @Test
    fun `writes minimal log as json`(@Forgery fakeLog: Log) {
        val minimalLog = fakeLog.copy(
            timestamp = null,
            userAgent = null,
            throwable = null
        )

        testedLogWriter.writeLog(minimalLog)
        waitForNextBatch()
        val batch = testedLogReader.readNextBatch()!!
        val log = batch.second.first()

        val jsonObject = JsonParser.parseString(log).asJsonObject

        assertThat(jsonObject)
            .hasField(LogStrategy.TAG_MESSAGE, fakeLog.message)
            .hasField(LogStrategy.TAG_SERVICE_NAME, fakeLog.serviceName)
            .hasField(LogStrategy.TAG_STATUS, levels[fakeLog.level])
            .hasNoField(LogStrategy.TAG_USER_AGENT_SDK)
            .hasNoField(LogStrategy.TAG_DATE)
    }

    @Test
    fun `writes batch of logs`() {
        fakeLogs.forEach {
            testedLogWriter.writeLog(it)
        }
        waitForNextBatch()
        val batch = testedLogReader.readNextBatch()!!

        batch.second.forEachIndexed { i, log ->
            val jsonObject = JsonParser.parseString(log).asJsonObject
            assertLogMatches(jsonObject, fakeLogs[i])
        }
    }

    @Test
    fun `writes in new batch if delay passed`(@Forgery fakeLog: Log, @Forgery nextLog: Log) {
        testedLogWriter.writeLog(fakeLog)
        waitForNextBatch()

        testedLogWriter.writeLog(nextLog)
        val batch = testedLogReader.readNextBatch()!!
        val log = batch.second.first()

        val jsonObject = JsonParser.parseString(log).asJsonObject
        assertLogMatches(jsonObject, fakeLog)
    }

    // endregion

    // region Reader Tests

    @Test
    fun `read returns null when first batch is already sent`(@Forgery fakeLog: Log) {
        testedLogWriter.writeLog(fakeLog)
        waitForNextBatch()
        val batch = testedLogReader.readNextBatch()
        checkNotNull(batch)

        testedLogReader.dropBatch(batch.first)
        val batch2 = testedLogReader.readNextBatch()

        assertThat(batch2)
            .isNull()
    }

    @Test
    fun `read returns null when first batch is too recent`(@Forgery fakeLog: Log) {
        testedLogWriter.writeLog(fakeLog)
        val batch = testedLogReader.readNextBatch()

        assertThat(batch)
            .isNull()
    }

    @Test
    fun `read returns null when nothing was written`() {

        val batch = testedLogReader.readNextBatch()

        assertThat(batch)
            .isNull()
    }

    @Test
    fun `fails gracefully if sent batch with unknown id`(forge: Forge) {
        testedLogReader.dropBatch(forge.aNumericalString())

        // Nothing to do, just check that no exception is thrown
    }

    // endregion

    // region Internal

    private fun assertLogMatches(jsonObject: JsonObject, log: Log) {
        assertThat(jsonObject)
            .hasField(LogStrategy.TAG_MESSAGE, log.message)
            .hasField(LogStrategy.TAG_SERVICE_NAME, log.serviceName)
            .hasField(LogStrategy.TAG_STATUS, levels[log.level])
            .hasField(LogStrategy.TAG_USER_AGENT_SDK, log.userAgent)
            .hasStringField(LogStrategy.TAG_DATE, nullable = false)

        log.fields
            .filter { it.key.isNotBlank() }
            .forEach {
                val value = it.value
                when (value) {
                    null -> assertThat(jsonObject).hasNullField(it.key)
                    is Boolean -> assertThat(jsonObject).hasField(it.key, value)
                    is Int -> assertThat(jsonObject).hasField(it.key, value)
                    is Long -> assertThat(jsonObject).hasField(it.key, value)
                    is Float -> assertThat(jsonObject).hasField(it.key, value)
                    is Double -> assertThat(jsonObject).hasField(it.key, value)
                    is String -> assertThat(jsonObject).hasField(it.key, value)
                    is Date -> assertThat(jsonObject).hasField(it.key, value.time)
                    else -> throw IllegalStateException(
                        "Unable to handle key:${it.key} with value:$value"
                    )
                }
            }
        }
    }

    // endregion

    companion object {
        private val levels = arrayOf(
            "DEBUG", "DEBUG", "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "ERROR"
        )
    }
}
