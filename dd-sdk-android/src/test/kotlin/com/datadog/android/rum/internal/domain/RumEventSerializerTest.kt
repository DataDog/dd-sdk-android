/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.event.RumEventData
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date
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
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumEventSerializerTest {

    lateinit var underTest: RumEventSerializer

    @BeforeEach
    fun `set up`() {
        underTest = RumEventSerializer()
    }

    @Test
    fun `serializes resource rum event`(
        @Forgery fakeEvent: RumEvent,
        @Forgery fakeResource: RumEventData.Resource
    ) {
        val event = fakeEvent.copy(eventData = fakeResource)

        val serialized = underTest.serialize(event)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertEventMatches(jsonObject, event)
        val timing = fakeResource.timing!!
        assertThat(jsonObject)
            .hasField(RumAttributes.DURATION, fakeResource.durationNanoSeconds)
            .hasField(RumAttributes.RESOURCE_KIND, fakeResource.kind.value)
            .hasField(RumAttributes.HTTP_URL, fakeResource.url)
            .hasField(RumAttributes.HTTP_METHOD, fakeResource.method)
            .hasField(RumAttributes.USER_NAME, event.userInfo.name)
            .hasField(RumAttributes.USER_EMAIL, event.userInfo.email)
            .hasField(RumAttributes.USER_ID, event.userInfo.id)
            .hasField(RumAttributes.RESOURCE_TIMING_DNS_START, timing.dnsStart)
            .hasField(RumAttributes.RESOURCE_TIMING_DNS_DURATION, timing.dnsDuration)
            .hasField(RumAttributes.RESOURCE_TIMING_CONNECT_START, timing.connectStart)
            .hasField(RumAttributes.RESOURCE_TIMING_CONNECT_DURATION, timing.connectDuration)
            .hasField(RumAttributes.RESOURCE_TIMING_SSL_START, timing.sslStart)
            .hasField(RumAttributes.RESOURCE_TIMING_SSL_DURATION, timing.sslDuration)
            .hasField(RumAttributes.RESOURCE_TIMING_FB_START, timing.firstByteStart)
            .hasField(RumAttributes.RESOURCE_TIMING_FB_DURATION, timing.firstByteDuration)
            .hasField(RumAttributes.RESOURCE_TIMING_DL_START, timing.downloadStart)
            .hasField(RumAttributes.RESOURCE_TIMING_DL_DURATION, timing.downloadDuration)
    }

    @Test
    fun `serializes resource rum event without timing`(
        @Forgery fakeEvent: RumEvent,
        @Forgery fakeResource: RumEventData.Resource
    ) {
        val event = fakeEvent.copy(eventData = fakeResource.copy(timing = null))

        val serialized = underTest.serialize(event)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertEventMatches(jsonObject, event)
        assertThat(jsonObject)
            .hasField(RumAttributes.DURATION, fakeResource.durationNanoSeconds)
            .hasField(RumAttributes.RESOURCE_KIND, fakeResource.kind.value)
            .hasField(RumAttributes.HTTP_URL, fakeResource.url)
            .hasField(RumAttributes.HTTP_METHOD, fakeResource.method)
            .hasField(RumAttributes.USER_NAME, event.userInfo.name)
            .hasField(RumAttributes.USER_EMAIL, event.userInfo.email)
            .hasField(RumAttributes.USER_ID, event.userInfo.id)
            .doesNotHaveField(RumAttributes.RESOURCE_TIMING_DNS_START)
            .doesNotHaveField(RumAttributes.RESOURCE_TIMING_DNS_DURATION)
            .doesNotHaveField(RumAttributes.RESOURCE_TIMING_CONNECT_START)
            .doesNotHaveField(RumAttributes.RESOURCE_TIMING_CONNECT_DURATION)
            .doesNotHaveField(RumAttributes.RESOURCE_TIMING_SSL_START)
            .doesNotHaveField(RumAttributes.RESOURCE_TIMING_SSL_DURATION)
            .doesNotHaveField(RumAttributes.RESOURCE_TIMING_FB_START)
            .doesNotHaveField(RumAttributes.RESOURCE_TIMING_FB_DURATION)
            .doesNotHaveField(RumAttributes.RESOURCE_TIMING_DL_START)
            .doesNotHaveField(RumAttributes.RESOURCE_TIMING_DL_DURATION)
    }

    @Test
    fun `serializes user action rum event`(
        @Forgery fakeEvent: RumEvent,
        @Forgery fakeAction: RumEventData.UserAction
    ) {
        val event = fakeEvent.copy(eventData = fakeAction)

        val serialized = underTest.serialize(event)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertEventMatches(jsonObject, event)
        assertThat(jsonObject)
            .hasField(RumAttributes.EVT_NAME, fakeAction.name)
            .hasField(RumAttributes.EVT_ID, fakeAction.id.toString())
            .hasField(RumAttributes.DURATION, fakeAction.durationNanoSeconds)
    }

    @Test
    fun `serializes view rum event`(
        @Forgery fakeEvent: RumEvent,
        @Forgery fakeView: RumEventData.View
    ) {
        val event = fakeEvent.copy(eventData = fakeView)

        val serialized = underTest.serialize(event)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertEventMatches(jsonObject, event)
        assertThat(jsonObject)
            .hasField(RumAttributes._DD_DOCUMENT_VERSION, fakeView.version)
            .hasField(RumAttributes.VIEW_URL, fakeView.name)
            .hasField(RumAttributes.VIEW_DURATION, fakeView.durationNanoSeconds)
            .hasField(RumAttributes.VIEW_ERROR_COUNT, fakeView.errorCount)
            .hasField(RumAttributes.VIEW_RESOURCE_COUNT, fakeView.resourceCount)
            .hasField(RumAttributes.VIEW_ACTION_COUNT, fakeView.actionCount)
    }

    @Test
    fun `serializes error rum event`(
        @Forgery fakeEvent: RumEvent,
        @Forgery fakeError: RumEventData.Error
    ) {
        val event = fakeEvent.copy(eventData = fakeError)

        val serialized = underTest.serialize(event)

        val sw = StringWriter()
        val throwable = fakeError.throwable!!
        throwable.printStackTrace(PrintWriter(sw))
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertEventMatches(jsonObject, event)
        assertThat(jsonObject)
            .hasField(RumAttributes.ERROR_MESSAGE, fakeError.message)
            .hasField(RumAttributes.ERROR_ORIGIN, fakeError.origin)
            .hasField(RumAttributes.ERROR_KIND, fakeError.throwable.javaClass.simpleName)
            .hasField(RumAttributes.ERROR_STACK, sw.toString())
    }

    @Test
    fun `if user info is missing will not be serialized`(
        @Forgery fakeEvent: RumEvent,
        @Forgery fakeResource: RumEventData.Resource
    ) {
        val event = fakeEvent.copy(eventData = fakeResource, userInfo = UserInfo())

        val serialized = underTest.serialize(event)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertEventMatches(jsonObject, event)
        assertThat(jsonObject)
            .hasField(RumAttributes.DURATION, fakeResource.durationNanoSeconds)
            .hasField(RumAttributes.RESOURCE_KIND, fakeResource.kind.value)
            .hasField(RumAttributes.HTTP_URL, fakeResource.url)
            .doesNotHaveField(RumAttributes.USER_ID)
            .doesNotHaveField(RumAttributes.USER_EMAIL)
            .doesNotHaveField(RumAttributes.USER_NAME)
    }

    @Test
    fun `serializes error rum event without throwable`(
        @Forgery fakeEvent: RumEvent,
        @Forgery fakeError: RumEventData.Error
    ) {
        val event = fakeEvent.copy(eventData = fakeError.copy(throwable = null))

        val serialized = underTest.serialize(event)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertEventMatches(jsonObject, event)
        assertThat(jsonObject)
            .hasField(RumAttributes.ERROR_MESSAGE, fakeError.message)
            .hasField(RumAttributes.ERROR_ORIGIN, fakeError.origin)
            .doesNotHaveField(RumAttributes.ERROR_KIND)
            .doesNotHaveField(RumAttributes.ERROR_STACK)
    }

    // region Internal

    private fun assertNetworkInfoMatches(event: RumEvent, jsonObject: JsonObject) {
        val info = event.networkInfo
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

    private fun assertUserInfoMatches(event: RumEvent, jsonObject: JsonObject) {
        val info = event.userInfo
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

    private fun assertEventMatches(
        jsonObject: JsonObject,
        event: RumEvent
    ) {
        assertThat(jsonObject)
            .hasField(
                RumAttributes.APPLICATION_ID,
                event.context.applicationId
            )
            .hasField(RumAttributes.SESSION_ID, event.context.sessionId)
            .hasField(RumAttributes.SESSION_TYPE, "user")
            .hasField(RumAttributes._DD_FORMAT_VERSION, 2)
            .hasField(RumAttributes.VIEW_ID, event.context.viewId)
            .hasStringFieldMatching(
                RumAttributes.DATE,
                "\\d+\\-\\d{2}\\-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"
            )
            .hasField(RumAttributes.TYPE, event.eventData.category)

        assertAttributesMatch(jsonObject, event)

        assertNetworkInfoMatches(event, jsonObject)
        assertUserInfoMatches(event, jsonObject)
    }

    private fun assertAttributesMatch(
        jsonObject: JsonObject,
        event: RumEvent
    ) {
        event.attributes
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
                    else -> assertThat(jsonObject).hasField(it.key, value.toString())
                }
            }
    }

    // endregion
}
