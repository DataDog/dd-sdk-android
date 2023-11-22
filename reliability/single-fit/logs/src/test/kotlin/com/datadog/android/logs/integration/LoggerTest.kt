/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.logs.integration

import android.util.Log
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.log.Logger
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.logs.integration.tests.elmyr.LogsIntegrationForgeConfigurator
import com.datadog.android.tests.ktx.getString
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(LogsIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoggerTest {

    private lateinit var stubSdkCore: StubSDKCore

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)

        val fakeLogsConfiguration = LogsConfiguration.Builder().build()
        Logs.enable(fakeLogsConfiguration, stubSdkCore)
    }

    @RepeatedTest(16)
    fun `M send log with defaults W Logger#log()`(
        @StringForgery fakeMessage: String,
        @IntForgery(Log.VERBOSE, 10) fakeLevel: Int
    ) {
        // Given
        val logger = Logger.Builder(stubSdkCore).build()

        // When
        logger.log(fakeLevel, fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo(LEVEL_NAMES[fakeLevel])
    }

    @Test
    fun `M send verbose log with defaults W Logger#v()`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val logger = Logger.Builder(stubSdkCore).build()

        // When
        logger.v(fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo("trace")
    }

    @Test
    fun `M send debug log with defaults W Logger#d()`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val logger = Logger.Builder(stubSdkCore).build()

        // When
        logger.d(fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo("debug")
    }

    @Test
    fun `M send info log with defaults W Logger#i()`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val logger = Logger.Builder(stubSdkCore).build()

        // When
        logger.i(fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo("info")
    }

    @Test
    fun `M send warning log with defaults W Logger#w()`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val logger = Logger.Builder(stubSdkCore).build()

        // When
        logger.w(fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo("warn")
    }

    @Test
    fun `M send error log with defaults W Logger#e()`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val logger = Logger.Builder(stubSdkCore).build()

        // When
        logger.e(fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo("error")
    }

    @Test
    fun `M send assert log with defaults W Logger#wtf()`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val logger = Logger.Builder(stubSdkCore).build()

        // When
        logger.wtf(fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo("critical")
    }

    @RepeatedTest(16)
    fun `M send log with custom service W Logger#Builder#setService() + Logger#log()`(
        @StringForgery fakeServiceName: String,
        @StringForgery fakeMessage: String,
        @IntForgery(Log.VERBOSE, 10) fakeLevel: Int
    ) {
        // Given
        val testedLogger = Logger.Builder(stubSdkCore).setService(fakeServiceName).build()

        // When
        testedLogger.log(fakeLevel, fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(fakeServiceName)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo(LEVEL_NAMES[fakeLevel])
    }

    @RepeatedTest(16)
    fun `M send log with custom logger name W Logger#Builder#setName() + Logger#log()`(
        @StringForgery fakeLoggerName: String,
        @StringForgery fakeMessage: String,
        @IntForgery(Log.VERBOSE, 10) fakeLevel: Int
    ) {
        // Given
        val testedLogger = Logger.Builder(stubSdkCore).setName(fakeLoggerName).build()

        // When
        testedLogger.log(fakeLevel, fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo(LEVEL_NAMES[fakeLevel])
        assertThat(event0.getString("logger.name")).isEqualTo(fakeLoggerName)
    }

    @RepeatedTest(16)
    fun `M send log with base user info W SDKCore#setUserInfo() + Logger#log()`(
        @StringForgery fakeMessage: String,
        @StringForgery fakeUserId: String,
        @StringForgery fakeUserName: String,
        @StringForgery fakeUserEmail: String,
        @IntForgery(Log.VERBOSE, 10) fakeLevel: Int
    ) {
        // Given
        stubSdkCore.setUserInfo(fakeUserId, fakeUserName, fakeUserEmail)
        val testedLogger = Logger.Builder(stubSdkCore).build()

        // When
        testedLogger.log(fakeLevel, fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo(LEVEL_NAMES[fakeLevel])
        assertThat(event0.getString("usr.id")).isEqualTo(fakeUserId)
        assertThat(event0.getString("usr.name")).isEqualTo(fakeUserName)
        assertThat(event0.getString("usr.email")).isEqualTo(fakeUserEmail)
    }

    @RepeatedTest(16)
    fun `M send log with custom user info W SDKCore#setUserInfo() + Logger#log()`(
        @StringForgery fakeMessage: String,
        @StringForgery fakeUserKey: String,
        @StringForgery fakeUserValue: String,
        @IntForgery(Log.VERBOSE, 10) fakeLevel: Int
    ) {
        // Given
        stubSdkCore.setUserInfo(extraInfo = mapOf(fakeUserKey to fakeUserValue))
        val testedLogger = Logger.Builder(stubSdkCore).build()

        // When
        testedLogger.log(fakeLevel, fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo(LEVEL_NAMES[fakeLevel])
        assertThat(event0.getString("usr.$fakeUserKey")).isEqualTo(fakeUserValue)
    }

    @RepeatedTest(16)
    fun `M send log with network info W Logger#Builder#setNetworkInfoEnabled(true) + Logger#log()`(
        @Forgery fakeNetworkInfo: NetworkInfo,
        @StringForgery fakeMessage: String,
        @IntForgery(Log.VERBOSE, 10) fakeLevel: Int
    ) {
        // Given
        val testedLogger = Logger.Builder(stubSdkCore).setNetworkInfoEnabled(true).build()

        // When
        stubSdkCore.stubNetworkInfo(fakeNetworkInfo)
        testedLogger.log(fakeLevel, fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo(LEVEL_NAMES[fakeLevel])
        assertThat(event0.getString("network.client.connectivity"))
            .isEqualTo(fakeNetworkInfo.connectivity.name)
        assertThat(event0.getString("network.client.downlink_kbps"))
            .isEqualTo(fakeNetworkInfo.downKbps?.toString())
        assertThat(event0.getString("network.client.uplink_kbps"))
            .isEqualTo(fakeNetworkInfo.upKbps?.toString())
    }

    @RepeatedTest(16)
    fun `M send log without network info W Logger#Builder#setNetworkInfoEnabled(false) + Logger#log()`(
        @StringForgery fakeMessage: String,
        @IntForgery(Log.VERBOSE, 10) fakeLevel: Int
    ) {
        // Given
        val testedLogger = Logger.Builder(stubSdkCore).setNetworkInfoEnabled(false).build()

        // When
        testedLogger.log(fakeLevel, fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo(LEVEL_NAMES[fakeLevel])
        assertThat(event0.getString("network.client.connectivity")).isNull()
        assertThat(event0.getString("network.client.uplink_kbps")).isNull()
        assertThat(event0.getString("network.client.downlink_kbps")).isNull()
    }

    @RepeatedTest(16)
    fun `M send log above threshold W Logger#Builder#setRemoteLogThreshold() + Logger#log()`(
        @StringForgery fakeMessage: String,
        @IntForgery(0, 9) fakeThreshold: Int,
        forge: Forge
    ) {
        // Given
        val fakeLevel = forge.anInt(fakeThreshold, 10)
        val testedLogger = Logger.Builder(stubSdkCore).setRemoteLogThreshold(fakeThreshold).build()

        // When
        testedLogger.log(fakeLevel, fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo(LEVEL_NAMES[fakeLevel])
    }

    @RepeatedTest(16)
    fun `M ignore log below threshold W Logger#Builder#setRemoteLogThreshold() + Logger#log()`(
        @StringForgery fakeMessage: String,
        @IntForgery(1, 10) fakeThreshold: Int,
        forge: Forge
    ) {
        // Given
        val fakeLevel = forge.anInt(0, fakeThreshold)
        val testedLogger = Logger.Builder(stubSdkCore).setRemoteLogThreshold(fakeThreshold).build()

        // When
        testedLogger.log(fakeLevel, fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).isEmpty()
    }

    @RepeatedTest(16)
    fun `M send sampled log W Logger#Builder#setRemoteSampleRate() + Logger#log()`(
        @StringForgery fakeMessage: String,
        @IntForgery(Log.VERBOSE, 10) fakeLevel: Int,
        @FloatForgery(0f, 100f) fakeSampleRate: Float
    ) {
        // Given
        val repeatCount = 256
        val offsetMargin = repeatCount / 10 // allow 10% margin
        val testedLogger = Logger.Builder(stubSdkCore).setRemoteSampleRate(fakeSampleRate).build()

        // When
        repeat(repeatCount) {
            testedLogger.log(fakeLevel, fakeMessage)
        }

        // Then
        val expectedCount = (repeatCount * fakeSampleRate / 100f).toInt()
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten.size).isCloseTo(expectedCount, offset(offsetMargin))
    }

    @RepeatedTest(16)
    fun `M send log with rum context W Logger#Builder#setBundleWithRumEnabled(true) + Logger#log()`(
        @StringForgery fakeRumApplicationId: String,
        @StringForgery fakeRumSessionId: String,
        @StringForgery fakeRumViewId: String,
        @StringForgery fakeRumActionId: String,
        @StringForgery fakeMessage: String,
        @IntForgery(Log.VERBOSE, 10) fakeLevel: Int
    ) {
        // Given
        val testedLogger = Logger.Builder(stubSdkCore).setBundleWithRumEnabled(true).build()
        stubSdkCore.updateFeatureContext(Feature.RUM_FEATURE_NAME) {
            it["application_id"] = fakeRumApplicationId
            it["session_id"] = fakeRumSessionId
            it["view_id"] = fakeRumViewId
            it["action_id"] = fakeRumActionId
        }

        // When
        testedLogger.log(fakeLevel, fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo(LEVEL_NAMES[fakeLevel])
        assertThat(event0.getString("application_id")).isEqualTo(fakeRumApplicationId)
        assertThat(event0.getString("session_id")).isEqualTo(fakeRumSessionId)
        assertThat(event0.getString("view.id")).isEqualTo(fakeRumViewId)
        assertThat(event0.getString("user_action.id")).isEqualTo(fakeRumActionId)
    }

    @RepeatedTest(16)
    fun `M send log with trace context W Logger#Builder#setBundleWithTraceEnabled(true) + Logger#log()`(
        @StringForgery fakeTraceId: String,
        @StringForgery fakeSpanId: String,
        @StringForgery fakeMessage: String,
        @IntForgery(Log.VERBOSE, 10) fakeLevel: Int
    ) {
        // Given
        val testedLogger = Logger.Builder(stubSdkCore).setBundleWithTraceEnabled(true).build()
        stubSdkCore.updateFeatureContext(Feature.TRACING_FEATURE_NAME) {
            it["context@${Thread.currentThread().name}"] = mapOf<String, Any>(
                "trace_id" to fakeTraceId,
                "span_id" to fakeSpanId
            )
        }

        // When
        testedLogger.log(fakeLevel, fakeMessage)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo(LEVEL_NAMES[fakeLevel])
        assertThat(event0.getString("dd.trace_id")).isEqualTo(fakeTraceId)
        assertThat(event0.getString("dd.span_id")).isEqualTo(fakeSpanId)
    }

    @RepeatedTest(16)
    fun `M send log with custom attributes info W  Logger#log()`(
        @StringForgery fakeMessage: String,
        @StringForgery fakeKey: String,
        @StringForgery fakeValue: String,
        @IntForgery(Log.VERBOSE, 10) fakeLevel: Int
    ) {
        // Given
        val testedLogger = Logger.Builder(stubSdkCore).build()

        // When
        testedLogger.log(fakeLevel, fakeMessage, null, mapOf(fakeKey to fakeValue))

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo(LEVEL_NAMES[fakeLevel])
        assertThat(event0.getString(fakeKey)).isEqualTo(fakeValue)
    }

    @RepeatedTest(16)
    fun `M send log with exception W Logger#log()`(
        @Forgery fakeException: Exception,
        @StringForgery fakeMessage: String,
        @IntForgery(Log.VERBOSE, 10) fakeLevel: Int
    ) {
        // Given
        val testedLogger = Logger.Builder(stubSdkCore).build()

        // When
        testedLogger.log(fakeLevel, fakeMessage, fakeException)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo(LEVEL_NAMES[fakeLevel])
        assertThat(event0.getString("error.kind")).isEqualTo(fakeException.javaClass.canonicalName)
        assertThat(event0.getString("error.message")).isEqualTo(fakeException.message)
        assertThat(event0.getString("error.stack"))
            .contains("\tat com.datadog.tools.unit.forge.ThrowableKt.anException")
    }

    @RepeatedTest(16)
    fun `M send log with custom exception W Logger#log()`(
        @StringForgery fakeErrorKind: String,
        @StringForgery fakeErrorMessage: String,
        @StringForgery fakeErrorStack: String,
        @StringForgery fakeMessage: String,
        @IntForgery(Log.VERBOSE, 10) fakeLevel: Int
    ) {
        // Given
        val testedLogger = Logger.Builder(stubSdkCore).build()

        // When
        testedLogger.log(fakeLevel, fakeMessage, fakeErrorKind, fakeErrorMessage, fakeErrorStack)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.LOGS_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getString("service")).isEqualTo(stubSdkCore.getDatadogContext().service)
        assertThat(event0.getString("message")).isEqualTo(fakeMessage)
        assertThat(event0.getString("status")).isEqualTo(LEVEL_NAMES[fakeLevel])
        assertThat(event0.getString("error.kind")).isEqualTo(fakeErrorKind)
        assertThat(event0.getString("error.message")).isEqualTo(fakeErrorMessage)
        assertThat(event0.getString("error.stack")).isEqualTo(fakeErrorStack)
    }

    companion object {
        val LEVEL_NAMES = listOf(
            "debug",
            "debug",
            "trace",
            "debug",
            "info",
            "warn",
            "error",
            "critical",
            "debug",
            "emergency"
        )
    }
}
