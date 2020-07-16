/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.internal.domain.event.toJsonArray
import com.datadog.android.rum.internal.domain.model.ActionEvent
import com.datadog.android.rum.internal.domain.model.ErrorEvent
import com.datadog.android.rum.internal.domain.model.ResourceEvent
import com.datadog.android.rum.internal.domain.model.ViewEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
    fun `serializes event`(
        @Forgery fakeEvent: RumEvent,
        @Forgery event: ResourceEvent
    ) {
        val rumEvent = fakeEvent.copy(event = event)

        val serialized = underTest.serialize(rumEvent)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertEventMatches(jsonObject, rumEvent)
        assertThat(jsonObject)
            .hasField("type", "resource")
            .hasField("date", event.date)
            .hasField("resource") {
                hasField("type", event.resource.type.name.toLowerCase())
                hasField("url", event.resource.url)
                hasField("duration", event.resource.duration)
                hasNullableField("method", event.resource.method?.name)
                hasNullableField("status_code", event.resource.statusCode)
                hasNullableField("size", event.resource.size)
                // TODO timing ?
            }
            .hasField("application") {
                hasField("id", event.application.id)
            }
            .hasField("session") {
                hasField("id", event.session.id)
                hasField("type", event.session.type.name.toLowerCase())
            }
            .hasField("view") {
                hasField("id", event.view.id)
                hasField("url", event.view.url)
            }
            .hasField("usr") {
                hasNullableField("id", event.usr?.id)
                hasNullableField("name", event.usr?.name)
                hasNullableField("email", event.usr?.email)
            }
            .hasField("_dd") {
                hasField("format_version", 2L)
            }
    }

    @Test
    fun `serializes user action rum event`(
        @Forgery fakeEvent: RumEvent,
        @Forgery event: ActionEvent
    ) {
        val rumEvent = fakeEvent.copy(event = event)

        val serialized = underTest.serialize(rumEvent)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertEventMatches(jsonObject, rumEvent)
        assertThat(jsonObject)
            .hasField("type", "action")
            .hasField("date", event.date)
            .hasField("action") {
                hasField("type", event.action.type.name.toLowerCase())
                hasNullableField("id", event.action.id)
                event.action.target?.let {
                    hasField("target") {
                        hasField("name", it.name)
                    }
                }
                event.action.resource?.let {
                    hasField("resource") {
                        hasField("count", it.count)
                    }
                }
                event.action.error?.let {
                    hasField("error") {
                        hasField("count", it.count)
                    }
                }
                event.action.longTask?.let {
                    hasField("long_task") {
                        hasField("count", it.count)
                    }
                }
                hasNullableField("loading_time", event.action.loadingTime)
            }
            .hasField("application") {
                hasField("id", event.application.id)
            }
            .hasField("session") {
                hasField("id", event.session.id)
                hasField("type", event.session.type.name.toLowerCase())
            }
            .hasField("view") {
                hasField("id", event.view.id)
                hasField("url", event.view.url)
            }
            .hasField("usr") {
                hasNullableField("id", event.usr?.id)
                hasNullableField("name", event.usr?.name)
                hasNullableField("email", event.usr?.email)
            }
            .hasField("_dd") {
                hasField("format_version", 2L)
            }
    }

    @Test
    fun `serializes view rum event`(
        @Forgery fakeEvent: RumEvent,
        @Forgery event: ViewEvent
    ) {
        val rumEvent = fakeEvent.copy(event = event)

        val serialized = underTest.serialize(rumEvent)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertEventMatches(jsonObject, rumEvent)
        assertThat(jsonObject)
            .hasField("type", "view")
            .hasField("date", event.date)
            .hasField("application") {
                hasField("id", event.application.id)
            }
            .hasField("session") {
                hasField("id", event.session.id)
                hasField("type", event.session.type.name.toLowerCase())
            }
            .hasField("view") {
                hasField("id", event.view.id)
                hasField("url", event.view.url)
                hasField("time_spent", event.view.timeSpent)
                hasField("action") {
                    hasField("count", event.view.action.count)
                }
                hasField("resource") {
                    hasField("count", event.view.resource.count)
                }
                hasField("error") {
                    hasField("count", event.view.error.count)
                }
                event.view.longTask?.let {
                    hasField("long_task") {
                        hasField("count", it.count)
                    }
                }
            }
            .hasField("usr") {
                hasNullableField("id", event.usr?.id)
                hasNullableField("name", event.usr?.name)
                hasNullableField("email", event.usr?.email)
            }
            .hasField("_dd") {
                hasField("format_version", 2L)
            }
    }

    @Test
    fun `serializes error rum event`(
        @Forgery fakeEvent: RumEvent,
        @Forgery event: ErrorEvent
    ) {
        val rumEvent = fakeEvent.copy(event = event)

        val serialized = underTest.serialize(rumEvent)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertEventMatches(jsonObject, rumEvent)
        assertThat(jsonObject)
            .hasField("type", "error")
            .hasField("date", event.date)
            .hasField("error") {
                hasField("message", event.error.message)
                hasField("source", event.error.source.name.toLowerCase())
                hasNullableField("stack", event.error.stack)
                event.error.resource?.let {
                    hasField("resource") {
                        hasField("method", it.method.name.toUpperCase())
                        hasField("status_code", it.statusCode)
                        hasField("url", it.url)
                    }
                }
            }
            .hasField("application") {
                hasField("id", event.application.id)
            }
            .hasField("session") {
                hasField("id", event.session.id)
                hasField("type", event.session.type.name.toLowerCase())
            }
            .hasField("view") {
                hasField("id", event.view.id)
                hasField("url", event.view.url)
            }
            .hasField("usr") {
                hasNullableField("id", event.usr?.id)
                hasNullableField("name", event.usr?.name)
                hasNullableField("email", event.usr?.email)
            }
            .hasField("_dd") {
                hasField("format_version", 2L)
            }
    }

    @Test
    fun `keep known custom attributes as is`(
        @Forgery fakeEvent: RumEvent,
        forge: Forge
    ) {
        val key = forge.anElementFrom(RumEventSerializer.knownAttributes)
        val value = forge.anAlphabeticalString()
        val event = fakeEvent.copy(attributes = mapOf(key to value))

        val serialized = underTest.serialize(event)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject

        assertThat(jsonObject)
            .hasField(key, value)
    }

    // region Internal

    private fun assertEventMatches(
        jsonObject: JsonObject,
        event: RumEvent
    ) {
        assertCustomAttributesMatch(jsonObject, event)
    }

    private fun assertCustomAttributesMatch(
        jsonObject: JsonObject,
        event: RumEvent
    ) {
        event.attributes
            .filter { it.key.isNotBlank() }
            .forEach {
                val value = it.value
                val keyName = "context.${it.key}"
                when (value) {
                    null -> assertThat(jsonObject).hasNullField(keyName)
                    is Boolean -> assertThat(jsonObject).hasField(keyName, value)
                    is Int -> assertThat(jsonObject).hasField(keyName, value)
                    is Long -> assertThat(jsonObject).hasField(keyName, value)
                    is Float -> assertThat(jsonObject).hasField(keyName, value)
                    is Double -> assertThat(jsonObject).hasField(keyName, value)
                    is String -> assertThat(jsonObject).hasField(keyName, value)
                    is Date -> assertThat(jsonObject).hasField(keyName, value.time)
                    is JsonObject -> assertThat(jsonObject).hasField(keyName, value)
                    is JsonArray -> assertThat(jsonObject).hasField(keyName, value)
                    is Iterable<*> -> assertThat(jsonObject).hasField(keyName, value.toJsonArray())
                    else -> assertThat(jsonObject).hasField(keyName, value.toString())
                }
            }
    }

    // endregion
}
