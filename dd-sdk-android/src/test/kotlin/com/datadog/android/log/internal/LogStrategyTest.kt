/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.content.Context
import android.os.Build
import android.util.Log as AndroidLog
import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import com.datadog.android.log.assertj.JsonObjectAssert.Companion.assertThat
import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.net.NetworkInfo
import com.datadog.tools.unit.annotations.SystemOutStream
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.SystemOutputExtension
import com.datadog.tools.unit.invokeMethod
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date
import kotlin.math.min
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class),
    ExtendWith(SystemOutputExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings()
internal abstract class LogStrategyTest {

    lateinit var testedLogWriter: LogWriter
    lateinit var testedLogReader: LogReader

    // region Setup

    @BeforeEach
    fun `set up`(forge: Forge) {
        val mockContext: Context = mock()
        whenever(mockContext.applicationContext) doReturn mockContext
        whenever(mockContext.packageName) doReturn forge.anAlphabeticalString()

        Datadog.initialize(mockContext, forge.anHexadecimalString())
        val persistingStrategy = getStrategy()

        testedLogWriter = persistingStrategy.getLogWriter()
        testedLogReader = persistingStrategy.getLogReader()

        setUp(testedLogWriter, testedLogReader)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.invokeMethod("stop")
    }

    abstract fun getStrategy(): LogStrategy

    abstract fun setUp(writer: LogWriter, reader: LogReader)

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
            throwable = null,
            networkInfo = null,
            attributes = emptyMap(),
            tags = emptyList()
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
        val logCount = min(MAX_LOGS_PER_BATCH, fakeLogs.size)
        for (i in 0 until logCount) {
            val log = fakeLogs[i]
            val updatedLog = log.copy(level = i % 8)
            testedLogWriter.writeLog(updatedLog)
            sentLogs.add(updatedLog)
        }
        waitForNextBatch()
        val batch = testedLogReader.readNextBatch()!!

        val batchLogCount = min(MAX_LOGS_PER_BATCH, batch.logs.size)
        for (i in 0 until batchLogCount) {
            val log = batch.logs[i]
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

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `writes batch of logs from mutliple threads`(@Forgery fakeLogs: List<Log>) {
        val runnables = fakeLogs.map {
            Runnable { testedLogWriter.writeLog(it) }
        }
        runnables.forEach {
            Thread(it).start()
        }

        waitForNextBatch()
        waitForNextBatch()
        val batch = testedLogReader.readNextBatch()!!

        batch.logs.forEachIndexed { i, log ->
            val jsonObject = JsonParser.parseString(log).asJsonObject
            assertHasMatches(jsonObject, fakeLogs)
        }
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `don't write log if size is too big`(forge: Forge) {
        val bigLog = Log(
            level = AndroidLog.ASSERT,
            serviceName = forge.anAlphabeticalString(size = 65536),
            message = forge.anAlphabeticalString(size = 131072),
            tags = forge.aList(size = 256) { forge.anAlphabeticalString(size = 128) },
            attributes = forge.aMap(size = 256) {
                forge.anAlphabeticalString(size = 64) to forge.anAlphabeticalString(
                    size = 128
                )
            },
            networkInfo = NetworkInfo(
                connectivity = NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER,
                carrierId = forge.aHugeInt(),
                carrierName = forge.anAlphabeticalString(size = 256)
            ),
            throwable = ArrayIndexOutOfBoundsException(forge.anAlphabeticalString()),
            timestamp = forge.aLong(),
            loggerName = forge.anAlphabeticalString(),
            threadName = forge.anAlphabeticalString()
        )

        testedLogWriter.writeLog(bigLog)
        waitForNextBatch()
        val batch = testedLogReader.readNextBatch()

        assertThat(batch)
            .isNull()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `limit the number of logs per batch`(forge: Forge) {
        val logs = forge.aList(MAX_LOGS_PER_BATCH * 3) {
            forge.getForgery<Log>().copy(
                serviceName = anAlphabeticalString(size = aTinyInt()),
                message = anAlphabeticalString(size = aTinyInt()),
                throwable = null,
                networkInfo = null,
                attributes = emptyMap(),
                tags = emptyList()
            )
        }

        logs.forEach { testedLogWriter.writeLog(it) }
        waitForNextBatch()
        val batch = testedLogReader.readNextBatch()!!
        testedLogReader.dropBatch(batch.id)
        waitForNextBatch()
        val batch2 = testedLogReader.readNextBatch()!!

        assertThat(batch.logs.size)
            .isEqualTo(MAX_LOGS_PER_BATCH)
        assertThat(batch2.logs.size)
            .isEqualTo(MAX_LOGS_PER_BATCH)
        batch.logs.forEachIndexed { i, log ->
            val jsonObject = JsonParser.parseString(log).asJsonObject
            assertLogMatches(jsonObject, logs[i])
        }
        batch2.logs.forEachIndexed { i, log ->
            val jsonObject = JsonParser.parseString(log).asJsonObject
            assertLogMatches(jsonObject, logs[i + MAX_LOGS_PER_BATCH])
        }
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
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `read returns null when drop all was called`(
        @Forgery firstLogs: List<Log>,
        @Forgery secondLogs: List<Log>
    ) {
        firstLogs.forEach { testedLogWriter.writeLog(it) }
        waitForNextBatch()
        secondLogs.forEach { testedLogWriter.writeLog(it) }

        testedLogReader.dropAllBatches()
        val batch = testedLogReader.readNextBatch()

        assertThat(batch)
            .isNull()
    }

    @Test
    fun `fails gracefully if sent batch with unknown id`(
        forge: Forge,
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        testedLogReader.dropBatch(forge.aNumericalString())
        if (BuildConfig.DEBUG) {
            val logMessages = outputStream.toString().trim().split("\n")
            assertThat(logMessages[logMessages.size - 1].trim())
                .withFailMessage("We were expecting a log message here")
                .matches("W/DD_LOG: LogFileReader: .+")
        }
    }

    // endregion

    // region Internal

    private fun assertHasMatches(
        jsonObject: JsonObject,
        logs: List<Log>
    ) {
        val message = (jsonObject[LogStrategy.TAG_MESSAGE] as JsonPrimitive).asString
        val serviceName = (jsonObject[LogStrategy.TAG_SERVICE_NAME] as JsonPrimitive).asString
        val status = (jsonObject[LogStrategy.TAG_STATUS] as JsonPrimitive).asString

        val roughMatches = logs.filter {
            message == it.message && serviceName == it.serviceName && status == levels[it.level]
        }

        assertThat(roughMatches).isNotEmpty()
    }

    private fun assertLogMatches(
        jsonObject: JsonObject,
        log: Log
    ) {
        assertThat(jsonObject)
            .hasField(LogStrategy.TAG_MESSAGE, log.message)
            .hasField(LogStrategy.TAG_SERVICE_NAME, log.serviceName)
            .hasField(LogStrategy.TAG_STATUS, levels[log.level])
            .hasField(LogStrategy.TAG_LOGGER_NAME, log.loggerName)
            .hasField(LogStrategy.TAG_THREAD_NAME, log.threadName)

        // yyyy-mm-ddThh:mm:ss.SSSZ
        assertThat(jsonObject).hasStringFieldMatching(
            LogStrategy.TAG_DATE,
            "\\d+\\-\\d{2}\\-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")

        assertNetworkInfoMatches(log, jsonObject)

        assertFieldsMatch(log, jsonObject)
        assertTagsMatch(jsonObject, log)
        assertThrowableMatches(log, jsonObject)
    }

    private fun assertNetworkInfoMatches(log: Log, jsonObject: JsonObject) {
        val info = log.networkInfo
        if (info != null) {
            assertThat(jsonObject).apply {
                hasField(LogStrategy.TAG_NETWORK_CONNECTIVITY, info.connectivity.serialized)
                if (!info.carrierName.isNullOrBlank()) {
                    hasField(LogStrategy.TAG_NETWORK_CARRIER_NAME, info.carrierName)
                } else {
                    doesNotHaveField(LogStrategy.TAG_NETWORK_CARRIER_NAME)
                }
                if (info.carrierId >= 0) {
                    hasField(LogStrategy.TAG_NETWORK_CARRIER_ID, info.carrierId)
                } else {
                    doesNotHaveField(LogStrategy.TAG_NETWORK_CARRIER_ID)
                }
            }
        } else {
            assertThat(jsonObject)
                .doesNotHaveField(LogStrategy.TAG_NETWORK_CONNECTIVITY)
                .doesNotHaveField(LogStrategy.TAG_NETWORK_CARRIER_NAME)
                .doesNotHaveField(LogStrategy.TAG_NETWORK_CARRIER_ID)
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
                    else -> assertThat(jsonObject).hasField(it.key, value.toString())
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
                .toList()

            assertThat(tags)
                .containsExactlyInAnyOrder(*log.tags.toTypedArray())
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
        } else {
            assertThat(jsonObject)
                .doesNotHaveField(LogStrategy.TAG_ERROR_KIND)
                .doesNotHaveField(LogStrategy.TAG_ERROR_MESSAGE)
                .doesNotHaveField(LogStrategy.TAG_ERROR_STACK)
        }
    }

    // endregion

    companion object {

        const val MAX_BATCH_SIZE: Long = 128 * 1024
        const val MAX_LOGS_PER_BATCH: Int = 32

        private val levels = arrayOf(
            "DEBUG", "DEBUG", "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "CRITICAL"
        )
    }
}
