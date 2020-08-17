/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.file.DeferredWriter
import com.datadog.android.core.internal.domain.FilePersistenceStrategyTest
import com.datadog.android.core.internal.domain.PayloadDecoration
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.utils.NULL_MAP_VALUE
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.log.LogAttributes
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
import java.util.Date
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ForgeConfiguration(Configurator::class)
internal class LogFileStrategyTest :
    FilePersistenceStrategyTest<Log>(
        LogFileStrategy.LOGS_FOLDER,
        payloadDecoration = PayloadDecoration.JSON_ARRAY_DECORATION,
        modelClass = Log::class.java
    ) {

    // region LogStrategyTest

    @BeforeEach
    override fun `set up`(forge: Forge) {
        // add fake data into the old data directory
        val oldDir = File(tempDir, LogFileStrategy.DATA_FOLDER_ROOT)
        oldDir.mkdirs()
        val file1 = File(oldDir, "file1")
        val file2 = File(oldDir, "file2")
        file1.createNewFile()
        file2.createNewFile()
        assertThat(oldDir).exists()
        super.`set up`(forge)
    }

    override fun getStrategy(): PersistenceStrategy<Log> {
        return LogFileStrategy(
            context = mockContext,
            recentDelayMs = RECENT_DELAY_MS,
            maxBatchSize = MAX_BATCH_SIZE,
            maxLogPerBatch = MAX_MESSAGES_PER_BATCH,
            maxDiskSpace = MAX_DISK_SPACE,
            dataPersistenceExecutorService = mockExecutorService
        )
    }

    override fun setUp(writer: Writer<Log>, reader: Reader) {
        // add fake data into the old data directory
        (testedWriter as DeferredWriter<Log>).invokeMethod(
            "tryToConsumeQueue"
        ) // consume all the queued messages
    }

    override fun waitForNextBatch() {
        Thread.sleep(RECENT_DELAY_MS * 2)
    }

    override fun forgeMinimalCopy(of: Log): Log {
        return of.copy(
            throwable = null,
            networkInfo = null,
            attributes = emptyMap(),
            tags = emptyList()
        )
    }

    override fun forgeLightModel(forge: Forge): Log {
        return forge.getForgery<Log>().copy(
            serviceName = forge.anAlphabeticalString(size = forge.aTinyInt()),
            message = forge.anAlphabeticalString(size = forge.aTinyInt()),
            throwable = null,
            networkInfo = null,
            attributes = emptyMap(),
            tags = emptyList()
        )
    }

    override fun forgeHeavyModel(forge: Forge): Log {
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

    override fun assertJsonContainsModels(jsonObject: JsonObject, models: List<Log>) {
        val message = (jsonObject[LogAttributes.MESSAGE] as JsonPrimitive).asString
        val serviceName = (jsonObject[LogAttributes.SERVICE_NAME] as JsonPrimitive).asString
        val status = (jsonObject[LogAttributes.STATUS] as JsonPrimitive).asString

        val roughMatches = models.filter {
            message == it.message && serviceName == it.serviceName && status == levels[it.level]
        }

        assertThat(roughMatches).isNotEmpty()
    }

    override fun assertJsonMatchesModel(jsonObject: JsonObject, model: Log) {
        assertThat(jsonObject)
            .hasField(LogAttributes.MESSAGE, model.message)
            .hasField(LogAttributes.SERVICE_NAME, model.serviceName)
            .hasField(LogAttributes.STATUS, levels[model.level])
            .hasField(LogAttributes.LOGGER_NAME, model.loggerName)
            .hasField(LogAttributes.LOGGER_THREAD_NAME, model.threadName)

        // yyyy-mm-ddThh:mm:ss.SSSZ
        assertThat(jsonObject).hasStringFieldMatching(
            LogAttributes.DATE,
            "\\d+\\-\\d{2}\\-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"
        )

        assertJsonContainsNetworkInfo(jsonObject, model)

        assertJsonContainsCustomAttributes(jsonObject, model)
        assertJsonContainsCustomTags(jsonObject, model)
        assertJsonContainsThrowableInfo(jsonObject, model)
    }

    // endregion

    @Test
    fun `migrates the data from v0 to v1`() {
        val oldDir = File(tempDir, LogFileStrategy.DATA_FOLDER_ROOT)
        assertThat(oldDir).doesNotExist()
    }

    // region Internal

    private fun assertJsonContainsNetworkInfo(
        jsonObject: JsonObject,
        log: Log
    ) {
        val info = log.networkInfo
        if (info != null) {
            assertThat(jsonObject).apply {
                hasField(LogAttributes.NETWORK_CONNECTIVITY, info.connectivity.serialized)
                if (!info.carrierName.isNullOrBlank()) {
                    hasField(LogAttributes.NETWORK_CARRIER_NAME, info.carrierName)
                } else {
                    doesNotHaveField(LogAttributes.NETWORK_CARRIER_NAME)
                }
                if (info.carrierId >= 0) {
                    hasField(LogAttributes.NETWORK_CARRIER_ID, info.carrierId)
                } else {
                    doesNotHaveField(LogAttributes.NETWORK_CARRIER_ID)
                }
                if (info.upKbps >= 0) {
                    hasField(LogAttributes.NETWORK_UP_KBPS, info.upKbps)
                } else {
                    doesNotHaveField(LogAttributes.NETWORK_UP_KBPS)
                }
                if (info.downKbps >= 0) {
                    hasField(LogAttributes.NETWORK_DOWN_KBPS, info.downKbps)
                } else {
                    doesNotHaveField(LogAttributes.NETWORK_DOWN_KBPS)
                }
                if (info.strength > Int.MIN_VALUE) {
                    hasField(LogAttributes.NETWORK_SIGNAL_STRENGTH, info.strength)
                } else {
                    doesNotHaveField(LogAttributes.NETWORK_SIGNAL_STRENGTH)
                }
            }
        } else {
            assertThat(jsonObject)
                .doesNotHaveField(LogAttributes.NETWORK_CONNECTIVITY)
                .doesNotHaveField(LogAttributes.NETWORK_CARRIER_NAME)
                .doesNotHaveField(LogAttributes.NETWORK_CARRIER_ID)
        }
    }

    private fun assertJsonContainsCustomAttributes(
        jsonObject: JsonObject,
        log: Log
    ) {
        log.attributes
            .filter { it.key.isNotBlank() }
            .forEach {
                val value = it.value
                when (value) {
                    NULL_MAP_VALUE -> assertThat(jsonObject).hasNullField(it.key)
                    is Boolean -> assertThat(jsonObject).hasField(it.key, value)
                    is Int -> assertThat(jsonObject).hasField(it.key, value)
                    is Long -> assertThat(jsonObject).hasField(it.key, value)
                    is Float -> assertThat(jsonObject).hasField(it.key, value)
                    is Double -> assertThat(jsonObject).hasField(it.key, value)
                    is String -> assertThat(jsonObject).hasField(it.key, value)
                    is Date -> assertThat(jsonObject).hasField(it.key, value.time)
                    is JsonObject -> assertThat(jsonObject).hasField(it.key, value)
                    is JsonArray -> assertThat(jsonObject).hasField(it.key, value)
                    else -> assertThat(jsonObject).hasField(it.key, value.toString())
                }
            }
    }

    private fun assertJsonContainsCustomTags(
        jsonObject: JsonObject,
        log: Log
    ) {
        val jsonTagString = (jsonObject[LogSerializer.TAG_DATADOG_TAGS] as? JsonPrimitive)?.asString

        if (jsonTagString.isNullOrBlank()) {
            Assertions.assertThat(log.tags)
                .isEmpty()
        } else {
            val tags = jsonTagString
                .split(',')
                .toList()

            Assertions.assertThat(tags)
                .containsExactlyInAnyOrder(*log.tags.toTypedArray())
        }
    }

    private fun assertJsonContainsThrowableInfo(
        jsonObject: JsonObject,
        log: Log
    ) {
        val throwable = log.throwable
        if (throwable != null) {
            assertThat(jsonObject)
                .hasField(LogAttributes.ERROR_KIND, throwable.javaClass.simpleName)
                .hasNullableField(LogAttributes.ERROR_MESSAGE, throwable.message)
                .hasField(LogAttributes.ERROR_STACK, throwable.loggableStackTrace())
        } else {
            assertThat(jsonObject)
                .doesNotHaveField(LogAttributes.ERROR_KIND)
                .doesNotHaveField(LogAttributes.ERROR_MESSAGE)
                .doesNotHaveField(LogAttributes.ERROR_STACK)
        }
    }

    private fun assertJsonContainsUserInfo(
        jsonObject: JsonObject,
        log: Log
    ) {
        val info = log.userInfo
        assertThat(jsonObject).apply {
            if (info.id.isNullOrEmpty()) {
                doesNotHaveField(LogAttributes.USR_ID)
            } else {
                hasField(LogAttributes.USR_ID, info.id)
            }
            if (info.name.isNullOrEmpty()) {
                doesNotHaveField(LogAttributes.USR_NAME)
            } else {
                hasField(LogAttributes.USR_NAME, info.name)
            }
            if (info.email.isNullOrEmpty()) {
                doesNotHaveField(LogAttributes.USR_EMAIL)
            } else {
                hasField(LogAttributes.USR_EMAIL, info.email)
            }
        }
    }

    // endregion

    companion object {
        private const val RECENT_DELAY_MS = 150L
        private const val MAX_DISK_SPACE = 16 * 32 * 1024L
        private val levels = arrayOf(
            "debug", "debug", "trace", "debug", "info", "warn",
            "error", "critical", "debug", "emergency"
        )
    }
}
