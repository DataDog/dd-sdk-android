/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.utils.NULL_MAP_VALUE
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Date
import org.assertj.core.api.Assertions
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
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(Configurator::class)
internal class LogSerializerTest {

    lateinit var testedSerializer: LogSerializer

    @BeforeEach
    fun `set up`() {
        testedSerializer = LogSerializer()
    }

    @Test
    fun `serializes full log as json`(@Forgery fakeLog: Log) {
        val serialized = testedSerializer.serialize(fakeLog)
        assertSerializedLogMatchesInputLog(serialized, fakeLog)
    }

    @Test
    fun `serializes minimal log as json`(@Forgery fakeLog: Log) {
        val minimalLog = fakeLog.copy(
            throwable = null,
            networkInfo = null,
            userInfo = UserInfo(),
            attributes = emptyMap(),
            tags = emptyList()
        )

        val serialized = testedSerializer.serialize(minimalLog)

        assertSerializedLogMatchesInputLog(serialized, minimalLog)
    }

    @Test
    fun `ignores reserved attributes`(@Forgery fakeLog: Log, forge: Forge) {
        // given
        val logWithoutAttributes = fakeLog.copy(attributes = emptyMap())
        val attributes = forge.aMap {
            anElementFrom(*LogSerializer.reservedAttributes) to forge.anAsciiString()
        }.toMap()
        val logWithReservedAttributes = fakeLog.copy(attributes = attributes)

        // when
        val serialized = testedSerializer.serialize(logWithReservedAttributes)

        // then
        assertSerializedLogMatchesInputLog(serialized, logWithoutAttributes)
    }

    @Test
    fun `ignores reserved tags keys`(@Forgery fakeLog: Log, forge: Forge) {
        // given
        val logWithoutTags = fakeLog.copy(tags = emptyList())
        val key = forge.anElementFrom("host", "device", "source", "service")
        val value = forge.aNumericalString()
        val reservedTag = "$key:$value"
        val logWithReservedTags = fakeLog.copy(tags = listOf(reservedTag))

        // when
        val serialized = testedSerializer.serialize(logWithReservedTags)

        // then
        assertSerializedLogMatchesInputLog(serialized, logWithoutTags)
    }

    @Test
    fun `serializes a log with no network info available`(@Forgery fakeLog: Log, forge: Forge) {
        // given
        val logWithoutNetworkInfo = fakeLog.copy(networkInfo = null)

        // when
        val serialized = testedSerializer.serialize(logWithoutNetworkInfo)

        // then
        assertSerializedLogMatchesInputLog(serialized, logWithoutNetworkInfo)
    }

    @Test
    fun `serializes a log with no throwable available`(@Forgery fakeLog: Log, forge: Forge) {
        // given
        val logWithoutThrowable = fakeLog.copy(throwable = null)

        // when
        val serialized = testedSerializer.serialize(logWithoutThrowable)

        // then
        assertSerializedLogMatchesInputLog(serialized, logWithoutThrowable)
    }

    // region Internal

    private fun assertSerializedLogMatchesInputLog(
        serializedObject: String,
        log: Log
    ) {
        val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
        assertThat(jsonObject)
            .hasField(LogAttributes.MESSAGE, log.message)
            .hasField(LogAttributes.SERVICE_NAME, log.serviceName)
            .hasField(LogAttributes.STATUS, levels[log.level])
            .hasField(LogAttributes.LOGGER_NAME, log.loggerName)
            .hasField(LogAttributes.LOGGER_THREAD_NAME, log.threadName)
            .hasField(LogAttributes.LOGGER_VERSION, BuildConfig.VERSION_NAME)

        // yyyy-mm-ddThh:mm:ss.SSSZ
        assertThat(jsonObject).hasStringFieldMatching(
            LogAttributes.DATE,
            "\\d+\\-\\d{2}\\-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"
        )

        assertJsonContainsNetworkInfo(jsonObject, log)
        assertJsonContainsUserInfo(jsonObject, log)

        assertJsonContainsCustomAttributes(jsonObject, log)
        assertJsonContainsCustomTags(jsonObject, log)
        assertJsonContainsThrowableInfo(jsonObject, log)
    }

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
        internal val levels = arrayOf(
            "debug", "debug", "trace", "debug", "info", "warn",
            "error", "critical", "debug", "emergency"
        )
    }
}
