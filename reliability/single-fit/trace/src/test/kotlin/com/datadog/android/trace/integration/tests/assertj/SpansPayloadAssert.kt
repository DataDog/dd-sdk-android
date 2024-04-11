/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.integration.tests.assertj

import com.datadog.android.tests.ktx.getDouble
import com.datadog.android.tests.ktx.getInt
import com.datadog.android.tests.ktx.getLong
import com.datadog.android.tests.ktx.getString
import com.google.gson.JsonObject
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import java.util.Locale

internal class SpansPayloadAssert(actual: JsonObject) :
    AbstractObjectAssert<SpansPayloadAssert, JsonObject>(actual, SpansPayloadAssert::class.java) {

    private val spans = actual.getAsJsonArray(SPANS_KEY)

    fun hasEnv(env: String): SpansPayloadAssert {
        assertThat(actual.getString(ENV_KEY)).overridingErrorMessage(
            "Expected env to be $env but was ${actual.getString(ENV_KEY)}"
        ).isEqualTo(env)
        return this
    }

    fun hasSpanAtIndexWith(index: Int, block: SpanAssert.() -> Unit): SpansPayloadAssert {
        SpanAssert(spans.get(index).asJsonObject, index).block()
        return this
    }

    class SpanAssert(private val actualSpan: JsonObject, private val index: Int) :
        AbstractObjectAssert<SpanAssert, JsonObject>(actualSpan, SpanAssert::class.java) {

        fun hasTraceId(traceId: String): SpanAssert {
            val actualTraceId = actualSpan.getString(TRACE_ID_KEY)
            assertThat(actualTraceId).overridingErrorMessage(
                "Expected traceId to be $traceId but was $actualTraceId for index $index"
            ).isEqualTo(traceId)
            return this
        }

        fun hasSpanId(spanId: String): SpanAssert {
            val actualSpanId = actualSpan.getString(SPAN_ID_KEY)
            assertThat(actualSpanId).overridingErrorMessage(
                "Expected spanId to be $spanId but was $actualSpanId for index $index"
            ).isEqualTo(spanId)
            return this
        }

        fun hasService(service: String): SpanAssert {
            val actualServiceName = actualSpan.getString(SERVICE_KEY)
            assertThat(actualServiceName).overridingErrorMessage(
                "Expected service to be $service but was $actualServiceName for index $index"
            ).isEqualTo(service)
            return this
        }

        fun hasError(error: Int): SpanAssert {
            val actualHasError = actualSpan.getInt(ERROR_KEY)
            assertThat(actualHasError).overridingErrorMessage(
                "Expected error to be $error but was $actualHasError for index $index"
            ).isEqualTo(error)
            return this
        }

        fun hasName(name: String): SpanAssert {
            val actualName = actualSpan.getString(NAME_KEY)
            assertThat(actualName).overridingErrorMessage(
                "Expected name to be $name but was $actualName for index $index"
            ).isEqualTo(name)
            return this
        }

        fun hasResource(resource: String): SpanAssert {
            val actualResource = actualSpan.getString(RESOURCE_KEY)
            assertThat(actualResource).overridingErrorMessage(
                "Expected resource to be $resource but was $actualResource for index $index"
            ).isEqualTo(resource)
            return this
        }

        fun hasUserObject(usr: String): SpanAssert {
            val actualUserObject = actualSpan.getString(USR_KEY)
            assertThat(actualUserObject).overridingErrorMessage(
                "Expected usr to be $usr but was $actualUserObject for index $index"
            ).isEqualTo(usr)
            return this
        }

        fun hasUserId(usrId: String): SpanAssert {
            val actualUserId = actualSpan.getString(USR_ID_KEY)
            assertThat(actualUserId).overridingErrorMessage(
                "Expected usrId to be $usrId but was $actualUserId for index $index"
            ).isEqualTo(usrId)
            return this
        }

        fun hasUserName(usrName: String): SpanAssert {
            val actualUserName = actualSpan.getString(USR_NAME_KEY)
            assertThat(actualUserName).overridingErrorMessage(
                "Expected usrName to be $usrName but was $actualUserName for index $index"
            ).isEqualTo(usrName)
            return this
        }

        fun hasUserEmail(usrEmail: String): SpanAssert {
            val actualUserEmail = actualSpan.getString(USR_EMAIL_KEY)
            assertThat(actualUserEmail).overridingErrorMessage(
                "Expected usrEmail to be $usrEmail but was $actualUserEmail for index $index"
            ).isEqualTo(usrEmail)
            return this
        }

        fun hasDuration(duration: Long): SpanAssert {
            val actualDuration = actualSpan.getLong(DURATION_KEY)
            assertThat(actualDuration).overridingErrorMessage(
                "Expected duration to be $duration but was $actualDuration for index $index"

            ).isEqualTo(duration)
            return this
        }

        fun hasDurationBetween(durationStartInteval: Long, durationEndInterval: Long): SpanAssert {
            val actualDuration = actualSpan.getLong(DURATION_KEY)
            assertThat(actualDuration).overridingErrorMessage(
                "Expected duration to be between $durationStartInteval " +
                    "and $durationEndInterval but was $actualDuration for index $index"
            ).isBetween(durationStartInteval, durationEndInterval)
            return this
        }

        fun hasSource(source: String): SpanAssert {
            val actualSource = actualSpan.getString(SOURCE_KEY)
            assertThat(actualSource).overridingErrorMessage(
                "Expected source to be $source but was $actualSource for index $index"
            ).isEqualTo(source)
            return this
        }

        fun hasVersion(version: String): SpanAssert {
            val actualVersion = actualSpan.getString(VERSION_KEY)
            assertThat(actualVersion).overridingErrorMessage(
                "Expected version to be $version but was $actualVersion for index $index"
            ).isEqualTo(version)
            return this
        }

        fun hasTracerVersion(tracerVersion: String): SpanAssert {
            val actualTracerVersion = actualSpan.getString(TRACER_VERSION_KEY)
            assertThat(actualTracerVersion).overridingErrorMessage(
                "Expected tracerVersion to be $tracerVersion but" +
                    " was $actualTracerVersion for index $index"
            ).isEqualTo(tracerVersion)
            return this
        }

        fun hasSamplingPriority(samplingPriority: Int): SpanAssert {
            val actualSamplingPriority = actualSpan.getInt(SAMPLING_PRIORITY_KEY)
            assertThat(actualSamplingPriority).overridingErrorMessage(
                "Expected samplingPriority to be $samplingPriority but " +
                    "was $actualSamplingPriority for index $index"
            ).isEqualTo(samplingPriority)
            return this
        }

        fun hasAgentPsr(agentPsr: Double): SpanAssert {
            val actualPsrValue = actualSpan.getDouble(AGENT_PSR_KEY)
            assertThat(actualPsrValue).overridingErrorMessage(
                "Expected agentPsr to be $agentPsr but was $actualPsrValue for index $index"
            ).isEqualTo(agentPsr)
            return this
        }

        fun hasAgentPsrCloseTo(agentPsr: Double, offset: Offset<Double>): SpanAssert {
            val actualPsrValue = actualSpan.getDouble(AGENT_PSR_KEY)
            assertThat(actualPsrValue).overridingErrorMessage(
                "Expected agentPsr to be close to $agentPsr but was $actualPsrValue for index $index"
            ).isCloseTo(agentPsr, offset)
            return this
        }

        fun hasApplicationId(applicationId: String?): SpanAssert {
            val actualApplicationId = actualSpan.getString(APPLICATION_ID_KEY)
            assertThat(actualApplicationId).overridingErrorMessage(
                "Expected applicationId to be $applicationId but was $actualApplicationId for index $index"
            ).isEqualTo(applicationId)
            return this
        }

        fun hasSessionId(sessionId: String?): SpanAssert {
            val actualSessionId = actualSpan.getString(SESSION_ID_KEY)
            assertThat(actualSessionId).overridingErrorMessage(
                "Expected sessionId to be $sessionId but was $actualSessionId for index $index"
            ).isEqualTo(sessionId)
            return this
        }

        fun hasViewId(viewId: String?): SpanAssert {
            val actualViewId = actualSpan.getString(VIEW_ID_KEY)
            assertThat(actualViewId).overridingErrorMessage(
                "Expected viewId to be $viewId but was $actualViewId for index $index"
            ).isEqualTo(viewId)
            return this
        }

        fun hasGenericMetaValue(key: String, value: String): SpanAssert {
            val formattedKey = GENERIC_META_KEY_FORMAT.format(Locale.US, key)
            val actualKeyValue = actualSpan.getString(formattedKey)
            assertThat(actualKeyValue).overridingErrorMessage(
                "Expected $formattedKey to be $value but was $actualKeyValue for index $index"
            ).isEqualTo(value)
            return this
        }
    }

    companion object {
        private const val SPANS_KEY = "spans"
        private const val ENV_KEY = "env"
        private const val TRACE_ID_KEY = "trace_id"
        private const val SPAN_ID_KEY = "span_id"
        private const val SERVICE_KEY = "service"
        private const val ERROR_KEY = "error"
        private const val NAME_KEY = "name"
        private const val RESOURCE_KEY = "resource"
        private const val USR_KEY = "meta.usr"
        private const val USR_ID_KEY = "meta.usr.id"
        private const val USR_NAME_KEY = "meta.usr.name"
        private const val USR_EMAIL_KEY = "meta.usr.email"
        private const val DURATION_KEY = "duration"
        private const val SOURCE_KEY = "meta._dd.source"
        private const val VERSION_KEY = "meta.version"
        private const val TRACER_VERSION_KEY = "meta.tracer.version"
        private const val SAMPLING_PRIORITY_KEY = "metrics._sampling_priority_v1"
        private const val AGENT_PSR_KEY = "metrics._dd.agent_psr"
        private const val APPLICATION_ID_KEY = "meta._dd.application.id"
        private const val SESSION_ID_KEY = "meta._dd.session.id"
        private const val VIEW_ID_KEY = "meta._dd.view.id"
        private const val GENERIC_META_KEY_FORMAT = "meta.%s"

        fun assertThat(actual: JsonObject): SpansPayloadAssert {
            return SpansPayloadAssert(actual)
        }
    }
}
