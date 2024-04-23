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

internal class TraceSamplingRulesTest {

    @Test
    fun `Deserialize empty list of Trace Sampling Rules from JSON`() {
        // Given
        val rules = TraceSamplingRules.deserialize("[]")

        // Then
        assertThat(rules.isEmpty()).isTrue()
    }

    @Test
    fun `Deserialize Trace Sampling Rules from JSON`() {
        // When
        val rules = TraceSamplingRules.deserialize(
            """[
            {"service": "service-name", "name": "operation-name", "resource": "resource-name", 
            "tags":{"tag-name1": "tag-pattern1", "tag-name2": "tag-pattern2"},"sample_rate": 0.0},
            {},
            {"service": "", "name": "", "resource": "", "tags": {}}, 
            {"service": null, "name": null, "resource": null, "tags": null, "sample_rate": null}, 
            {"sample_rate": 0.25},
            {"sample_rate": 0.5}, 
            {"sample_rate": 0.75},
            {"sample_rate": 1}
        ]"""
        ).rules

        // Then
        assertThat(rules).hasSize(8)

        // Test a complete rule
        var ruleIndex = 0
        assertThat(rules[ruleIndex].service).isEqualTo("service-name")
        assertThat(rules[ruleIndex].name).isEqualTo("operation-name")
        assertThat(rules[ruleIndex].resource).isEqualTo("resource-name")
        assertThat(rules[ruleIndex].tags).isEqualTo(mapOf("tag-name1" to "tag-pattern1", "tag-name2" to "tag-pattern2"))
        assertThat(rules[ruleIndex++].sampleRate).isEqualTo(0.0)

        // Test default values with an empty rule
        assertThat(rules[ruleIndex].service).isEqualTo(SamplingRule.MATCH_ALL)
        assertThat(rules[ruleIndex].name).isEqualTo(SamplingRule.MATCH_ALL)
        assertThat(rules[ruleIndex].resource).isEqualTo(SamplingRule.MATCH_ALL)
        assertThat(rules[ruleIndex].tags).isEmpty()
        assertThat(rules[ruleIndex++].sampleRate).isEqualTo(1.0)

        // Test rule with empty values
        assertThat(rules[ruleIndex].service).isEqualTo("")
        assertThat(rules[ruleIndex].name).isEqualTo("")
        assertThat(rules[ruleIndex].resource).isEqualTo("")
        assertThat(rules[ruleIndex].tags).isEmpty()
        assertThat(rules[ruleIndex++].sampleRate).isEqualTo(1.0)

        // Test rule with null values
        assertThat(rules[ruleIndex].service).isEqualTo(SamplingRule.MATCH_ALL)
        assertThat(rules[ruleIndex].name).isEqualTo(SamplingRule.MATCH_ALL)
        assertThat(rules[ruleIndex].resource).isEqualTo(SamplingRule.MATCH_ALL)
        assertThat(rules[ruleIndex].tags).isEmpty()
        assertThat(rules[ruleIndex++].sampleRate).isEqualTo(1.0)

        // Test different sample rate values
        assertThat(rules[ruleIndex++].sampleRate).isEqualTo(0.25)
        assertThat(rules[ruleIndex++].sampleRate).isEqualTo(0.5)
        assertThat(rules[ruleIndex++].sampleRate).isEqualTo(0.75)
        assertThat(rules[ruleIndex].sampleRate).isEqualTo(1.0)
    }

    @ParameterizedTest
    @ValueSource(strings = ["-0.1", "-11", "1.2", "100", "\"zero\"", "\"\"", "astring"])
    fun `Skip Trace Sampling Rules with invalid sample rate values`(rate: String) {
        // Given
        val rules = TraceSamplingRules.deserialize(
            """[
            {"service": "usersvc", "name": "healthcheck", "sample_rate": $rate}
        ]"""
        )

        // Then
        assertThat(rules.isEmpty()).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["[", "{\"service\": \"usersvc\",}", ""])
    fun `Skip Trace Sampling Rules when incorrect JSON provided`(jsonRules: String) {
        // Given
        val rules = TraceSamplingRules.deserialize(jsonRules)

        // Then
        assertThat(rules.isEmpty()).isTrue()
    }

    @Test
    fun `Render JsonRule correctly when toString() is called`() {
        // Given
        val json = "{\"name\":\"name\",\"resource\":\"resource\",\"sample_rate\":\"0.5\"," +
            "\"service\":\"service\",\"tags\":{\"a\":\"b\",\"foo\":\"bar\"}}"
        val jsonRule = TraceSamplingRules.deserialize("[$json]").rules[0].asStringJsonRule()

        // Then
        assertThat(jsonRule).isEqualTo(json)
    }

    @Test
    fun `Keep only valid rules when invalid rules are present`() {
        val rules = TraceSamplingRules.deserialize(
            """[
            {"service": "usersvc", "name": "healthcheck", "sample_rate": 0.5},
            {"service": "usersvc", "name": "healthcheck", "sample_rate": 200}
        ]"""
        )
        assertThat(rules.rules).hasSize(1)
    }
}
