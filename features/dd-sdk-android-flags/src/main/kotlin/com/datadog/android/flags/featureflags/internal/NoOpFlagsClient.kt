/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal

import com.datadog.android.flags.featureflags.FlagsClient
import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.flags.featureflags.model.EvaluationDetails
import org.json.JSONObject

@Suppress("TooManyFunctions") // Required by mobile API spec
internal class NoOpFlagsClient : FlagsClient {
    override fun setEvaluationContext(evaluationContext: EvaluationContext) {
        // No-op implementation - context is ignored
    }

    override fun getBooleanValue(flagKey: String, defaultValue: Boolean): Boolean = defaultValue

    override fun getStringValue(flagKey: String, defaultValue: String): String = defaultValue

    override fun getNumberValue(flagKey: String, defaultValue: Number): Number = defaultValue

    override fun getIntValue(flagKey: String, defaultValue: Int): Int = defaultValue

    override fun getStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject = defaultValue

    override fun getBooleanDetails(flagKey: String, defaultValue: Boolean): EvaluationDetails =
        EvaluationDetails.defaultValue(flagKey, defaultValue)

    override fun getStringDetails(flagKey: String, defaultValue: String): EvaluationDetails =
        EvaluationDetails.defaultValue(flagKey, defaultValue)

    override fun getNumberDetails(flagKey: String, defaultValue: Number): EvaluationDetails =
        EvaluationDetails.defaultValue(flagKey, defaultValue)

    override fun getIntDetails(flagKey: String, defaultValue: Int): EvaluationDetails =
        EvaluationDetails.defaultValue(flagKey, defaultValue)

    override fun getStructureDetails(flagKey: String, defaultValue: JSONObject): EvaluationDetails =
        EvaluationDetails.defaultValue(flagKey, defaultValue)
}
