/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.constraints.DataConstraints
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.assertj.containsExtraAttributes
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
        // Given
        val logWithoutAttributes = fakeLog.copy(attributes = emptyMap())
        val attributes = forge.aMap {
            anElementFrom(*LogSerializer.reservedAttributes) to forge.anAsciiString()
        }.toMap()
        val logWithReservedAttributes = fakeLog.copy(attributes = attributes)

        // When
        val serialized = testedSerializer.serialize(logWithReservedAttributes)

        // Then
        assertSerializedLogMatchesInputLog(serialized, logWithoutAttributes)
    }

    @Test
    fun `ignores reserved tags keys`(@Forgery fakeLog: Log, forge: Forge) {
        // Given
        val logWithoutTags = fakeLog.copy(tags = emptyList())
        val key = forge.anElementFrom("host", "device", "source", "service")
        val value = forge.aNumericalString()
        val reservedTag = "$key:$value"
        val logWithReservedTags = fakeLog.copy(tags = listOf(reservedTag))

        // When
        val serialized = testedSerializer.serialize(logWithReservedTags)

        // Then
        assertSerializedLogMatchesInputLog(serialized, logWithoutTags)
    }

    @Test
    fun `serializes a log with no network info available`(@Forgery fakeLog: Log, forge: Forge) {
        // Given
        val logWithoutNetworkInfo = fakeLog.copy(networkInfo = null)

        // When
        val serialized = testedSerializer.serialize(logWithoutNetworkInfo)

        // Then
        assertSerializedLogMatchesInputLog(serialized, logWithoutNetworkInfo)
    }

    @Test
    fun `serializes a log with no throwable available`(@Forgery fakeLog: Log, forge: Forge) {
        // Given
        val logWithoutThrowable = fakeLog.copy(throwable = null)

        // When
        val serialized = testedSerializer.serialize(logWithoutThrowable)

        // Then
        assertSerializedLogMatchesInputLog(serialized, logWithoutThrowable)
    }

    @Test
    fun `M sanitise the user extra info keys W level deeper than 8`(
        @Forgery fakeLog: Log,
        forge: Forge
    ) {
        // GIVEN
        val fakeBadKey =
            forge.aList(size = 10) { forge.anAlphabeticalString() }.joinToString(".")
        val lastDotIndex = fakeBadKey.lastIndexOf('.')
        val expectedSanitisedKey =
            fakeBadKey.replaceRange(lastDotIndex..lastDotIndex, "_")
        val attributeValue = forge.anAlphabeticalString()
        val fakeUserInfo = fakeLog.userInfo.copy(extraInfo = mapOf(fakeBadKey to attributeValue))

        // WHEN
        val serializedEvent = testedSerializer.serialize(fakeLog.copy(userInfo = fakeUserInfo))
        val jsonObject = JsonParser.parseString(serializedEvent).asJsonObject

        // THEN
        assertThat(jsonObject)
            .hasField(
                "${LogAttributes.USR_ATTRIBUTES_GROUP}.$expectedSanitisedKey",
                attributeValue
            )
        assertThat(jsonObject)
            .doesNotHaveField("${LogAttributes.USR_ATTRIBUTES_GROUP}.$fakeBadKey")
    }

    @Test
    fun `M use the attributes group verbose name W validateAttributes { user extra info }`(
        @Forgery fakeLog: Log,
        forge: Forge
    ) {
        // GIVEN
        val mockedDataConstrains: DataConstraints = mock()
        testedSerializer = LogSerializer(mockedDataConstrains)

        // WHEN
        val serializedEvent = testedSerializer.serialize(fakeLog)
        val jsonObject = JsonParser.parseString(serializedEvent).asJsonObject

        // THEN
        verify(mockedDataConstrains).validateAttributes(
            any(),
            eq(LogAttributes.USR_ATTRIBUTES_GROUP),
            eq(LogSerializer.USER_EXTRA_GROUP_VERBOSE_NAME)
        )
    }

    @Test
    fun `M use the simple name as error kind W serialize { canonical name is null }`(
        @Forgery fakeLog: Log,
        forge: Forge
    ) {

        // GIVEN
        class CustomThrowable : Throwable()

        val fakeThrowable = CustomThrowable()
        val fakeLogWithLocalThrowable = fakeLog.copy(throwable = fakeThrowable)

        // WHEN
        val serializedEvent = testedSerializer.serialize(fakeLogWithLocalThrowable)
        val jsonObject = JsonParser.parseString(serializedEvent).asJsonObject

        // THEN
        assertThat(jsonObject)
            .hasField(
                LogAttributes.ERROR_KIND,
                fakeThrowable.javaClass.simpleName
            )
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
        assertThat(jsonObject)
            .hasStringFieldMatching(
                LogAttributes.DATE,
                "\\d+\\-\\d{2}\\-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"
            )
            .containsExtraAttributes(log.attributes)

        assertJsonContainsNetworkInfo(jsonObject, log)
        assertJsonContainsUserInfo(jsonObject, log)
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
                hasField(LogAttributes.NETWORK_CONNECTIVITY, info.connectivity.toJson().asString)
                if (!info.carrierName.isNullOrBlank()) {
                    hasField(LogAttributes.NETWORK_CARRIER_NAME, info.carrierName!!)
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
            val expectedErrorKind =
                throwable.javaClass.canonicalName ?: throwable.javaClass.simpleName
            assertThat(jsonObject)
                .hasField(LogAttributes.ERROR_KIND, expectedErrorKind)
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
            containsExtraAttributes(info.extraInfo, LogAttributes.USR_ATTRIBUTES_GROUP + ".")
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
