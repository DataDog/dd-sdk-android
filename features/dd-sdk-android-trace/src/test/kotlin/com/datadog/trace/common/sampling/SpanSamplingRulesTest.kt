/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.common.sampling

import com.datadog.trace.api.sampling.SamplingRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal open class SpanSamplingRulesTest {

    @Test
    fun `Deserialize empty list of Span Sampling Rules from JSON`() {
        // Given
        val rules = deserializeRules("[]")

        // Then
        assertThat(rules.isEmpty()).isTrue()
    }

    @Test
    fun `Deserialize Span Sampling Rules from JSON`() {
        // Given
        val rules = deserializeRules(
            """[
        {"service": "service-name", "name": "operation-name", "resource": "resource-name", "tags":
            {"tag-name1": "tag-pattern1",
            "tag-name2": "tag-pattern2"},
            "sample_rate": 0.0, "max_per_second": 10.0},
        {},
        {"service": "", "name": "", "resource": "", "tags": {}},
        {"service": null, "name": null, "resource": null, "tags": null, "sample_rate": null, 
        "sample_rate": null, "max_per_second": null},

        {"sample_rate": 0.25},
        {"sample_rate": 0.5},
        {"sample_rate": 0.75},
        {"sample_rate": 1},

        {"max_per_second": 0.2},
        {"max_per_second": 1.0},
        {"max_per_second": 10},
        {"max_per_second": 10.123},
        {"max_per_second": 10000}
    ]"""
        ).rules
        var ruleIndex = 0

        // Then
        assertThat(rules.size).isEqualTo(13)

        // Test a complete rule
        assertThat(rules[ruleIndex].service).isEqualTo("service-name")
        assertThat(rules[ruleIndex].name).isEqualTo("operation-name")
        assertThat(rules[ruleIndex].resource).isEqualTo("resource-name")
        assertThat(rules[ruleIndex].tags).isEqualTo(mapOf("tag-name1" to "tag-pattern1", "tag-name2" to "tag-pattern2"))
        assertThat(rules[ruleIndex].sampleRate).isEqualTo(0.0)
        assertThat(rules[ruleIndex++].maxPerSecond).isEqualTo(10)

        // Test default values with an empty rule
        assertThat(rules[ruleIndex].service).isEqualTo(SamplingRule.MATCH_ALL)
        assertThat(rules[ruleIndex].name).isEqualTo(SamplingRule.MATCH_ALL)
        assertThat(rules[ruleIndex].resource).isEqualTo(SamplingRule.MATCH_ALL)
        assertThat(rules[ruleIndex].tags).isEmpty()
        assertThat(rules[ruleIndex].sampleRate).isEqualTo(1.0)
        assertThat(rules[ruleIndex++].maxPerSecond).isEqualTo(Integer.MAX_VALUE)

        // Test rule with empty values
        assertThat(rules[ruleIndex].service).isEqualTo("")
        assertThat(rules[ruleIndex].name).isEqualTo("")
        assertThat(rules[ruleIndex].resource).isEqualTo("")
        assertThat(rules[ruleIndex].tags).isEmpty()
        assertThat(rules[ruleIndex].sampleRate).isEqualTo(1.0)
        assertThat(rules[ruleIndex++].maxPerSecond).isEqualTo(Integer.MAX_VALUE)

        // Test rule with null values
        assertThat(rules[ruleIndex].service).isEqualTo(SamplingRule.MATCH_ALL)
        assertThat(rules[ruleIndex].name).isEqualTo(SamplingRule.MATCH_ALL)
        assertThat(rules[ruleIndex].resource).isEqualTo(SamplingRule.MATCH_ALL)
        assertThat(rules[ruleIndex].tags).isEmpty()
        assertThat(rules[ruleIndex].sampleRate).isEqualTo(1.0)
        assertThat(rules[ruleIndex++].maxPerSecond).isEqualTo(Integer.MAX_VALUE)

        // Test different sample rate values
        assertThat(rules[ruleIndex++].sampleRate).isEqualTo(0.25)
        assertThat(rules[ruleIndex++].sampleRate).isEqualTo(0.5)
        assertThat(rules[ruleIndex++].sampleRate).isEqualTo(0.75)
        assertThat(rules[ruleIndex++].sampleRate).isEqualTo(1.0)

        // Test different max per second values
        assertThat(rules[ruleIndex++].maxPerSecond).isEqualTo(1)
        assertThat(rules[ruleIndex++].maxPerSecond).isEqualTo(1)
        assertThat(rules[ruleIndex++].maxPerSecond).isEqualTo(10)
        assertThat(rules[ruleIndex++].maxPerSecond).isEqualTo(10)
        assertThat(rules[ruleIndex].maxPerSecond).isEqualTo(10000)
    }

    @ParameterizedTest
    @ValueSource(strings = ["-0.1", "-11", "1.2", "100", "\"zero\"", "\"\""])
    fun `Skip Span Sampling Rules with invalid sample_rate values`(rate: String) {
        // Given
        val rules =
            deserializeRules(
                """[
            {"service": "usersvc", "name": "healthcheck", "sample_rate": $rate}
        ]"""
            )

        // Then
        assertThat(rules.isEmpty()).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-11", "\"zero\"", "\"\""])
    fun `Skip Span Sampling Rules with invalid max_per_second values`(limit: String) {
        // Given
        val rules =
            deserializeRules(
                """[{"service": "usersvc", "name": "healthcheck", "max_per_second": $limit}]"""
            )

        // Then
        assertThat(rules.isEmpty()).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["[", "{\"service\": \"usersvc\",}", ""])
    fun `Skip Span Sampling Rules when incorrect JSON provided`(jsonRules: String) {
        val rules = deserializeRules(jsonRules)
        assertThat(rules.isEmpty()).isTrue()
    }

    @Test
    fun `Render JsonRule correctly when toString() is called`() {
        // Given
        val json = "{\"max_per_second\":\"10\",\"name\":\"name\",\"resource\":\"resource\"," +
            "\"sample_rate\":\"0.5\",\"service\":\"service\",\"tags\":{\"a\":\"b\",\"foo\":\"bar\"}}"

        // When
        val jsonRule = deserializeRules("[$json]").rules[0].asStringJsonRule()

        // Then
        assertThat(jsonRule.toString()).isEqualTo(json)
    }

    @Test
    fun `Keep only valid rules when invalid rules are present`() {
        // Given
        val rules = SpanSamplingRules.deserialize(
            """[
                {"service": "usersvc", "name": "healthcheck", "sample_rate": 0.5},
                {"service": "usersvc", "name": "healthcheck2", "sample_rate": 200}
                ]"""
        )

        // Then
        assertThat(rules.rules.size).isEqualTo(1)
    }

    internal open fun deserializeRules(jsonRules: String): SpanSamplingRules {
        return SpanSamplingRules.deserialize(jsonRules)
    }
}
