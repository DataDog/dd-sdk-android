/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.file.DeferredWriter
import com.datadog.android.core.internal.domain.FilePersistenceStrategyTest
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.threading.LazyHandlerThread
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogFileStrategy
import com.datadog.android.log.internal.domain.LogSerializer
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.datadog.tools.unit.invokeMethod
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@ForgeConfiguration(Configurator::class)
internal class LogFileStrategyTest :
    FilePersistenceStrategyTest<Log>(LogFileStrategy.LOGS_FOLDER, modelClass = Log::class.java) {

    // region LogStrategyTest

    override fun getStrategy(): PersistenceStrategy<Log> {
        return LogFileStrategy(
            context = mockContext,
            recentDelayMs = RECENT_DELAY_MS,
            maxBatchSize = MAX_BATCH_SIZE,
            maxLogPerBatch = MAX_MESSAGES_PER_BATCH,
            maxDiskSpace = MAX_DISK_SPACE
        )
    }

    override fun setUp(writer: Writer<Log>, reader: Reader) {
        // add fake data into the old data directory
        val oldDir = File(tempDir, LogFileStrategy.DATA_FOLDER_ROOT)
        oldDir.mkdirs()
        val file1 = File(oldDir, "file1")
        val file2 = File(oldDir, "file2")
        file1.createNewFile()
        file2.createNewFile()
        assertThat(oldDir).exists()
        (testedWriter as DeferredWriter<Log>).deferredHandler = mockDeferredHandler

        // consume all the queued messages
        (testedWriter as DeferredWriter<Log>).invokeMethod("consumeQueue")
    }

    @Test
    fun `migrates the data from v0 to v1`() {
        val oldDir = File(tempDir, LogFileStrategy.DATA_FOLDER_ROOT)
        assertThat(oldDir).doesNotExist()
    }

    // endregion

    // region utils

    override fun waitForNextBatch() {
        Thread.sleep(RECENT_DELAY_MS * 2)
    }

    override fun minimalCopy(of: Log): Log {
        return of.copy(
            throwable = null,
            networkInfo = null,
            attributes = emptyMap(),
            tags = emptyList()
        )
    }

    override fun lightModel(forge: Forge): Log {
        return forge.getForgery<Log>().copy(
            serviceName = forge.anAlphabeticalString(size = forge.aTinyInt()),
            message = forge.anAlphabeticalString(size = forge.aTinyInt()),
            throwable = null,
            networkInfo = null,
            attributes = emptyMap(),
            tags = emptyList()
        )
    }

    override fun bigModel(forge: Forge): Log {
        return Log(
            level = android.util.Log.ASSERT,
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
            userInfo = UserInfo(), // TODO !!!
            throwable = ArrayIndexOutOfBoundsException(forge.anAlphabeticalString()),
            timestamp = forge.aLong(),
            loggerName = forge.anAlphabeticalString(),
            threadName = forge.anAlphabeticalString()
        )
    }

    override fun assertHasMatches(jsonObject: JsonObject, models: List<Log>) {
        val message = (jsonObject[LogSerializer.TAG_MESSAGE] as JsonPrimitive).asString
        val serviceName = (jsonObject[LogSerializer.TAG_SERVICE_NAME] as JsonPrimitive).asString
        val status = (jsonObject[LogSerializer.TAG_STATUS] as JsonPrimitive).asString

        val roughMatches = models.filter {
            message == it.message && serviceName == it.serviceName && status == levels[it.level]
        }

        assertThat(roughMatches).isNotEmpty()
    }

    override fun assertMatches(jsonObject: JsonObject, model: Log) {
        assertThat(jsonObject)
            .hasField(LogSerializer.TAG_MESSAGE, model.message)
            .hasField(LogSerializer.TAG_SERVICE_NAME, model.serviceName)
            .hasField(LogSerializer.TAG_STATUS, levels[model.level])
            .hasField(LogSerializer.TAG_LOGGER_NAME, model.loggerName)
            .hasField(LogSerializer.TAG_THREAD_NAME, model.threadName)

        // yyyy-mm-ddThh:mm:ss.SSSZ
        assertThat(jsonObject).hasStringFieldMatching(
            LogSerializer.TAG_DATE,
            "\\d+\\-\\d{2}\\-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"
        )

        assertNetworkInfoMatches(model, jsonObject)

        assertFieldsMatch(model, jsonObject)
        assertTagsMatch(jsonObject, model)
        assertThrowableMatches(model, jsonObject)
    }

    private fun assertNetworkInfoMatches(log: Log, jsonObject: JsonObject) {
        val info = log.networkInfo
        if (info != null) {
            assertThat(jsonObject).apply {
                hasField(LogSerializer.TAG_NETWORK_CONNECTIVITY, info.connectivity.serialized)
                if (!info.carrierName.isNullOrBlank()) {
                    hasField(LogSerializer.TAG_NETWORK_CARRIER_NAME, info.carrierName)
                } else {
                    doesNotHaveField(LogSerializer.TAG_NETWORK_CARRIER_NAME)
                }
                if (info.carrierId >= 0) {
                    hasField(LogSerializer.TAG_NETWORK_CARRIER_ID, info.carrierId)
                } else {
                    doesNotHaveField(LogSerializer.TAG_NETWORK_CARRIER_ID)
                }
            }
        } else {
            assertThat(jsonObject)
                .doesNotHaveField(LogSerializer.TAG_NETWORK_CONNECTIVITY)
                .doesNotHaveField(LogSerializer.TAG_NETWORK_CARRIER_NAME)
                .doesNotHaveField(LogSerializer.TAG_NETWORK_CARRIER_ID)
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
                    is JsonObject -> assertThat(jsonObject).hasField(it.key, value)
                    is JsonArray -> assertThat(jsonObject).hasField(it.key, value)
                    else -> assertThat(jsonObject).hasField(
                        it.key,
                        value.toString()
                    )
                }
            }
    }

    private fun assertTagsMatch(jsonObject: JsonObject, log: Log) {
        val jsonTagString = (jsonObject[LogSerializer.TAG_DATADOG_TAGS] as? JsonPrimitive)?.asString

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
                .hasField(LogSerializer.TAG_ERROR_KIND, throwable.javaClass.simpleName)
                .hasField(LogSerializer.TAG_ERROR_MESSAGE, throwable.message)
                .hasField(LogSerializer.TAG_ERROR_STACK, sw.toString())
        } else {
            assertThat(jsonObject)
                .doesNotHaveField(LogSerializer.TAG_ERROR_KIND)
                .doesNotHaveField(LogSerializer.TAG_ERROR_MESSAGE)
                .doesNotHaveField(LogSerializer.TAG_ERROR_STACK)
        }
    }

    // endregion

    companion object {
        private const val RECENT_DELAY_MS = 150L
        private const val MAX_DISK_SPACE = 16 * 32 * 1024L
        private val levels = arrayOf(
            "DEBUG", "DEBUG", "TRACE", "DEBUG", "INFO", "WARN",
            "ERROR", "CRITICAL", "DEBUG", "EMERGENCY"
        )
    }
}
