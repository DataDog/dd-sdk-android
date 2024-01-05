/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing.assertj

import com.datadog.opentracing.DDSpanContext
import java.math.BigInteger
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class DDSpanContextAssert(actual: DDSpanContext) :
    AbstractObjectAssert<DDSpanContextAssert, DDSpanContext>(
        actual,
        DDSpanContextAssert::class.java
    ) {

    fun hasSpanId(spanId: BigInteger): DDSpanContextAssert {
        assertThat(actual.spanId)
            .overridingErrorMessage(
                "Expected span context to have spanId: $spanId" +
                    " but instead was: ${actual.spanId}"
            )
            .isEqualTo(spanId)
        return this
    }

    fun hasTraceId(traceId: BigInteger): DDSpanContextAssert {
        assertThat(actual.traceId)
            .overridingErrorMessage(
                "Expected span context to have traceId: $traceId" +
                    " but instead was: ${actual.traceId}"
            )
            .isEqualTo(traceId)
        return this
    }

    fun hasParentId(parentId: BigInteger): DDSpanContextAssert {
        assertThat(actual.parentId)
            .overridingErrorMessage(
                "Expected span context to have parentId: $parentId" +
                    " but instead was: ${actual.parentId}"
            )
            .isEqualTo(parentId)
        return this
    }

    fun hasServiceName(serviceName: String?): DDSpanContextAssert {
        assertThat(actual.serviceName)
            .overridingErrorMessage(
                "Expected span context to have serviceName: $serviceName" +
                    " but instead was: ${actual.serviceName}"
            )
            .isEqualTo(serviceName)
        return this
    }

    fun hasResourceName(resourceName: String): DDSpanContextAssert {
        assertThat(actual.resourceName)
            .overridingErrorMessage(
                "Expected span context to have resourceName: $resourceName" +
                    " but instead was: ${actual.resourceName}"
            )
            .isEqualTo(resourceName)
        return this
    }

    fun hasOperationName(operationName: String): DDSpanContextAssert {
        assertThat(actual.operationName)
            .overridingErrorMessage(
                "Expected span context to have operationName: $operationName" +
                    " but instead was: ${actual.operationName}"
            )
            .isEqualTo(operationName)
        return this
    }

    fun hasOrigin(origin: String): DDSpanContextAssert {
        assertThat(actual.origin)
            .overridingErrorMessage(
                "Expected span context to have origin: $origin" +
                    " but instead was: ${actual.origin}"
            )
            .isEqualTo(origin)
        return this
    }

    fun hasSpanType(spanType: String): DDSpanContextAssert {
        assertThat(actual.spanType)
            .overridingErrorMessage(
                "Expected span context to have spanType: $spanType" +
                    " but instead was: ${actual.spanType}"
            )
            .isEqualTo(spanType)
        return this
    }

    fun hasErrorFlag(errorFlag: Boolean): DDSpanContextAssert {
        assertThat(actual.errorFlag)
            .overridingErrorMessage(
                "Expected span context to have errorFlag: $errorFlag" +
                    " but instead was: ${actual.errorFlag}"
            )
            .isEqualTo(errorFlag)
        return this
    }

    fun hasSamplingPriority(samplingPriority: Int): DDSpanContextAssert {
        assertThat(actual.samplingPriority)
            .overridingErrorMessage(
                "Expected span context to have samplingPriority: $samplingPriority" +
                    " but instead was: ${actual.samplingPriority}"
            )
            .isEqualTo(samplingPriority)
        return this
    }

    fun hasExactlyBaggageItems(attributes: Map<String, String>): DDSpanContextAssert {
        assertThat(actual.baggageItems)
            .hasSameSizeAs(attributes)
            .containsAllEntriesOf(attributes)
        return this
    }

    fun containsBaggageItems(baggageItems: Map<String, String>): DDSpanContextAssert {
        assertThat(actual.baggageItems)
            .containsAllEntriesOf(baggageItems)
        return this
    }

    fun hasExactlyTags(tags: Map<String, Any>): DDSpanContextAssert {
        assertThat(actual.tags)
            .hasSameSizeAs(tags)
            .containsAllEntriesOf(tags)
        return this
    }

    fun containsTags(tags: Map<String, Any>): DDSpanContextAssert {
        assertThat(actual.tags)
            .containsAllEntriesOf(tags)
        return this
    }

    fun doesNotContainTags(vararg keys: String): DDSpanContextAssert {
        assertThat(actual.tags)
            .doesNotContainKeys(*keys)
        return this
    }

    fun hasExactlyMetrics(metrics: Map<String, Number>): DDSpanContextAssert {
        assertThat(actual.metrics)
            .hasSameSizeAs(metrics)
            .containsAllEntriesOf(metrics)
        return this
    }

    fun doesNotContainMetrics(vararg keys: String): DDSpanContextAssert {
        assertThat(actual.metrics)
            .doesNotContainKeys(*keys)
        return this
    }

    companion object {
        internal fun assertThat(actual: DDSpanContext): DDSpanContextAssert {
            return DDSpanContextAssert(actual)
        }
    }
}
