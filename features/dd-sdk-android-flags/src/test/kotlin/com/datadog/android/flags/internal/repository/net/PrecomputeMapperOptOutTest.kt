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
    @ValueSource(strings = ["1", "true", "\"\"", "{}", "[]"])
    fun `M reject malformed mode W map() {immediate, legacy acknowledgement}`(mode: String) {
        // Given
        val request = ExperimentationOptOut(ExperimentationOptOutMode.IMMEDIATE)
            .prepare(EvaluationContext("subject", mapOf(ExperimentationOptOut.ATTRIBUTE to "2026-10-21T10:00:00Z")))
        val options = JSONObject("{\"experimentation_opt_out\":true,\"experimentation_opt_out_mode\":$mode}")

        // When
        val result = testedMapper.map(response(options), request)

        // Then
        assertThat(result).isNull()
    }

    @ParameterizedTest
    @CsvSource(
        "DISABLED,,'{\"experimentation_opt_out\":true}',disabled,none",
        "DISABLED,2026-10-21T10:00:00Z,'{\"experimentation_opt_out\":true}',disabled,none",
        "IMMEDIATE,,'{\"experimentation_opt_out\":true}',immediate,none",
        "FUTURE_EXPERIMENTS_ONLY,,'{\"experimentation_opt_out_mode\":\"immediate\"}',future_experiments_only,none",
        "IMMEDIATE,2026-10-21T10:00:00Z,,immediate,applied",
        "FUTURE_EXPERIMENTS_ONLY,2026-10-21T10:00:00Z,,future_experiments_only,applied"
    )
    fun `M report configured mode and policy W map() {response rejected}`(
        mode: ExperimentationOptOutMode,
        timestamp: String?,
        options: String?,
        expectedMode: String,
        expectedPolicy: String
    ) {
        // Given
        val mapper = PrecomputeMapper(mockInternalLogger, mode)
        val attributes = if (timestamp == null) emptyMap() else mapOf(ExperimentationOptOut.ATTRIBUTE to timestamp)
        val request = ExperimentationOptOut(mode).prepare(EvaluationContext("subject", attributes))

        // When
        val result = mapper.map(response(options?.let { JSONObject(it) }), request.takeIf { it.optedOut })

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.TELEMETRY,
            "Rejected experimentation opt-out acknowledgement: mode=$expectedMode policy=$expectedPolicy",
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
