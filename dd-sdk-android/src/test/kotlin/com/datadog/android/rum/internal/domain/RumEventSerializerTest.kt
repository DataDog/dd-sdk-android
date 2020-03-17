/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.log.internal.domain.LogSerializer
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
            .hasField(RumEventSerializer.TAG_DURATION, fakeResource.durationNanoSeconds)
            .hasField(RumEventSerializer.TAG_RESOURCE_KIND, fakeResource.kind.value)
            .hasField(RumEventSerializer.TAG_HTTP_URL, fakeResource.url)
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
            .hasField(RumEventSerializer.TAG_EVENT_NAME, fakeAction.name)
            .hasField(RumEventSerializer.TAG_EVENT_ID, fakeAction.id.toString())
            .hasField(RumEventSerializer.TAG_DURATION, fakeAction.durationNanoSeconds)
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
            .hasField(RumEventSerializer.TAG_RUM_DOC_VERSION, fakeView.version)
            .hasField(RumEventSerializer.TAG_VIEW_URL, fakeView.name)
            .hasField(RumEventSerializer.TAG_DURATION, fakeView.durationNanoSeconds)
            .hasField(RumEventSerializer.TAG_MEASURES_ERRORS, fakeView.measures.errorCount)
            .hasField(RumEventSerializer.TAG_MEASURES_RESOURCES, fakeView.measures.resourceCount)
            .hasField(RumEventSerializer.TAG_MEASURES_ACTIONS, fakeView.measures.userActionCount)
    }

    @Test
    fun `serializes error rum event`(
        @Forgery fakeEvent: RumEvent,
        @Forgery fakeError: RumEventData.Error
    ) {
        val event = fakeEvent.copy(eventData = fakeError)

        val serialized = underTest.serialize(event)

        val sw = StringWriter()
        fakeError.throwable.printStackTrace(PrintWriter(sw))
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertEventMatches(jsonObject, event)
        assertThat(jsonObject)
            .hasField(RumEventSerializer.TAG_MESSAGE, fakeError.message)
            .hasField(RumEventSerializer.TAG_ERROR_ORIGIN, fakeError.origin)
            .hasField(RumEventSerializer.TAG_ERROR_KIND, fakeError.throwable.javaClass.simpleName)
            .hasField(RumEventSerializer.TAG_ERROR_MESSAGE, fakeError.throwable.message)
            .hasField(RumEventSerializer.TAG_ERROR_STACK, sw.toString())
    }

    // region Internal

    private fun assertEventMatches(
        jsonObject: JsonObject,
        event: RumEvent
    ) {
        assertThat(jsonObject)
            .hasField(
                RumEventSerializer.TAG_APPLICATION_ID,
                event.context.applicationId.toString()
            )
            .hasField(RumEventSerializer.TAG_SESSION_ID, event.context.sessionId.toString())
            .hasField(RumEventSerializer.TAG_VIEW_ID, event.context.viewId.toString())
            .hasStringFieldMatching(
                LogSerializer.TAG_DATE,
                "\\d+\\-\\d{2}\\-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"
            )
            .hasField(RumEventSerializer.TAG_EVENT_CATEGORY, event.eventData.category)

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
