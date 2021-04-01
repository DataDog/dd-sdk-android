/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.domain.event

import com.datadog.android.core.internal.constraints.DatadogDataConstraints
import com.datadog.android.core.internal.utils.NULL_MAP_VALUE
import com.datadog.android.tracing.model.SpanEvent
import com.datadog.android.utils.extension.getString
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.tools.unit.assertj.JsonObjectAssert
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Date
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(Configurator::class)
internal class SpanEventSerializerTest {

    @StringForgery
    lateinit var fakeEnvName: String

    @Mock
    lateinit var mockDatadogConstraints: DatadogDataConstraints

    lateinit var testedSerializer: SpanEventSerializer

    @BeforeEach
    fun `set up`() {
        whenever(
            mockDatadogConstraints.validateAttributes(
                any(),
                anyOrNull(),
                anyOrNull()
            )
        ).thenAnswer {
            it.getArgument(0)
        }
        testedSerializer =
            SpanEventSerializer(fakeEnvName, dataConstraints = mockDatadogConstraints)
    }

    // region tests

    @Test
    fun `M serialize a SpanEvent when serialize`(@Forgery fakeSpanEvent: SpanEvent) {
        // WHEN
        val serialized = testedSerializer.serialize(fakeSpanEvent)

        // THEN
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        val spanObject = jsonObject.getAsJsonArray(KEY_SPANS).first() as JsonObject
        assertJsonMatchesInputSpan(spanObject, fakeSpanEvent)
        Assertions.assertThat(jsonObject.getString(KEY_ENV)).isEqualTo(fakeEnvName)
    }

    @Test
    fun `M sanitise the user extra info keys W level deeper than 8`(
        @Forgery fakeSpanEvent: SpanEvent,
        forge: Forge
    ) {
        // GIVEN
        val fakeSanitizedAttributes = forge.exhaustiveAttributes()
        whenever(
            mockDatadogConstraints
                .validateAttributes(fakeSpanEvent.meta.usr.additionalProperties)
        )
            .thenReturn(fakeSanitizedAttributes)

        // WHEN
        val serialized = testedSerializer.serialize(fakeSpanEvent)

        // THEN
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        val spanObject = jsonObject.getAsJsonArray(KEY_SPANS).first() as JsonObject
        JsonObjectAssert.assertThat(spanObject).hasField(KEY_META) {
            hasField(KEY_USR) {
                containsExtraAttributesMappedToMetaStrings(fakeSanitizedAttributes)
            }
        }
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
                    hasField(KEY_SOURCE, DD_SOURCE_ANDROID)
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
        hasField(
            KEY_NETWORK_CONNECTIVITY,
            network.client.connectivity
        )
        hasField(KEY_SIM_CARRIER) {
            val simCarrierName = network.client.simCarrier.name
            if (simCarrierName != null) {
                hasField(KEY_NETWORK_CARRIER_NAME, simCarrierName)
            } else {
                doesNotHaveField(KEY_NETWORK_CARRIER_NAME)
            }
            val simCarrierId = network.client.simCarrier.id
            if (simCarrierId != null) {
                hasField(
                    KEY_NETWORK_CARRIER_ID,
                    simCarrierId
                )
            } else {
                doesNotHaveField(KEY_NETWORK_CARRIER_ID)
            }
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
