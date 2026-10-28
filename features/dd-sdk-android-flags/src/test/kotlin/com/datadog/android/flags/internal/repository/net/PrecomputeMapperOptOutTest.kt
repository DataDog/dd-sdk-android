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
import com.datadog.android.utils.verifyLog
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock

internal class PrecomputeMapperOptOutTest {
    private val mockInternalLogger = mock(InternalLogger::class.java)
    private val testedMapper = PrecomputeMapper(mockInternalLogger)

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

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class)
    fun `M reject immediate filtered response W map() {no effective opt-out}`(mode: ExperimentationOptOutMode) {
        // Given
        val request = ExperimentationOptOut(mode).prepare(EvaluationContext("subject"))
        val options = JSONObject().put("experimentation_opt_out_mode", "immediate")

        // When
        val result = testedMapper.map(response(options), request.takeIf { it.optedOut })

        // Then
        assertThat(result).isNull()
    }

    @ParameterizedTest
    @CsvSource(
        "true,,true",
        "true,immediate,true",
        "true,future_experiments_only,false",
        "true,some_new_mode,false",
        ",immediate,false",
        "false,immediate,false"
    )
    fun `M apply immediate admission rule W map() {opted out}`(legacy: Boolean?, mode: String?, admitted: Boolean) {
        // Given
        val request = ExperimentationOptOut(ExperimentationOptOutMode.IMMEDIATE)
            .prepare(EvaluationContext("subject", mapOf(ExperimentationOptOut.ATTRIBUTE to "2026-10-21T10:00:00Z")))
        val options = JSONObject()
        if (legacy != null) options.put("experimentation_opt_out", legacy)
        if (mode != null) options.put("experimentation_opt_out_mode", mode)

        // When
        val result = testedMapper.map(response(options), request)

        // Then
        assertThat(result != null).isEqualTo(admitted)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "{\"experimentation_opt_out_mode\":\"disabled\"}",
            "{\"experimentation_opt_out_mode\":\"some_new_mode\"}",
            "{\"experimentation_opt_out_mode\":1}",
            "{\"experimentation_opt_out\":false}"
        ]
    )
    fun `M admit unrecognized acknowledgement W map() {no effective opt-out}`(options: String) {
        // When
        val result = testedMapper.map(response(JSONObject(options)), null)

        // Then
        assertThat(result?.flags?.get("flag")?.variationValue).isEqualTo("true")
    }

    @ParameterizedTest
    @EnumSource(ExperimentationOptOutMode::class, names = ["IMMEDIATE", "FUTURE_EXPERIMENTS_ONLY"])
    fun `M report rejected policy W map() {acknowledgement absent}`(mode: ExperimentationOptOutMode) {
        // Given
        val request = ExperimentationOptOut(mode)
            .prepare(EvaluationContext("subject", mapOf(ExperimentationOptOut.ATTRIBUTE to "2026-10-21T10:00:00Z")))

        // When
        val result = testedMapper.map(response(null), request)

        // Then
        assertThat(result).isNull()
        val expectedMode = if (mode == ExperimentationOptOutMode.IMMEDIATE) "immediate" else "future_experiments_only"
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.TELEMETRY,
            "Rejected experimentation opt-out acknowledgement: requested=$expectedMode",
            onlyOnce = true
        )
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
