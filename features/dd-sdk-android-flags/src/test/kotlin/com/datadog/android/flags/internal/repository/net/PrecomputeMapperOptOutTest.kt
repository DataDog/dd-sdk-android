/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.internal.evaluation.ExperimentationOptOut
import com.datadog.android.flags.model.EvaluationContext
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.mock

internal class PrecomputeMapperOptOutTest {
    private val testedMapper = PrecomputeMapper(mock(InternalLogger::class.java))

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M accept ordinary response W map() {no effective opt-out}`(mode: ExperimentationOptOutMode) {
        // Given
        val request = ExperimentationOptOut(mode).prepare(EvaluationContext("subject"))

        // When
        val result = testedMapper.map(response(null), request.takeIf { it.optedOut })

        // Then
        assertThat(result?.flags?.get("flag")?.variationValue).isEqualTo("true")
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M reject legacy filtered response W map() {no effective opt-out}`(mode: ExperimentationOptOutMode) {
        // Given
        val request = ExperimentationOptOut(mode).prepare(EvaluationContext("subject"))
        val options = JSONObject().put("experimentation_opt_out", true)

        // When
        val result = testedMapper.map(response(options), request.takeIf { it.optedOut })

        // Then
        assertThat(result).isNull()
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M reject future filtered response W map() {no effective opt-out}`(mode: ExperimentationOptOutMode) {
        // Given
        val request = ExperimentationOptOut(mode).prepare(EvaluationContext("subject"))
        val options = JSONObject().put("experimentation_opt_out_mode", "future_experiments_only")

        // When
        val result = testedMapper.map(response(options), request.takeIf { it.optedOut })

        // Then
        assertThat(result).isNull()
    }

    private fun response(options: JSONObject?): String {
        val flag = JSONObject()
            .put("variationType", "boolean")
            .put("variationValue", true)
            .put("doLog", false)
            .put("allocationKey", "ordinary")
            .put("variationKey", "on")
            .put("extraLogging", JSONObject())
            .put("reason", "TARGETING_MATCH")
        val attributes = JSONObject().put("flags", JSONObject().put("flag", flag))
        if (options != null) attributes.put("evaluation_options", options)
        return JSONObject().put("data", JSONObject().put("attributes", attributes)).toString()
    }
}
