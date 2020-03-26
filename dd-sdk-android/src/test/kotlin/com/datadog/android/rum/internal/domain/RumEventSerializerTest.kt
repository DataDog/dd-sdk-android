/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.rum.RumAttributes
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
        assertThat(jsonObject)
            .hasField(RumAttributes.DURATION, fakeResource.durationNanoSeconds)
            .hasField(RumAttributes.RESOURCE_KIND, fakeResource.kind.value)
            .hasField(RumAttributes.HTTP_URL, fakeResource.url)
            .hasField(RumAttributes.USER_NAME, event.userInfo.name)
            .hasField(RumAttributes.USER_EMAIL, event.userInfo.email)
            .hasField(RumAttributes.USER_ID, event.userInfo.id)
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
        val measures = fakeView.measures
        assertThat(jsonObject)
            .hasField(RumAttributes.RUM_DOCUMENT_VERSION, fakeView.version)
            .hasField(RumAttributes.VIEW_URL, fakeView.name)
            .hasField(RumAttributes.DURATION, fakeView.durationNanoSeconds)
            .hasField(RumAttributes.VIEW_MEASURES_ERROR_COUNT, fakeView.measures.errorCount)
            .hasField(RumAttributes.VIEW_MEASURES_RESOURCE_COUNT, fakeView.measures.resourceCount)
            .hasField(RumAttributes.VIEW_MEASURES_USER_ACTION_COUNT, measures.userActionCount)
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

    private fun assertEventMatches(
        jsonObject: JsonObject,
        event: RumEvent
    ) {
        assertThat(jsonObject)
            .hasField(
                RumAttributes.APPLICATION_ID,
                event.context.applicationId.toString()
            )
            .hasField(RumAttributes.SESSION_ID, event.context.sessionId.toString())
            .hasField(RumAttributes.VIEW_ID, event.context.viewId.toString())
            .hasStringFieldMatching(
                RumAttributes.DATE,
                "\\d+\\-\\d{2}\\-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"
            )
            .hasField(RumAttributes.EVT_CATEGORY, event.eventData.category)

        assertAttributesMatch(jsonObject, event)
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
