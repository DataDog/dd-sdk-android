/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.assertj

import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.trace.internal.domain.event.CoreTracerSpanToSpanEventMapper
import com.datadog.android.trace.model.SpanEvent
import com.datadog.trace.api.DDSpanId
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpanLink
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class SpanEventAssert(actual: SpanEvent) :
    AbstractObjectAssert<SpanEventAssert, SpanEvent>(
        actual,
        SpanEventAssert::class.java
    ) {

    // region Assert

    fun hasSpanId(spanId: String): SpanEventAssert {
        assertThat(actual.spanId)
            .overridingErrorMessage(
                "Expected SpanEvent to have spanId: $spanId" +
                    " but instead was: ${actual.spanId}"
            )
            .isEqualTo(spanId)
        return this
    }

    fun hasTraceId(traceId: String): SpanEventAssert {
        assertThat(actual.traceId)
            .overridingErrorMessage(
                "Expected SpanEvent to have traceId: $traceId" +
                    " but instead was: ${actual.traceId}"
            )
            .isEqualTo(traceId)
        return this
    }

    fun hasParentId(parentId: String): SpanEventAssert {
        assertThat(actual.parentId)
            .overridingErrorMessage(
                "Expected SpanEvent to have parentId: $parentId" +
                    " but instead was: ${actual.parentId}"
            )
            .isEqualTo(parentId)
        return this
    }

    fun hasServiceName(service: String?): SpanEventAssert {
        assertThat(actual.service)
            .overridingErrorMessage(
                "Expected SpanEvent to have serviceName: $service" +
                    " but instead was: ${actual.service}"
            )
            .isEqualTo(service)
        return this
    }

    fun hasResourceName(resourceName: String): SpanEventAssert {
        assertThat(actual.resource)
            .overridingErrorMessage(
                "Expected SpanEvent to have resourceName: $resourceName" +
                    " but instead was: ${actual.resource}"
            )
            .isEqualTo(resourceName)
        return this
    }

    fun hasOperationName(name: String): SpanEventAssert {
        assertThat(actual.name)
            .overridingErrorMessage(
                "Expected SpanEvent to have name : $name" +
                    " but instead was: ${actual.name}"
            )
            .isEqualTo(name)
        return this
    }

    fun hasSpanType(spanType: String): SpanEventAssert {
        assertThat(actual.type)
            .overridingErrorMessage(
                "Expected SpanEvent to have spanType: $spanType" +
                    " but instead was: ${actual.type}"
            )
            .isEqualTo(spanType)
        return this
    }

    fun hasSpanSource(spanSource: String): SpanEventAssert {
        assertThat(actual.meta.dd.source)
            .overridingErrorMessage(
                "Expected SpanEvent to have _dd.source: $spanSource" +
                    " but instead was: ${actual.meta.dd.source}"
            )
            .isEqualTo(spanSource)
        return this
    }

    fun hasApplicationId(applicationId: String?): SpanEventAssert {
        assertThat(actual.meta.dd.application?.id)
            .overridingErrorMessage(
                "Expected SpanEvent to have _dd.application.id: $applicationId" +
                    " but instead was: ${actual.meta.dd.application?.id}"
            )
            .isEqualTo(applicationId)
        return this
    }

    fun hasSessionId(sessionId: String?): SpanEventAssert {
        assertThat(actual.meta.dd.session?.id)
            .overridingErrorMessage(
                "Expected SpanEvent to have _dd.session.id: $sessionId" +
                    " but instead was: ${actual.meta.dd.session?.id}"
            )
            .isEqualTo(sessionId)
        return this
    }

    fun hasViewId(viewId: String?): SpanEventAssert {
        assertThat(actual.meta.dd.view?.id)
            .overridingErrorMessage(
                "Expected SpanEvent to have _dd.view.id: $viewId" +
                    " but instead was: ${actual.meta.dd.view?.id}"
            )
            .isEqualTo(viewId)
        return this
    }

    fun hasSpanStartTime(startTime: Long): SpanEventAssert {
        assertThat(actual.start)
            .overridingErrorMessage(
                "Expected SpanEvent to have start time: $startTime" +
                    " but instead was: ${actual.start}"
            )
            .isEqualTo(
                startTime
            )
        return this
    }

    fun hasSpanDuration(duration: Long): SpanEventAssert {
        assertThat(actual.duration)
            .overridingErrorMessage(
                "Expected SpanEvent to have duration: $duration" +
                    " but instead was: ${actual.duration}"
            )
            .isEqualTo(
                duration
            )
        return this
    }

    fun hasTracerVersion(version: String): SpanEventAssert {
        assertThat(actual.meta.tracer.version)
            .overridingErrorMessage(
                "Expected SpanEvent to have tracer version: $version" +
                    " but instead was: ${actual.meta.tracer.version}"
            )
            .isEqualTo(version)
        return this
    }

    fun hasClientPackageVersion(clientPackageVersion: String): SpanEventAssert {
        assertThat(actual.meta.version)
            .overridingErrorMessage(
                "Expected SpanEvent to have client package" +
                    " version: $clientPackageVersion" +
                    " but instead was: ${actual.meta.version}"
            )
            .isEqualTo(clientPackageVersion)
        return this
    }

    fun hasErrorFlag(error: Long): SpanEventAssert {
        assertThat(actual.error)
            .overridingErrorMessage(
                "Expected SpanEvent to have errorFlag: $error" +
                    " but instead was: ${actual.error}"
            )
            .isEqualTo(error)
        return this
    }

    fun hasMeta(attributes: Map<String, String>): SpanEventAssert {
        assertThat(actual.meta.additionalProperties)
            .containsAllEntriesOf(attributes)
        return this
    }

    fun hasMetrics(metrics: Map<String, Number>): SpanEventAssert {
        assertThat(actual.metrics.additionalProperties)
            .hasSameSizeAs(metrics)
            .containsAllEntriesOf(metrics)
        return this
    }

    fun isTopSpan(): SpanEventAssert {
        assertThat(actual.metrics.topLevel).overridingErrorMessage(
            "Expected the " +
                " metrics top level to be 1 but instead was ${actual.metrics.topLevel}"
        ).isEqualTo(1L)
        return this
    }

    fun isNotTopSpan(): SpanEventAssert {
        assertThat(actual.metrics.topLevel).overridingErrorMessage(
            "Expected the " +
                " metrics top level to be null but instead was ${actual.metrics.topLevel}"
        ).isNull()
        return this
    }

    fun hasNetworkInfo(networkInfo: NetworkInfo): SpanEventAssert {
        assertThat(actual.meta.network?.client?.connectivity)
            .overridingErrorMessage(
                "Expected SpanEvent to have connectivity: " +
                    "${networkInfo.connectivity} but " +
                    "instead was: ${actual.meta.network?.client?.connectivity}"
            )
            .isEqualTo(networkInfo.connectivity.toString())
        assertThat(actual.meta.network?.client?.downlinkKbps)
            .overridingErrorMessage(
                "Expected SpanEvent to have downlinkKbps: " +
                    "${networkInfo.downKbps?.toString()} but " +
                    "instead was: ${actual.meta.network?.client?.downlinkKbps}"
            )
            .isEqualTo(networkInfo.downKbps?.toString())
        assertThat(actual.meta.network?.client?.uplinkKbps)
            .overridingErrorMessage(
                "Expected SpanEvent to have uplinkKbps: " +
                    "${networkInfo.upKbps?.toString()} but " +
                    "instead was: ${actual.meta.network?.client?.uplinkKbps}"
            )
            .isEqualTo(networkInfo.upKbps?.toString())
        assertThat(actual.meta.network?.client?.signalStrength)
            .overridingErrorMessage(
                "Expected SpanEvent to have signal strength: " +
                    "${networkInfo.strength?.toString()} but " +
                    "instead was: ${actual.meta.network?.client?.signalStrength}"
            )
            .isEqualTo(networkInfo.strength?.toString())
        assertThat(actual.meta.network?.client?.simCarrier?.id)
            .overridingErrorMessage(
                "Expected SpanEvent to have carrier id: " +
                    "${networkInfo.carrierId?.toString()} but " +
                    "instead was: ${actual.meta.network?.client?.simCarrier?.id}"
            )
            .isEqualTo(networkInfo.carrierId?.toString())
        assertThat(actual.meta.network?.client?.simCarrier?.name)
            .overridingErrorMessage(
                "Expected SpanEvent to have carrier name: " +
                    "${networkInfo.carrierName} but " +
                    "instead was: ${actual.meta.network?.client?.simCarrier?.name}"
            )
            .isEqualTo(networkInfo.carrierName)
        return this
    }

    fun doesntHaveNetworkInfo(): SpanEventAssert {
        assertThat(actual.meta.network)
            .overridingErrorMessage(
                "Expected SpanEvent to not have network info but was: " +
                    "${actual.meta.network}"
            )
            .isNull()
        return this
    }

    fun hasUserInfo(userInfo: UserInfo): SpanEventAssert {
        assertThat(actual.meta.usr.name)
            .overridingErrorMessage(
                "Expected SpanEvent to have user name: " +
                    "${userInfo.name} but " +
                    "instead was: ${actual.meta.usr.name}"
            )
            .isEqualTo(userInfo.name)
        assertThat(actual.meta.usr.email)
            .overridingErrorMessage(
                "Expected SpanEvent to have user email: " +
                    "${userInfo.email} but " +
                    "instead was: ${actual.meta.usr.email}"
            )
            .isEqualTo(userInfo.email)
        assertThat(actual.meta.usr.id)
            .overridingErrorMessage(
                "Expected SpanEvent to have user id: " +
                    "${userInfo.id} but " +
                    "instead was: ${actual.meta.usr.id}"
            )
            .isEqualTo(userInfo.id)
        assertThat(actual.meta.usr.additionalProperties)
            .hasSameSizeAs(userInfo.additionalProperties)
            .containsAllEntriesOf(userInfo.additionalProperties)
        return this
    }

    fun hasSpanLinks(links: List<AgentSpanLink>): SpanEventAssert {
        if (links.isEmpty()) {
            assertThat(actual.meta.additionalProperties)
                .overridingErrorMessage(
                    "Expected SpanEvent to not have span links but " +
                        "instead was: ${actual.meta.additionalProperties}"
                )
                .doesNotContainKey(CoreTracerSpanToSpanEventMapper.SPAN_LINKS_KEY)
            return this
        }
        val serializedLinks = actual.meta.additionalProperties[CoreTracerSpanToSpanEventMapper.SPAN_LINKS_KEY]
        val deserializedLinks = JsonParser.parseString(serializedLinks).asJsonArray
        assertThat(deserializedLinks.size())
            .overridingErrorMessage(
                "Expected SpanEvent to have ${links.size} span links but " +
                    "instead was: ${deserializedLinks.size()}"
            )
            .isEqualTo(links.size)
        links.forEachIndexed { index, link ->
            val actualLink = deserializedLinks[index].asJsonObject
            val traceId = link.traceId().toHexString()
            val spanId = DDSpanId.toHexStringPadded(link.spanId())
            val traceFlags = link.traceFlags()
            val traceState = link.traceState()
            val attributes = link.attributes().asMap()
            SerializedSpanLinkAssert(actualLink)
                .hasTraceId(traceId)
                .hasSpanId(spanId)
                .hasFlags(traceFlags)
                .hasTraceState(traceState)
                .hasAttributes(attributes)
        }
        return this
    }

    // endregion

    private class SerializedSpanLinkAssert(actualLink: JsonObject) :
        AbstractObjectAssert<SerializedSpanLinkAssert, JsonObject>(actualLink, SerializedSpanLinkAssert::class.java) {
        fun hasTraceId(traceId: String): SerializedSpanLinkAssert {
            val actualTraceId = actual.get("trace_id").asString
            assertThat(actualTraceId)
                .overridingErrorMessage(
                    "Expected SpanEvent to have span link trace id: " +
                        "$traceId but " +
                        "instead was: $actualTraceId"
                )
                .isEqualTo(traceId)
            return this
        }

        fun hasSpanId(spanId: String): SerializedSpanLinkAssert {
            val actualSpanId = actual.get("span_id").asString
            assertThat(actualSpanId)
                .overridingErrorMessage(
                    "Expected SpanEvent to have span link span id: " +
                        "$spanId but " +
                        "instead was: $$actualSpanId"
                )
                .isEqualTo(spanId)
            return this
        }

        fun hasFlags(flags: Byte): SerializedSpanLinkAssert {
            if (flags.toInt() != 0) {
                val actualTraceFlags = actual.get("flags").asByte
                assertThat(actualTraceFlags)
                    .overridingErrorMessage(
                        "Expected SpanEvent to have span link flags: " +
                            "$flags but " +
                            "instead was: $actualTraceFlags"
                    )
                    .isEqualTo(flags)
            } else {
                assertThat(actual.has("flags"))
                    .overridingErrorMessage(
                        "Expected SpanEvent to not have span link flags but " +
                            "instead was: $actual"
                    )
                    .isFalse()
            }
            return this
        }

        fun hasTraceState(traceState: String): SerializedSpanLinkAssert {
            val actualTraceState = actual.get("tracestate").asString
            if (actualTraceState.isNotEmpty()) {
                assertThat(actualTraceState)
                    .overridingErrorMessage(
                        "Expected SpanEvent to have span link trace state: " +
                            "$traceState but " +
                            "instead was: $actualTraceState"
                    )
                    .isEqualTo(traceState)
            } else {
                assertThat(actual.has("tracestate"))
                    .overridingErrorMessage(
                        "Expected SpanEvent to not have span link trace state but " +
                            "instead was: $actual"
                    ).isFalse()
            }
            return this
        }

        fun hasAttributes(attributes: Map<String, String>): SerializedSpanLinkAssert {
            val actualAttributes = actual.get("attributes").toString()
            val jsonObject = JsonObject()
            attributes.forEach { (key, value) ->
                jsonObject.addProperty(key, value)
            }
            val expectedAttributesJsonString = jsonObject.toString()
            assertThat(actualAttributes)
                .overridingErrorMessage(
                    "Expected SpanEvent to have span link attributes: " +
                        "$expectedAttributesJsonString but " +
                        "instead was: $actualAttributes"
                )
                .isEqualTo(expectedAttributesJsonString)
            return this
        }
    }

    companion object {
        internal fun assertThat(actual: SpanEvent): SpanEventAssert {
            return SpanEventAssert(actual)
        }
    }
}
