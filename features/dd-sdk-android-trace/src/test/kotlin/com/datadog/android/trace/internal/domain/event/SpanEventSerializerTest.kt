/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.event

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.core.constraints.DatadogDataConstraints
import com.datadog.android.internal.utils.NULL_MAP_VALUE
import com.datadog.android.trace.model.SpanEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.assertj.JsonObjectAssert
import com.datadog.tools.unit.forge.anException
import com.datadog.tools.unit.forge.exhaustiveAttributes
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Date

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(Configurator::class)
internal class SpanEventSerializerTest {

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Mock
    lateinit var mockDatadogConstraints: DatadogDataConstraints

    private lateinit var testedSerializer: SpanEventSerializer

    @BeforeEach
    fun `set up`() {
        whenever(
            mockDatadogConstraints.validateAttributes<Any?>(
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
        ).thenAnswer {
            it.getArgument(0)
        }
        testedSerializer =
            SpanEventSerializer(internalLogger = mock(), dataConstraints = mockDatadogConstraints)
    }

    // region tests

    @Test
    fun `M serialize a SpanEvent W serialize`(@Forgery fakeSpanEvent: SpanEvent) {
        // WHEN
        val serialized = testedSerializer.serialize(fakeDatadogContext, fakeSpanEvent)

        // THEN
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        val spanObject = jsonObject.getAsJsonArray(KEY_SPANS).first() as JsonObject
        assertJsonMatchesInputSpan(spanObject, fakeSpanEvent)
        Assertions.assertThat(jsonObject.get(KEY_ENV).asString).isEqualTo(fakeDatadogContext.env)
    }

    @Test
    fun `M sanitise with the right prefix the user extra info keys W serialize`(
        @Forgery fakeSpanEvent: SpanEvent,
        forge: Forge
    ) {
        // GIVEN
        val fakeSanitizedAttributes = forge.exhaustiveAttributes(setOf(KEY_USR_ID, KEY_USR_NAME, KEY_USR_EMAIL))
        whenever(
            mockDatadogConstraints
                .validateAttributes(
                    fakeSpanEvent.meta.usr.additionalProperties,
                    SpanEventSerializer.META_USR_KEY_PREFIX,
                    reservedKeys = emptySet()
                )
        ).thenReturn(fakeSanitizedAttributes)

        // WHEN
        val serialized = testedSerializer.serialize(fakeDatadogContext, fakeSpanEvent)

        // THEN
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        val spanObject = jsonObject.getAsJsonArray(KEY_SPANS).first() as JsonObject
        JsonObjectAssert.assertThat(spanObject).hasField(KEY_META) {
            hasField(KEY_USR) {
                containsExtraAttributesMappedToMetaStrings(fakeSanitizedAttributes)
            }
        }
    }

    @Test
    fun `M sanitise with the right prefix then span metrics keys W serialize`(
        @Forgery fakeSpanEvent: SpanEvent,
        forge: Forge
    ) {
        // GIVEN
        val fakeSanitizedAttributes = forge.aMap<String, Number>(size = 10) {
            forge.anAlphabeticalString() to forge.aLong()
        }.toMutableMap()
        whenever(
            mockDatadogConstraints
                .validateAttributes(
                    fakeSpanEvent.metrics.additionalProperties,
                    SpanEventSerializer.METRICS_KEY_PREFIX,
                    reservedKeys = emptySet()
                )
        ).thenReturn(fakeSanitizedAttributes)

        // WHEN
        val serialized = testedSerializer.serialize(fakeDatadogContext, fakeSpanEvent)

        // THEN
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        val spanObject = jsonObject.getAsJsonArray(KEY_SPANS).first() as JsonObject
        JsonObjectAssert.assertThat(spanObject).hasField(KEY_METRICS) {
            containsExtraAttributesAsMetrics(fakeSanitizedAttributes)
        }
    }

    @Test
    fun `M not throw W serialize() { usr#additionalProperties serialization throws }`(
        @Forgery fakeSpanEvent: SpanEvent,
        forge: Forge
    ) {
        // GIVEN
        val faultyKey = forge.anAlphabeticalString()
        val faultyObject = object {
            override fun toString(): String {
                throw forge.anException()
            }
        }
        val faultySpanEvent = fakeSpanEvent.copy(
            meta = fakeSpanEvent.meta.copy(
                usr = fakeSpanEvent.meta.usr.copy(
                    additionalProperties = fakeSpanEvent.meta.usr
                        .additionalProperties
                        .toMutableMap()
                        .apply { put(faultyKey, faultyObject) }
                )
            )
        )

        // WHEN
        val serialized = testedSerializer.serialize(fakeDatadogContext, faultySpanEvent)

        // THEN
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        val spanObject = jsonObject.getAsJsonArray(KEY_SPANS).first() as JsonObject
        assertJsonMatchesInputSpan(spanObject, fakeSpanEvent)
        Assertions.assertThat(jsonObject.get(KEY_ENV).asString).isEqualTo(fakeDatadogContext.env)
    }

    // endregion

    // region Internal

    private fun assertJsonMatchesInputSpan(
        jsonObject: JsonObject,
        span: SpanEvent
    ) {
        JsonObjectAssert.assertThat(jsonObject)
            .hasField(KEY_START_TIMESTAMP, span.start)
            .hasField(KEY_DURATION, span.duration)
            .hasField(KEY_SERVICE_NAME, span.service)
            .hasField(KEY_TRACE_ID, span.traceId)
            .hasField(KEY_SPAN_ID, span.spanId)
            .hasField(KEY_PARENT_ID, span.parentId)
            .hasField(KEY_RESOURCE, span.resource)
            .hasField(KEY_OPERATION_NAME, span.name)
            .hasField(KEY_ERROR, span.error)
            .hasField(KEY_TYPE, TYPE_CUSTOM)
            .hasField(KEY_META) {
                hasField(KEY_DD) {
                    val expectedSource = span.meta.dd.source
                    if (expectedSource == null) {
                        doesNotHaveField(KEY_SOURCE)
                    } else {
                        hasField(KEY_SOURCE, expectedSource)
                    }
                }
                hasField(KEY_SPAN) {
                    hasField(KEY_KIND, KIND_CLIENT)
                }
                hasField(KEY_TRACER) {
                    hasField(KEY_VERSION, span.meta.tracer.version)
                }
                hasField(KEY_APPLICATION_VERSION, span.meta.version)
                hasField(KEY_NETWORK) {
                    hasField(KEY_CLIENT) {
                        hasNetworkInfo(span)
                    }
                }
                hasField(KEY_USR) {
                    hasUserInfo(span)
                }
                containsExtraAttributesMappedToMetaStrings(span.meta.additionalProperties)
            }
            .hasField(KEY_METRICS) {
                span.metrics.topLevel?.let {
                    hasField(KEY_METRICS_TOP_LEVEL, it)
                }
                containsExtraAttributesAsMetrics(span.metrics.additionalProperties)
            }
    }

    private fun JsonObjectAssert.containsExtraAttributesMappedToMetaStrings(
        attributes: Map<String, Any?>
    ) {
        attributes.filter { it.key.isNotBlank() }
            .forEach {
                val value = it.value
                when (value) {
                    NULL_MAP_VALUE -> doesNotHaveField(it.key)
                    null -> doesNotHaveField(it.key)
                    is Date -> hasField(it.key, value.time.toString())
                    is Iterable<*> -> hasField(it.key, value.toString())
                    else -> hasField(it.key, value.toString())
                }
            }
    }

    private fun JsonObjectAssert.containsExtraAttributesAsMetrics(
        attributes: Map<String, Number>
    ) {
        attributes.filter { it.key.isNotBlank() }
            .forEach {
                hasField(it.key, it.value)
            }
    }

    private fun JsonObjectAssert.hasNetworkInfo(
        spanEvent: SpanEvent
    ) {
        val network = spanEvent.meta.network
        val connectivity = network?.client?.connectivity
        if (connectivity != null) {
            hasField(KEY_NETWORK_CONNECTIVITY, connectivity)
        } else {
            doesNotHaveField(KEY_NETWORK_CONNECTIVITY)
        }
        val simCarrier = network?.client?.simCarrier
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
        val uplinkKbps = network?.client?.uplinkKbps
        if (uplinkKbps != null) {
            hasField(KEY_NETWORK_UP_KBPS, uplinkKbps)
        } else {
            doesNotHaveField(KEY_NETWORK_UP_KBPS)
        }
        val downlinkKbps = network?.client?.downlinkKbps
        if (downlinkKbps != null) {
            hasField(
                KEY_NETWORK_DOWN_KBPS,
                downlinkKbps
            )
        } else {
            doesNotHaveField(KEY_NETWORK_DOWN_KBPS)
        }
        val signalStrength = network?.client?.signalStrength
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
        spanEvent: SpanEvent
    ) {
        val userName = spanEvent.meta.usr.name
        val userEmail = spanEvent.meta.usr.email
        val userId = spanEvent.meta.usr.id
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
        containsExtraAttributesMappedToStrings(
            spanEvent.meta.usr.additionalProperties
        )
    }

    private fun JsonObjectAssert.containsExtraAttributesMappedToStrings(
        attributes: Map<String, Any?>,
        keyNamePrefix: String = ""
    ) {
        attributes.filter { it.key.isNotBlank() }
            .forEach {
                val value = it.value
                val key = keyNamePrefix + it.key
                when (value) {
                    NULL_MAP_VALUE -> doesNotHaveField(key)
                    null -> doesNotHaveField(key)
                    is Date -> hasField(key, value.time.toString())
                    is Iterable<*> -> hasField(key, value.toString())
                    else -> hasField(key, value.toString())
                }
            }
    }

// endregion

    companion object {

        internal const val DD_SOURCE_ANDROID = "android"
        internal const val KIND_CLIENT = "client"

        // PAYLOAD TAGS
        internal const val KEY_SPANS = "spans"
        internal const val KEY_ENV = "env"

        // SPAN TAGS
        internal const val KEY_START_TIMESTAMP = "start"
        internal const val KEY_DURATION = "duration"
        internal const val KEY_SERVICE_NAME = "service"
        internal const val KEY_APPLICATION_VERSION = "version"
        internal const val KEY_TRACE_ID = "trace_id"
        internal const val KEY_SPAN_ID = "span_id"
        internal const val KEY_PARENT_ID = "parent_id"
        internal const val KEY_RESOURCE = "resource"
        internal const val KEY_OPERATION_NAME = "name"
        internal const val KEY_ERROR = "error"
        internal const val KEY_TYPE = "type"
        internal const val KEY_META = "meta"
        internal const val KEY_NETWORK = "network"
        internal const val KEY_CLIENT = "client"
        internal const val KEY_METRICS = "metrics"
        internal const val KEY_METRICS_TOP_LEVEL = "_top_level"
        internal const val KEY_DD = "_dd"
        internal const val KEY_SOURCE = "source"
        internal const val KEY_SPAN = "span"
        internal const val KEY_KIND = "kind"
        internal const val KEY_TRACER = "tracer"
        internal const val KEY_VERSION = "version"
        internal const val TYPE_CUSTOM = "custom"
        internal const val KEY_SIM_CARRIER = "sim_carrier"
        internal const val KEY_NETWORK_CARRIER_ID: String = "id"
        internal const val KEY_NETWORK_CARRIER_NAME: String = "name"
        internal const val KEY_NETWORK_CONNECTIVITY: String = "connectivity"
        internal const val KEY_NETWORK_DOWN_KBPS: String = "downlink_kbps"
        internal const val KEY_NETWORK_SIGNAL_STRENGTH: String = "signal_strength"
        internal const val KEY_NETWORK_UP_KBPS: String = "uplink_kbps"
        internal const val KEY_USR = "usr"
        internal const val KEY_USR_NAME = "name"
        internal const val KEY_USR_EMAIL = "email"
        internal const val KEY_USR_ID = "id"
    }
}
