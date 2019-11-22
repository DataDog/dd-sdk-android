/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.os.Build
import com.datadog.android.log.assertj.JsonObjectAssert.Companion.assertThat
import com.datadog.android.log.forge.Configurator
import com.datadog.android.utils.ApiLevelExtension
import com.datadog.android.utils.TestTargetApi
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.PrintWriter
import java.io.StringWriter
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal abstract class LogStrategyTest {

    lateinit var testedLogWriter: LogWriter
    lateinit var testedLogReader: LogReader

    // region Setup

    @BeforeEach
    fun `set up`() {
        val persistingStrategy = getStrategy()

        testedLogWriter = persistingStrategy.getLogWriter()
        testedLogReader = persistingStrategy.getLogReader()
    }

    abstract fun getStrategy(): LogStrategy

    abstract fun waitForNextBatch()

    // endregion

    // region Writer Tests

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `writes full log as json`(@Forgery fakeLog: Log) {
        testedLogWriter.writeLog(fakeLog)
        waitForNextBatch()
        val batch = testedLogReader.readNextBatch()!!
        val log = batch.logs.first()

        val jsonObject = JsonParser.parseString(log).asJsonObject
        assertLogMatches(jsonObject, fakeLog)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `writes minimal log as json`(@Forgery fakeLog: Log) {
        val minimalLog = fakeLog.copy(
            timestamp = null,
            userAgent = null,
            throwable = null,
            networkInfo = null,
            attributes = emptyMap(),
            tags = emptyMap()
        )

        testedLogWriter.writeLog(minimalLog)
        waitForNextBatch()
        val batch = testedLogReader.readNextBatch()!!
        val log = batch.logs.first()

        val jsonObject = JsonParser.parseString(log).asJsonObject
        assertLogMatches(jsonObject, minimalLog)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `ignores reserved attributes`(@Forgery fakeLog: Log, forge: Forge) {
        val logWithoutAttributes = fakeLog.copy(attributes = emptyMap())
        val attributes = forge.aMap {
            anElementFrom(*LogStrategy.reservedAttributes) to forge.anAsciiString()
        }.toMap()
        val logWithReservedAttributes = fakeLog.copy(attributes = attributes)

        testedLogWriter.writeLog(logWithReservedAttributes)
        waitForNextBatch()
        val batch = testedLogReader.readNextBatch()!!
        val log = batch.logs.first()

        val jsonObject = JsonParser.parseString(log).asJsonObject
        assertLogMatches(jsonObject, logWithoutAttributes)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `writes batch of logs`(@Forgery fakeLogs: List<Log>) {
        val sentLogs = mutableListOf<Log>()
        fakeLogs.forEachIndexed { i, log ->
            val updatedLog = log.copy(level = i % 9)
            testedLogWriter.writeLog(updatedLog)
            sentLogs.add(updatedLog)
        }
        waitForNextBatch()
        val batch = testedLogReader.readNextBatch()!!

        batch.logs.forEachIndexed { i, log ->
            val jsonObject = JsonParser.parseString(log).asJsonObject
            assertLogMatches(jsonObject, sentLogs[i])
        }
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `writes in new batch if delay passed`(@Forgery fakeLog: Log, @Forgery nextLog: Log) {
        testedLogWriter.writeLog(fakeLog)
        waitForNextBatch()

        testedLogWriter.writeLog(nextLog)
        val batch = testedLogReader.readNextBatch()!!
        val log = batch.logs.first()

        val jsonObject = JsonParser.parseString(log).asJsonObject
        assertLogMatches(jsonObject, fakeLog)
    }

    // endregion

    // region Reader Tests

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `read returns null when first batch is already sent`(@Forgery fakeLog: Log) {
        testedLogWriter.writeLog(fakeLog)
        waitForNextBatch()
        val batch = testedLogReader.readNextBatch()
        checkNotNull(batch)

        testedLogReader.dropBatch(batch.id)
        val batch2 = testedLogReader.readNextBatch()

        assertThat(batch2)
            .isNull()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
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

    private fun assertLogMatches(
        jsonObject: JsonObject,
        log: Log
    ) {
        assertThat(jsonObject)
            .hasField(LogStrategy.TAG_MESSAGE, log.message)
            .hasField(LogStrategy.TAG_SERVICE_NAME, log.serviceName)
            .hasField(LogStrategy.TAG_STATUS, levels[log.level])

        if (!log.userAgent.isNullOrBlank()) {
            assertThat(jsonObject).hasField(LogStrategy.TAG_USER_AGENT_SDK, log.userAgent)
        } else {
            assertThat(jsonObject).doesNotHaveField(LogStrategy.TAG_DATE)
        }

        if (log.timestamp != null) {
            assertThat(jsonObject).hasStringField(LogStrategy.TAG_DATE, nullable = false)
        } else {
            assertThat(jsonObject).doesNotHaveField(LogStrategy.TAG_DATE)
        }

        assertNetworkInfoMatches(log, jsonObject)

        assertFieldsMatch(log, jsonObject)
        assertTagsMatch(jsonObject, log)
        assertThrowableMatches(log, jsonObject)
    }

    private fun assertNetworkInfoMatches(log: Log, jsonObject: JsonObject) {
        val info = log.networkInfo
        if (info != null) {
            assertThat(jsonObject)
                .hasField(LogStrategy.TAG_NETWORK_INFO) {
                    hasField(LogStrategy.TAG_NETWORK_CONNECTIVITY, info.connectivity.serialized)
                    if (!info.carrierName.isNullOrBlank()) {
                        hasField(LogStrategy.TAG_NETWORK_CARRIER_NAME, info.carrierName)
                    }
                    if (info.carrierId >= 0) {
                        hasField(LogStrategy.TAG_NETWORK_CARRIER_ID, info.carrierId)
                    }
                }
        } else {
            assertThat(jsonObject).doesNotHaveField(LogStrategy.TAG_NETWORK_INFO)
        }
    }

    private fun assertFieldsMatch(log: Log, jsonObject: JsonObject) {
        log.attributes
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

    private fun assertTagsMatch(jsonObject: JsonObject, log: Log) {
        val jsonTagString = (jsonObject[LogStrategy.TAG_DATADOG_TAGS] as? JsonPrimitive)?.asString

        if (jsonTagString.isNullOrBlank()) {
            assertThat(log.tags)
                .isEmpty()
        } else {
            val tags = jsonTagString
                .split(',')
                .map { it.split(':') }
                .map { it[0] to it[1] }
                .toMap()

            assertThat(tags)
                .containsAllEntriesOf(log.tags.filter { it.key.isNotBlank() && it.value != null })
        }
    }

    private fun assertThrowableMatches(log: Log, jsonObject: JsonObject) {
        val throwable = log.throwable
        if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))

            assertThat(jsonObject)
                .hasField(LogStrategy.TAG_ERROR_KIND, throwable.javaClass.simpleName)
                .hasField(LogStrategy.TAG_ERROR_MESSAGE, throwable.message)
                .hasField(LogStrategy.TAG_ERROR_STACK, sw.toString())
        }
    }

    // endregion

    companion object {

        const val MAX_BATCH_SIZE: Long = 32 * 1024

        private val levels = arrayOf(
            "DEBUG", "DEBUG", "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "CRITICAL"
        )
    }
}
