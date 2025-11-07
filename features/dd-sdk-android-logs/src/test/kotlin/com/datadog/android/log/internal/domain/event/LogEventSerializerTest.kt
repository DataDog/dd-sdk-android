/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain.event

import com.datadog.android.api.InternalLogger
import com.datadog.android.log.assertj.containsExtraAttributes
import com.datadog.android.log.model.LogEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.assertj.JsonObjectAssert
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.datadog.tools.unit.forge.anException
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(Configurator::class)
internal class LogEventSerializerTest {

    private lateinit var testedSerializer: LogEventSerializer

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedSerializer = LogEventSerializer(mockInternalLogger)
    }

    @RepeatedTest(4)
    fun `serializes full log as json`(@Forgery fakeLog: LogEvent) {
        val serialized = testedSerializer.serialize(fakeLog)
        assertSerializedLogMatchesInputLog(serialized, fakeLog)
    }

    @Test
    fun `ignores reserved attributes`(@Forgery fakeLog: LogEvent, forge: Forge) {
        // Given
        val logWithoutAttributes = fakeLog.copy(additionalProperties = mutableMapOf())
        val attributes = forge.aMap<String, Any?> {
            anElementFrom(LogEvent.RESERVED_PROPERTIES.asList()) to forge.anAsciiString()
        }.toMutableMap()
        val logWithReservedAttributes = fakeLog.copy(additionalProperties = attributes)

        // When
        val serialized = testedSerializer.serialize(logWithReservedAttributes)

        // Then
        assertSerializedLogMatchesInputLog(serialized, logWithoutAttributes)
    }

    @Test
    fun `ignores reserved tags keys`(@Forgery fakeLog: LogEvent, forge: Forge) {
        // Given
        val logWithoutTags = fakeLog.copy(ddtags = "")
        val key = forge.anElementFrom("host", "device", "source", "service")
        val value = forge.aNumericalString()
        val reservedTag = "$key:$value"
        val logWithReservedTags = fakeLog.copy(ddtags = reservedTag)

        // When
        val serialized = testedSerializer.serialize(logWithReservedTags)

        // Then
        assertSerializedLogMatchesInputLog(serialized, logWithoutTags)
    }

    @Test
    fun `M sanitise the user extra info keys W level deeper than 8`(
        @Forgery fakeLog: LogEvent,
        forge: Forge
    ) {
        // GIVEN
        // we generate the bad key with depth level = 9 as the `usr` prefix will add 1 extra depth
        val fakeBadKey =
            forge.aList(size = 10) { forge.anAlphabeticalString() }.joinToString(".")
        val lastDotIndex = fakeBadKey.lastIndexOf('.')
        val expectedSanitisedKey =
            fakeBadKey.replaceRange(lastDotIndex..lastDotIndex, "_")
        val attributeValue = forge.anAlphabeticalString()
        val fakeUserInfo = LogEvent.Usr(
            additionalProperties = mutableMapOf(
                fakeBadKey to attributeValue
            )
        )

        // WHEN
        val newFakeLog = fakeLog.copy(usr = fakeUserInfo)
        val serializedEvent = testedSerializer.serialize(newFakeLog)
        val jsonObject = JsonParser.parseString(serializedEvent).asJsonObject

        // THEN
        assertThat(jsonObject)
            .hasField(KEY_USR) {
                hasField(
                    expectedSanitisedKey,
                    attributeValue
                )
                doesNotHaveField(fakeBadKey)
            }
    }

    @Test
    fun `M sanitise the account extra info keys W level deeper than 8`(
        @Forgery fakeLog: LogEvent,
        forge: Forge
    ) {
        // GIVEN
        // we generate the bad key with depth level = 9 as the `account` prefix will add 1 extra depth
        val fakeBadKey =
            forge.aList(size = 10) { forge.anAlphabeticalString() }.joinToString(".")
        val lastDotIndex = fakeBadKey.lastIndexOf('.')
        val expectedSanitisedKey =
            fakeBadKey.replaceRange(lastDotIndex..lastDotIndex, "_")
        val attributeValue = forge.anAlphabeticalString()
        val fakeAccountInfo = LogEvent.Account(
            additionalProperties = mutableMapOf(
                fakeBadKey to attributeValue
            )
        )

        // WHEN
        val newFakeLog = fakeLog.copy(account = fakeAccountInfo)
        val serializedEvent = testedSerializer.serialize(newFakeLog)
        val jsonObject = JsonParser.parseString(serializedEvent).asJsonObject

        // THEN
        assertThat(jsonObject)
            .hasField(KEY_ACCOUNT) {
                hasField(
                    expectedSanitisedKey,
                    attributeValue
                )
                doesNotHaveField(fakeBadKey)
            }
    }

    @Test
    fun `M not throw W serialize() { usr#additionalProperties serialization throws }`(
        @Forgery fakeLog: LogEvent,
        forge: Forge
    ) {
        // Given
        val faultyKey = forge.anAlphabeticalString()
        val faultyObject = object {
            override fun toString(): String {
                throw forge.anException()
            }
        }
        val faultyLogEvent = fakeLog.copy(
            usr = fakeLog.usr?.copy(
                additionalProperties = fakeLog.usr?.additionalProperties
                    ?.toMutableMap()
                    ?.apply { put(faultyKey, faultyObject) }
                    .orEmpty()
                    .toMutableMap()
            )
        )

        // When
        val serialized = testedSerializer.serialize(faultyLogEvent)

        // Then
        assertSerializedLogMatchesInputLog(serialized, fakeLog)
    }

    @Test
    fun `M not throw W serialize() { account#additionalProperties serialization throws }`(
        @Forgery fakeLog: LogEvent,
        forge: Forge
    ) {
        // Given
        val faultyKey = forge.anAlphabeticalString()
        val faultyObject = object {
            override fun toString(): String {
                throw forge.anException()
            }
        }
        val faultyLogEvent = fakeLog.copy(
            account = fakeLog.account?.copy(
                additionalProperties = fakeLog.account?.additionalProperties
                    ?.toMutableMap()
                    ?.apply { put(faultyKey, faultyObject) }
                    .orEmpty()
                    .toMutableMap()
            )
        )

        // When
        val serialized = testedSerializer.serialize(faultyLogEvent)

        // Then
        assertSerializedLogMatchesInputLog(serialized, fakeLog)
    }

    @Test
    fun `M not throw W serialize() { additionalProperties serialization throws }`(
        @Forgery fakeLog: LogEvent,
        forge: Forge
    ) {
        // Given
        val faultyKey = forge.anAlphabeticalString()
        val faultyObject = object {
            override fun toString(): String {
                throw forge.anException()
            }
        }
        val faultyLogEvent = fakeLog.copy(
            additionalProperties = fakeLog.additionalProperties
                .toMutableMap()
                .apply { put(faultyKey, faultyObject) }
        )

        // When
        val serialized = testedSerializer.serialize(faultyLogEvent)

        // Then
        assertSerializedLogMatchesInputLog(serialized, fakeLog)
    }

    // region Internal

    private fun assertSerializedLogMatchesInputLog(
        serializedObject: String,
        log: LogEvent
    ) {
        val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
        assertThat(jsonObject)
            .hasField(KEY_MESSAGE, log.message)
            .hasField(KEY_SERVICE_NAME, log.service)
            .hasField(KEY_DATE, log.date)
            .hasField(KEY_TAGS, log.ddtags)
        log.status.let {
            assertThat(jsonObject).hasField(KEY_STATUS, it.toJson())
        }
        log.logger.let {
            assertThat(jsonObject).hasField(KEY_LOGGER) {
                hasLoggerInfo(it)
            }
        }
        log.network?.let {
            assertThat(jsonObject).hasField(KEY_NETWORK) {
                hasField(KEY_CLIENT) {
                    hasNetworkInfo(it)
                }
            }
        }
        log.usr?.let {
            assertThat(jsonObject).hasField(KEY_USR) {
                hasUserInfo(it)
            }
        }
        log.account?.let {
            assertThat(jsonObject).hasField(KEY_ACCOUNT) {
                hasAccountInfo(it)
            }
        }
        log.error?.let {
            assertThat(jsonObject).hasField(KEY_ERROR) {
                hasErrorInfo(it)
            }
        }
        assertThat(jsonObject).hasField(KEY_OS) {
            hasOsInfo(log.os)
        }
        assertThat(jsonObject).hasField(KEY_DEVICE) {
            hasDeviceInfo(log.device)
        }
    }

    private fun JsonObjectAssert.hasNetworkInfo(
        network: LogEvent.Network
    ) {
        hasField(
            KEY_NETWORK_CONNECTIVITY,
            network.client.connectivity
        )
        val simCarrier = network.client.simCarrier
        if (simCarrier != null) {
            hasField(KEY_SIM_CARRIER) {
                val simCarrierName = simCarrier.name
                if (simCarrierName != null) {
                    hasField(KEY_NETWORK_CARRIER_NAME, simCarrierName)
                } else {
                    doesNotHaveField(KEY_NETWORK_CARRIER_NAME)
                }
                val simCarrierId = simCarrier.id
                if (simCarrierId != null) {
                    hasField(
                        KEY_NETWORK_CARRIER_ID,
                        simCarrierId
                    )
                } else {
                    doesNotHaveField(KEY_NETWORK_CARRIER_ID)
                }
            }
        } else {
            doesNotHaveField(KEY_SIM_CARRIER)
        }
        val uplinkKbps = network.client.uplinkKbps
        if (uplinkKbps != null) {
            hasField(KEY_NETWORK_UP_KBPS, uplinkKbps)
        } else {
            doesNotHaveField(KEY_NETWORK_UP_KBPS)
        }
        val downlinkKbps = network.client.downlinkKbps
        if (downlinkKbps != null) {
            hasField(
                KEY_NETWORK_DOWN_KBPS,
                downlinkKbps
            )
        } else {
            doesNotHaveField(KEY_NETWORK_DOWN_KBPS)
        }
        val signalStrength = network.client.signalStrength
        if (signalStrength != null) {
            hasField(
                KEY_NETWORK_SIGNAL_STRENGTH,
                signalStrength
            )
        } else {
            doesNotHaveField(KEY_NETWORK_SIGNAL_STRENGTH)
        }
    }

    private fun JsonObjectAssert.hasUserInfo(
        userInfo: LogEvent.Usr
    ) {
        val userName = userInfo.name
        val userEmail = userInfo.email
        val userId = userInfo.id
        val anonymousId = userInfo.anonymousId
        if (anonymousId != null) {
            hasField(KEY_USR_ANONYMOUS_ID, anonymousId)
        } else {
            doesNotHaveField(KEY_USR_ANONYMOUS_ID)
        }
        if (userId != null) {
            hasField(KEY_USR_ID, userId)
        } else {
            doesNotHaveField(KEY_USR_ID)
        }
        if (userName != null) {
            hasField(KEY_USR_NAME, userName)
        } else {
            doesNotHaveField(KEY_USR_NAME)
        }
        if (userEmail != null) {
            hasField(KEY_USR_EMAIL, userEmail)
        } else {
            doesNotHaveField(KEY_USR_EMAIL)
        }
        containsExtraAttributes(
            userInfo.additionalProperties.minus(LogEvent.Usr.RESERVED_PROPERTIES)
        )
    }

    private fun JsonObjectAssert.hasAccountInfo(
        accountInfo: LogEvent.Account
    ) {
        val accountName = accountInfo.name
        val accountId = accountInfo.id
        if (accountId != null) {
            hasField(KEY_ACCOUNT_ID, accountId)
        } else {
            doesNotHaveField(KEY_ACCOUNT_ID)
        }
        if (accountName != null) {
            hasField(KEY_ACCOUNT_NAME, accountName)
        } else {
            doesNotHaveField(KEY_ACCOUNT_NAME)
        }
        containsExtraAttributes(
            accountInfo.additionalProperties.minus(LogEvent.Account.RESERVED_PROPERTIES)
        )
    }

    private fun JsonObjectAssert.hasLoggerInfo(loggerInfo: LogEvent.Logger) {
        val loggerName = loggerInfo.name
        val threadName = loggerInfo.threadName
        val sdkVersion = loggerInfo.version
        hasField(KEY_NAME, loggerName)
        if (threadName != null) {
            hasField(KEY_THREAD_NAME, threadName)
        } else {
            doesNotHaveField(KEY_THREAD_NAME)
        }
        hasNullableField(KEY_VERSION, sdkVersion)
    }

    private fun JsonObjectAssert.hasErrorInfo(errorInfo: LogEvent.Error) {
        val errorMessage = errorInfo.message
        val errorKind = errorInfo.kind
        val errorStack = errorInfo.stack
        val errorSourceType = errorInfo.sourceType
        if (errorMessage != null) {
            hasField(KEY_MESSAGE, errorMessage)
        } else {
            doesNotHaveField(KEY_MESSAGE)
        }
        if (errorKind != null) {
            hasField(KEY_KIND, errorKind)
        } else {
            doesNotHaveField(KEY_KIND)
        }
        if (errorStack != null) {
            hasField(KEY_STACK, errorStack)
        } else {
            doesNotHaveField(KEY_STACK)
        }
        if (errorSourceType != null) {
            hasField(KEY_SOURCE_TYPE, errorSourceType)
        } else {
            doesNotHaveField(KEY_SOURCE_TYPE)
        }
    }

    private fun JsonObjectAssert.hasOsInfo(osInfo: LogEvent.Os) {
        val osName = osInfo.name
        val osBuild = osInfo.build
        val osVersion = osInfo.version
        val osVersionMajor = osInfo.versionMajor
        hasField(KEY_NAME, osName)
        if (osBuild != null) {
            hasField(KEY_BUILD, osBuild)
        } else {
            doesNotHaveField(KEY_BUILD)
        }
        hasField(KEY_VERSION, osVersion)
        hasField(KEY_VERSION_MAJOR, osVersionMajor)
    }

    private fun JsonObjectAssert.hasDeviceInfo(deviceInfo: LogEvent.LogEventDevice) {
        val deviceType = deviceInfo.type
        val deviceName = deviceInfo.name
        val deviceModel = deviceInfo.model
        val deviceBrand = deviceInfo.brand
        val deviceArhitecture = deviceInfo.architecture
        val deviceLocale = deviceInfo.locale
        val deviceLocales = deviceInfo.locales
        val deviceTimezone = deviceInfo.timeZone
        val deviceBatteryLevel = deviceInfo.batteryLevel
        val devicePowerSavingMode = deviceInfo.powerSavingMode
        val deviceBrightnessLevel = deviceInfo.brightnessLevel
        if (deviceType != null) {
            hasField(KEY_TYPE, deviceType.name.lowercase(Locale.US))
        } else {
            doesNotHaveField(KEY_TYPE)
        }
        if (deviceName != null) {
            hasField(KEY_NAME, deviceName)
        } else {
            doesNotHaveField(KEY_NAME)
        }
        if (deviceModel != null) {
            hasField(KEY_MODEL, deviceModel)
        } else {
            doesNotHaveField(KEY_MODEL)
        }
        if (deviceBrand != null) {
            hasField(KEY_BRAND, deviceBrand)
        } else {
            doesNotHaveField(KEY_BRAND)
        }
        if (deviceArhitecture != null) {
            hasField(KEY_ARCHITECTURE, deviceArhitecture)
        } else {
            doesNotHaveField(KEY_ARCHITECTURE)
        }
        if (deviceLocale != null) {
            hasField(KEY_LOCALE, deviceLocale)
        } else {
            doesNotHaveField(KEY_LOCALE)
        }
        if (deviceLocales != null) {
            hasField(KEY_LOCALES, deviceLocales)
        } else {
            doesNotHaveField(KEY_LOCALES)
        }
        if (deviceTimezone != null) {
            hasField(KEY_TIME_ZONE, deviceTimezone)
        } else {
            doesNotHaveField(KEY_TIME_ZONE)
        }
        if (deviceBatteryLevel != null) {
            hasField(KEY_BATTERY_LEVEL, deviceBatteryLevel)
        } else {
            doesNotHaveField(KEY_BATTERY_LEVEL)
        }
        if (devicePowerSavingMode != null) {
            hasField(KEY_POWER_SAVING_MODE, devicePowerSavingMode)
        } else {
            doesNotHaveField(KEY_POWER_SAVING_MODE)
        }
        if (deviceBrightnessLevel != null) {
            hasField(KEY_BRIGHTNESS_LEVEL, deviceBrightnessLevel)
        } else {
            doesNotHaveField(KEY_BRIGHTNESS_LEVEL)
        }
    }

    // endregion

    companion object {

        private const val KEY_SERVICE_NAME = "service"
        private const val KEY_NAME = "name"
        private const val KEY_DATE = "date"
        private const val KEY_MESSAGE = "message"
        private const val KEY_KIND = "kind"
        private const val KEY_STACK = "stack"
        private const val KEY_SOURCE_TYPE = "source_type"
        private const val KEY_ERROR = "error"
        private const val KEY_VERSION = "version"
        private const val KEY_THREAD_NAME = "thread_name"
        private const val KEY_STATUS = "status"
        private const val KEY_TAGS = "ddtags"
        private const val KEY_SIM_CARRIER = "sim_carrier"
        private const val KEY_NETWORK_CARRIER_ID: String = "id"
        private const val KEY_NETWORK_CARRIER_NAME: String = "name"
        private const val KEY_NETWORK_CONNECTIVITY: String = "connectivity"
        private const val KEY_NETWORK_DOWN_KBPS: String = "downlink_kbps"
        private const val KEY_NETWORK_SIGNAL_STRENGTH: String = "signal_strength"
        private const val KEY_NETWORK_UP_KBPS: String = "uplink_kbps"
        private const val KEY_USR = "usr"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_NETWORK = "network"
        private const val KEY_CLIENT = "client"
        private const val KEY_USR_NAME = "name"
        private const val KEY_USR_EMAIL = "email"
        private const val KEY_USR_ANONYMOUS_ID = "anonymous_id"
        private const val KEY_USR_ID = "id"
        private const val KEY_ACCOUNT_NAME = "name"
        private const val KEY_ACCOUNT_ID = "id"
        private const val KEY_LOGGER = "logger"
        private const val KEY_BUILD = "build"
        private const val KEY_VERSION_MAJOR = "version_major"
        private const val KEY_OS = "os"
        private const val KEY_DEVICE = "device"
        private const val KEY_TYPE = "type"
        private const val KEY_MODEL = "model"
        private const val KEY_BRAND = "brand"
        private const val KEY_ARCHITECTURE = "architecture"
        private const val KEY_LOCALE = "locale"
        private const val KEY_LOCALES = "locales"
        private const val KEY_TIME_ZONE = "time_zone"
        private const val KEY_BATTERY_LEVEL = "battery_level"
        private const val KEY_POWER_SAVING_MODE = "power_saving_mode"
        private const val KEY_BRIGHTNESS_LEVEL = "brightness_level"
    }
}
