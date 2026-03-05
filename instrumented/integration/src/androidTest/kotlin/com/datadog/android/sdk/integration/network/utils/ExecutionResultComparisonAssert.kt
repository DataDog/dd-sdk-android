/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.network.utils

import com.datadog.android.sdk.integration.network.models.ClientExecutionResult
import com.datadog.android.sdk.integration.network.models.TestRequest
import com.datadog.android.trace.api.span.DatadogSpan
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class ExecutionResultComparisonAssert(
    actual: Map<String, ClientExecutionResult>,
    private val request: TestRequest
) :
    AbstractObjectAssert<ExecutionResultComparisonAssert, Map<String, ClientExecutionResult>>(
        actual,
        ExecutionResultComparisonAssert::class.java
    ) {

    fun haveRequestMethod(expected: String) = apply {
        actual.values.forEach {
            assertThat(it.request?.method)
                .overridingErrorMessage {
                    "Expected client ${it.name} to have request method:" +
                        " $expected but was ${it.request?.method}. ${requestState()}"
                }
                .isEqualTo(expected)
        }
    }

    fun haveRequestUrl(expected: String) = apply {
        actual.values.forEach {
            assertThat(it.request?.url)
                .overridingErrorMessage {
                    "Expected client ${it.name} to have request url: " +
                        "$expected but was ${it.request?.url}. ${requestState()}"
                }
                .isEqualTo(expected)
        }
    }

    fun haveExpectedClients() = apply {
        assertThat(actual.keys)
            .overridingErrorMessage {
                "Expected composite execution result to have clients: " +
                    "$EXPECTED_CLIENTS but was ${actual.keys}. ${requestState()}"
            }
            .containsAll(EXPECTED_CLIENTS)
    }

    fun haveResponseStatusCode(expected: Int) = apply {
        actual.values.forEach {
            assertThat(it.response?.statusCode)
                .overridingErrorMessage {
                    "Expected client ${it.name} to have response status code: $expected " +
                        "but was ${it.response?.statusCode}. ${requestState()}"
                }
                .isEqualTo(expected)
        }
    }

    fun haveSameStatusCode() = apply {
        actual.values.forEachClientResultPair { client1Result, client2Result ->
            assertThat(client1Result.response?.statusCode)
                .overridingErrorMessage {
                    "Expected that all composite execution results to have same response status code, " +
                        "but discrepancy found:" +
                        "${client1Result.name}=${client1Result.response?.statusCode}, " +
                        "${client2Result.name}=${client2Result.response?.statusCode}. ${requestState()}"
                }
                .isEqualTo(client2Result.response?.statusCode)
        }
    }

    fun haveSameSpanCount() = apply {
        actual.values.forEachClientResultPair { client1Result, client2Result ->
            assertThat(client1Result.collectedSpans.size)
                .overridingErrorMessage {
                    "Expected that all composite execution results to have same response span count, " +
                        "but discrepancy found: " +
                        "${client1Result.name}=${client1Result.collectedSpans.size}, " +
                        "${client2Result.name}=${client2Result.collectedSpans.size}. ${requestState()}"
                }
                .isEqualTo(client2Result.collectedSpans.size)
        }
    }

    fun haveSameSpanStructure() = apply {
        actual.values.forEachClientResultPair { client1Result, client2Result ->
            assertThat(client1Result.collectedSpans.hash())
                .overridingErrorMessage {
                    "Expected that all composite execution results to have same span structure, " +
                        "but discrepancy found: " +
                        "${client1Result.name}=${client1Result.collectedSpans.hash()}, " +
                        "${client2Result.name}=${client2Result.collectedSpans.hash()}. ${requestState()}"
                }
                .isEqualTo(client2Result.collectedSpans.hash())
        }
    }

    private fun Collection<ClientExecutionResult>.forEachClientResultPair(
        block: (ClientExecutionResult, ClientExecutionResult) -> Unit
    ) = asSequence()
        .windowed(2, 1)
        .forEach { (client1Result, client2Result) -> block(client1Result, client2Result) }

    private fun requestState() = request.toString()

    companion object {
        private val EXPECTED_CLIENTS = setOf("Cronet", "OkHttp")

        internal fun assertThat(actual: Map<String, ClientExecutionResult>, request: TestRequest) =
            ExecutionResultComparisonAssert(actual, request)

        fun List<DatadogSpan>.associateById(): Map<Long, DatadogSpan> = associateBy { it.context().spanId }

        fun Map<Long, DatadogSpan>.childrenByParentId(): Map<Long, List<DatadogSpan>> =
            values
                .filter { containsKey(it.parentSpanId) }
                .groupBy { checkNotNull(it.parentSpanId) }

        fun DatadogSpan.hash(childrenByParentId: Map<Long, List<DatadogSpan>>): String {
            val props = "{$resourceName,${if (isRootSpan) "root" else ""}}"
            val childHashes = childrenByParentId[context().spanId].orEmpty()
                .map { it.hash(childrenByParentId) }
                .sorted()

            return if (childHashes.isEmpty()) {
                props
            } else {
                "$props[${childHashes.joinToString(", ")}]"
            }
        }

        fun List<DatadogSpan>.hash(): String {
            val spanById = associateById()
            val childrenByParentId = spanById.childrenByParentId()

            val roots = filter { span ->
                span.parentSpanId == null || span.parentSpanId !in spanById
            }

            return roots
                .map { it.hash(childrenByParentId) }
                .sorted()
                .joinToString(",")
        }
    }
}
