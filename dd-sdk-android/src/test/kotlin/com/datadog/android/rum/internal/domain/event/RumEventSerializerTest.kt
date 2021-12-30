/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.internal.constraints.DataConstraints
import com.datadog.android.core.model.UserInfo
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.google.gson.JsonParser
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Locale
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
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumEventSerializerTest {

    lateinit var testedSerializer: RumEventSerializer

    @BeforeEach
    fun `set up`() {
        testedSerializer = RumEventSerializer()
    }

    @RepeatedTest(8)
    fun `𝕄 serialize RUM event 𝕎 serialize() with ResourceEvent`(@Forgery event: ResourceEvent) {
        val serialized = testedSerializer.serialize(event)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertThat(jsonObject)
            .hasField("type", "resource")
            .hasField("date", event.date)
            .hasField("resource") {
                hasField("type", event.resource.type.name.lowercase(Locale.US))
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
                hasField("type", event.session.type.name.lowercase(Locale.US))
            }
            .hasField("view") {
                hasField("id", event.view.id)
                hasField("url", event.view.url)
            }
            .hasField("_dd") {
                hasField("format_version", 2L)
                hasNullableField("span_id", event.dd.spanId)
                hasNullableField("trace_id", event.dd.traceId)
            }

        event.usr?.let { usr ->
            assertThat(jsonObject).hasField("usr") {
                hasNullableField("id", usr.id)
                hasNullableField("name", usr.name)
                hasNullableField("email", usr.email)
                containsAttributes(usr.additionalProperties)
            }
        }
        event.connectivity?.let { connectivity ->
            assertThat(jsonObject).hasField("connectivity") {
                hasNullableField("status", connectivity.status.name.lowercase(Locale.US))
                hasNullableField(
                    "interfaces",
                    connectivity.interfaces.map { it.name.lowercase(Locale.US) }
                )
                connectivity.cellular?.let { cellular ->
                    hasField("cellular") {
                        hasNullableField("technology", cellular.technology)
                        hasNullableField("carrier_name", cellular.carrierName)
                    }
                }
            }
        }
        event.context?.additionalProperties?.let {
            assertThat(jsonObject).hasField("context") {
                containsAttributes(it)
            }
        }
    }

    @RepeatedTest(8)
    fun `𝕄 serialize RUM event 𝕎 serialize() with ActionEvent`(
        @Forgery event: ActionEvent
    ) {
        val serialized = testedSerializer.serialize(event)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertThat(jsonObject)
            .hasField("type", "action")
            .hasField("date", event.date)
            .hasField("action") {
                hasField("type", event.action.type.name.lowercase(Locale.US))
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
                event.action.crash?.let {
                    hasField("crash") {
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
                hasField("type", event.session.type.name.lowercase(Locale.US))
            }
            .hasField("view") {
                hasField("id", event.view.id)
                hasField("url", event.view.url)
            }
            .hasField("_dd") {
                hasField("format_version", 2L)
            }

        event.usr?.let { usr ->
            assertThat(jsonObject).hasField("usr") {
                hasNullableField("id", usr.id)
                hasNullableField("name", usr.name)
                hasNullableField("email", usr.email)
                containsAttributes(usr.additionalProperties)
            }
        }
        event.connectivity?.let { connectivity ->
            assertThat(jsonObject).hasField("connectivity") {
                hasNullableField("status", connectivity.status.name.lowercase(Locale.US))
                hasNullableField(
                    "interfaces",
                    connectivity.interfaces.map { it.name.lowercase(Locale.US) }
                )
                connectivity.cellular?.let { cellular ->
                    hasField("cellular") {
                        hasNullableField("technology", cellular.technology)
                        hasNullableField("carrier_name", cellular.carrierName)
                    }
                }
            }
        }
        event.context?.additionalProperties?.let {
            assertThat(jsonObject).hasField("context") {
                containsAttributes(it)
            }
        }
    }

    @RepeatedTest(8)
    fun `𝕄 serialize RUM event 𝕎 serialize() with ViewEvent`(@Forgery event: ViewEvent) {
        val serialized = testedSerializer.serialize(event)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertThat(jsonObject)
            .hasField("type", "view")
            .hasField("date", event.date)
            .hasField("application") {
                hasField("id", event.application.id)
            }
            .hasField("session") {
                hasField("id", event.session.id)
                hasField("type", event.session.type.name.lowercase(Locale.US))
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
            .hasField("_dd") {
                hasField("format_version", 2L)
            }

        event.usr?.let { usr ->
            assertThat(jsonObject).hasField("usr") {
                hasNullableField("id", usr.id)
                hasNullableField("name", usr.name)
                hasNullableField("email", usr.email)
                containsAttributes(usr.additionalProperties)
            }
        }
        event.connectivity?.let { connectivity ->
            assertThat(jsonObject).hasField("connectivity") {
                hasNullableField("status", connectivity.status.name.lowercase(Locale.US))
                hasNullableField(
                    "interfaces",
                    connectivity.interfaces.map { it.name.lowercase(Locale.US) }
                )
                connectivity.cellular?.let { cellular ->
                    hasField("cellular") {
                        hasNullableField("technology", cellular.technology)
                        hasNullableField("carrier_name", cellular.carrierName)
                    }
                }
            }
        }
        event.context?.additionalProperties?.let {
            assertThat(jsonObject).hasField("context") {
                containsAttributes(it)
            }
        }
    }

    @RepeatedTest(8)
    fun `𝕄 serialize RUM event 𝕎 serialize() with ErrorEvent`(@Forgery event: ErrorEvent) {
        val serialized = testedSerializer.serialize(event)
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertThat(jsonObject)
            .hasField("type", "error")
            .hasField("date", event.date)
            .hasField("error") {
                hasField("message", event.error.message)
                hasField("source", event.error.source.name.lowercase(Locale.US))
                hasNullableField("stack", event.error.stack)
                event.error.resource?.let {
                    hasField("resource") {
                        hasField("method", it.method.name.uppercase())
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
                hasField("type", event.session.type.name.lowercase(Locale.US))
            }
            .hasField("view") {
                hasField("id", event.view.id)
                hasField("url", event.view.url)
            }
            .hasField("_dd") {
                hasField("format_version", 2L)
            }

        event.usr?.let { usr ->
            assertThat(jsonObject).hasField("usr") {
                hasNullableField("id", usr.id)
                hasNullableField("name", usr.name)
                hasNullableField("email", usr.email)
                containsAttributes(usr.additionalProperties)
            }
        }
        event.connectivity?.let { connectivity ->
            assertThat(jsonObject).hasField("connectivity") {
                hasNullableField("status", connectivity.status.name.lowercase(Locale.US))
                hasNullableField(
                    "interfaces",
                    connectivity.interfaces.map { it.name.lowercase(Locale.US) }
                )
                connectivity.cellular?.let { cellular ->
                    hasField("cellular") {
                        hasNullableField("technology", cellular.technology)
                        hasNullableField("carrier_name", cellular.carrierName)
                    }
                }
            }
        }
        event.context?.additionalProperties?.let {
            assertThat(jsonObject).hasField("context") {
                containsAttributes(it)
            }
        }
    }

    @RepeatedTest(8)
    fun `𝕄 serialize RUM event 𝕎 serialize() with LongTaskEvent`(
        @Forgery event: LongTaskEvent
    ) {
        val serialized = testedSerializer.serialize(event)
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertThat(jsonObject)
            .hasField("type", "long_task")
            .hasField("date", event.date)
            .hasField("long_task") {
                hasField("duration", event.longTask.duration)
            }
            .hasField("application") {
                hasField("id", event.application.id)
            }
            .hasField("session") {
                hasField("id", event.session.id)
                hasField("type", event.session.type.name.lowercase(Locale.US))
            }
            .hasField("view") {
                hasField("id", event.view.id)
                hasField("url", event.view.url)
            }
            .hasField("_dd") {
                hasField("format_version", 2L)
            }
            .hasNullableField("service", event.service)

        event.usr?.let { usr ->
            assertThat(jsonObject).hasField("usr") {
                hasNullableField("id", usr.id)
                hasNullableField("name", usr.name)
                hasNullableField("email", usr.email)
                containsAttributes(usr.additionalProperties)
            }
        }
        event.connectivity?.let { connectivity ->
            assertThat(jsonObject).hasField("connectivity") {
                hasNullableField("status", connectivity.status.name.lowercase(Locale.US))
                hasNullableField(
                    "interfaces",
                    connectivity.interfaces.map { it.name.lowercase(Locale.US) }
                )
                connectivity.cellular?.let { cellular ->
                    hasField("cellular") {
                        hasNullableField("technology", cellular.technology)
                        hasNullableField("carrier_name", cellular.carrierName)
                    }
                }
            }
        }
        event.context?.additionalProperties?.let {
            assertThat(jsonObject).hasField("context") {
                containsAttributes(it)
            }
        }
    }

    @Test
    fun `𝕄 serialize RUM event 𝕎 serialize() with unknown event`(
        @Forgery unknownEvent: UserInfo
    ) {
        val serialized = testedSerializer.serialize(unknownEvent)
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertThat(jsonObject)
            .doesNotHaveField("type")
            .doesNotHaveField("date")
            .doesNotHaveField("error")
            .doesNotHaveField("action")
            .doesNotHaveField("resource")
            .doesNotHaveField("application")
            .doesNotHaveField("session")
            .doesNotHaveField("view")
            .doesNotHaveField("usr")
            .doesNotHaveField("_dd")
    }

    @Test
    fun `𝕄 keep known custom attributes as is 𝕎 serialize()`(forge: Forge) {
        val key = forge.anElementFrom(RumEventSerializer.knownAttributes)
        val value = forge.anAlphabeticalString()
        val event = forge.forgeRumEvent(mapOf(key to value))

        val serialized = testedSerializer.serialize(event)

        val jsonObject = JsonParser.parseString(serialized).asJsonObject

        assertThat(jsonObject)
            .hasField(key, value)
    }

    @Test
    fun `M sanitise the custom attributes keys W level deeper than 9`(forge: Forge) {
        // GIVEN
        val fakeBadKey =
            forge.aList(size = 10) { forge.anAlphabeticalString() }.joinToString(".")
        val lastIndexOf = fakeBadKey.lastIndexOf('.')
        val expectedSanitisedKey =
            fakeBadKey.replaceRange(lastIndexOf..lastIndexOf, "_")
        val fakeAttributeValue = forge.anAlphabeticalString()
        val fakeEvent = forge.forgeRumEvent(
            mapOf(
                fakeBadKey to fakeAttributeValue
            )
        )

        // WHEN
        val serializedEvent = testedSerializer.serialize(fakeEvent)
        val jsonObject = JsonParser.parseString(serializedEvent).asJsonObject

        // THEN
        val contextObject = jsonObject.getAsJsonObject(RumEventSerializer.GLOBAL_ATTRIBUTE_PREFIX)
        assertThat(contextObject)
            .hasField(
                expectedSanitisedKey,
                fakeAttributeValue
            )
        assertThat(contextObject)
            .doesNotHaveField(fakeBadKey)
    }

    @Test
    fun `M sanitise the user extra info keys W total level deeper than 10`(forge: Forge) {
        // GIVEN
        val fakeBadKey = forge.aList(size = 10) { forge.anAlphabeticalString() }.joinToString(".")
        val lastIndexOf = fakeBadKey.lastIndexOf('.')
        val expectedSanitisedKey =
            fakeBadKey.replaceRange(lastIndexOf..lastIndexOf, "_")
        val fakeAttributeValue = forge.anAlphabeticalString()
        val fakeEvent = forge.forgeRumEvent(
            userAttributes = mapOf(
                fakeBadKey to fakeAttributeValue
            )
        )

        // WHEN
        val serializedEvent = testedSerializer.serialize(fakeEvent)
        val jsonObject = JsonParser.parseString(serializedEvent).asJsonObject

        // THEN
        val userObject = jsonObject.getAsJsonObject(RumEventSerializer.USER_ATTRIBUTE_PREFIX)
        assertThat(userObject)
            .hasField(
                expectedSanitisedKey,
                fakeAttributeValue
            )
        assertThat(userObject)
            .doesNotHaveField(fakeBadKey)
    }

    @Test
    fun `M use the attributes group verbose name W validateAttributes { ViewEvent }`(
        @Forgery fakeEvent: ViewEvent
    ) {

        // GIVEN
        val mockedDataConstrains: DataConstraints = mock()
        testedSerializer = RumEventSerializer(mockedDataConstrains)

        // WHEN
        testedSerializer.serialize(fakeEvent)

        // THEN
        fakeEvent.usr?.let {
            verify(mockedDataConstrains).validateAttributes(
                it.additionalProperties,
                RumEventSerializer.USER_ATTRIBUTE_PREFIX,
                RumEventSerializer.USER_EXTRA_GROUP_VERBOSE_NAME,
                RumEventSerializer.ignoredAttributes
            )
        }
    }

    @Test
    fun `M use the attributes group verbose name W validateAttributes { ActionEvent }`(
        @Forgery fakeEvent: ActionEvent
    ) {

        // GIVEN
        val mockedDataConstrains: DataConstraints = mock()
        testedSerializer = RumEventSerializer(mockedDataConstrains)

        // WHEN
        testedSerializer.serialize(fakeEvent)

        // THEN
        fakeEvent.usr?.let {
            verify(mockedDataConstrains).validateAttributes(
                it.additionalProperties,
                RumEventSerializer.USER_ATTRIBUTE_PREFIX,
                RumEventSerializer.USER_EXTRA_GROUP_VERBOSE_NAME,
                RumEventSerializer.ignoredAttributes
            )
        }
    }

    @Test
    fun `M use the attributes group verbose name W validateAttributes { ResourceEvent }`(
        @Forgery fakeEvent: ResourceEvent
    ) {

        // GIVEN
        val mockedDataConstrains: DataConstraints = mock()
        testedSerializer = RumEventSerializer(mockedDataConstrains)

        // WHEN
        testedSerializer.serialize(fakeEvent)

        // THEN
        fakeEvent.usr?.let {
            verify(mockedDataConstrains).validateAttributes(
                it.additionalProperties,
                RumEventSerializer.USER_ATTRIBUTE_PREFIX,
                RumEventSerializer.USER_EXTRA_GROUP_VERBOSE_NAME,
                RumEventSerializer.ignoredAttributes
            )
        }
    }

    @Test
    fun `M use the attributes group verbose name W validateAttributes { ErrorEvent }`(
        @Forgery fakeEvent: ErrorEvent
    ) {

        // GIVEN
        val mockedDataConstrains: DataConstraints = mock()
        testedSerializer = RumEventSerializer(mockedDataConstrains)

        // WHEN
        testedSerializer.serialize(fakeEvent)

        // THEN
        fakeEvent.usr?.let {
            verify(mockedDataConstrains).validateAttributes(
                it.additionalProperties,
                RumEventSerializer.USER_ATTRIBUTE_PREFIX,
                RumEventSerializer.USER_EXTRA_GROUP_VERBOSE_NAME,
                RumEventSerializer.ignoredAttributes
            )
        }
    }

    @Test
    fun `M use the attributes group verbose name W validateAttributes { LongTaskEvent }`(
        @Forgery fakeEvent: LongTaskEvent
    ) {

        // GIVEN
        val mockedDataConstrains: DataConstraints = mock()
        testedSerializer = RumEventSerializer(mockedDataConstrains)

        // WHEN
        testedSerializer.serialize(fakeEvent)

        // THEN
        fakeEvent.usr?.let {
            verify(mockedDataConstrains).validateAttributes(
                it.additionalProperties,
                RumEventSerializer.USER_ATTRIBUTE_PREFIX,
                RumEventSerializer.USER_EXTRA_GROUP_VERBOSE_NAME,
                RumEventSerializer.ignoredAttributes
            )
        }
    }

    @Test
    fun `M drop the internal reserved attributes W serialize { custom global attributes }`(
        forge: Forge
    ) {
        // GIVEN
        val fakeInternalTimestamp = forge.aLong()
        val fakeErrorType = forge.aString()
        val fakeErrorSourceType = forge.aString()
        val fakeEventWithInternalGlobalAttributes = forge.forgeRumEvent(
            attributes = mapOf(
                RumAttributes.INTERNAL_ERROR_TYPE to fakeErrorType,
                RumAttributes.INTERNAL_TIMESTAMP to fakeInternalTimestamp,
                RumAttributes.INTERNAL_ERROR_SOURCE_TYPE to fakeErrorSourceType
            )
        )
        // WHEN
        val serializedEvent = testedSerializer.serialize(fakeEventWithInternalGlobalAttributes)
        val jsonObject = JsonParser.parseString(serializedEvent).asJsonObject

        // THEN
        assertThat(jsonObject)
            .doesNotHaveField(
                RumEventSerializer.GLOBAL_ATTRIBUTE_PREFIX + "." + RumAttributes.INTERNAL_TIMESTAMP
            )
        assertThat(jsonObject)
            .doesNotHaveField(
                RumEventSerializer.GLOBAL_ATTRIBUTE_PREFIX + "." + RumAttributes.INTERNAL_ERROR_TYPE
            )
        assertThat(jsonObject)
            .doesNotHaveField(
                RumEventSerializer.GLOBAL_ATTRIBUTE_PREFIX +
                    "." + RumAttributes.INTERNAL_ERROR_SOURCE_TYPE
            )
    }

    @Test
    fun `M drop the internal reserved attributes W serialize { custom user attributes }`(
        forge: Forge
    ) {
        // GIVEN
        val fakeInternalTimestamp = forge.aLong()
        val fakeErrorType = forge.aString()
        val fakeErrorSourceType = forge.aString()
        val fakeEventWithInternalUserAttributes = forge.forgeRumEvent(
            userAttributes = mapOf(
                RumAttributes.INTERNAL_ERROR_TYPE to fakeErrorType,
                RumAttributes.INTERNAL_TIMESTAMP to fakeInternalTimestamp,
                RumAttributes.INTERNAL_ERROR_SOURCE_TYPE to fakeErrorSourceType
            )
        )

        // WHEN
        val serializedEvent = testedSerializer.serialize(fakeEventWithInternalUserAttributes)
        val jsonObject = JsonParser.parseString(serializedEvent).asJsonObject

        // THEN
        assertThat(jsonObject)
            .doesNotHaveField(
                RumEventSerializer.USER_ATTRIBUTE_PREFIX + "." + RumAttributes.INTERNAL_TIMESTAMP
            )
        assertThat(jsonObject)
            .doesNotHaveField(
                RumEventSerializer.USER_ATTRIBUTE_PREFIX + "." + RumAttributes.INTERNAL_ERROR_TYPE
            )
        assertThat(jsonObject)
            .doesNotHaveField(
                RumEventSerializer.GLOBAL_ATTRIBUTE_PREFIX +
                    "." + RumAttributes.INTERNAL_ERROR_SOURCE_TYPE
            )
    }

    // region Internal

    private fun Forge.forgeRumEvent(
        attributes: Map<String, Any?> = emptyMap(),
        userAttributes: Map<String, Any?> = emptyMap()
    ): Any {
        return when (this.anInt(min = 0, max = 5)) {
            1 -> this.getForgery(ViewEvent::class.java).let {
                it.copy(
                    context = ViewEvent.Context(additionalProperties = attributes),
                    usr = (it.usr ?: ViewEvent.Usr()).copy(additionalProperties = userAttributes)
                )
            }
            2 -> this.getForgery(ActionEvent::class.java).let {
                it.copy(
                    context = ActionEvent.Context(additionalProperties = attributes),
                    usr = (it.usr ?: ActionEvent.Usr()).copy(additionalProperties = userAttributes)
                )
            }
            3 -> this.getForgery(ErrorEvent::class.java).let {
                it.copy(
                    context = ErrorEvent.Context(additionalProperties = attributes),
                    usr = (it.usr ?: ErrorEvent.Usr()).copy(additionalProperties = userAttributes)
                )
            }
            4 -> this.getForgery(ResourceEvent::class.java).let {
                it.copy(
                    context = ResourceEvent.Context(additionalProperties = attributes),
                    usr = (it.usr ?: ResourceEvent.Usr())
                        .copy(additionalProperties = userAttributes)
                )
            }
            else -> this.getForgery(LongTaskEvent::class.java).let {
                it.copy(
                    context = LongTaskEvent.Context(additionalProperties = attributes),
                    usr = (it.usr ?: LongTaskEvent.Usr())
                        .copy(additionalProperties = userAttributes)
                )
            }
        }
    }

    // endregion
}
