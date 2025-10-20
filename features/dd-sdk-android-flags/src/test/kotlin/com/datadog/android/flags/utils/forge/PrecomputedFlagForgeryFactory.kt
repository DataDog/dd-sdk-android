/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.utils.forge

import com.datadog.android.flags.featureflags.internal.model.FlagReason
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.internal.model.VariationType
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.json.JSONObject

internal class PrecomputedFlagForgeryFactory : ForgeryFactory<PrecomputedFlag> {
    override fun getForgery(forge: Forge): PrecomputedFlag {
        val variationTypeEnum = forge.anElementFrom(*VariationType.values())

        // Use the same pattern as DatadogFlagsProviderTest
        val variationType = when (variationTypeEnum) {
            VariationType.BOOLEAN -> VariationType.BOOLEAN.value
            VariationType.STRING -> VariationType.STRING.value
            VariationType.INTEGER -> VariationType.INTEGER.value
            VariationType.NUMBER -> VariationType.NUMBER.value
            VariationType.FLOAT -> VariationType.FLOAT.value
            VariationType.OBJECT -> VariationType.OBJECT.value
        }

        val variationValue = when (variationTypeEnum) {
            VariationType.BOOLEAN -> forge.aBool().toString()
            VariationType.STRING -> forge.anAlphabeticalString()
            VariationType.INTEGER -> forge.anInt().toString()
            VariationType.NUMBER -> forge.aDouble().toString()
            VariationType.FLOAT -> forge.aFloat().toString()
            VariationType.OBJECT -> JSONObject().put(
                "key",
                forge.anAlphabeticalString()
            ).toString()
        }

        val reasonEnum = forge.anElementFrom(*FlagReason.values())
        val reason = when (reasonEnum) {
            FlagReason.DEFAULT -> FlagReason.DEFAULT.value
            FlagReason.TARGETING_MATCH -> FlagReason.TARGETING_MATCH.value
            FlagReason.RULE_MATCH -> FlagReason.RULE_MATCH.value
            FlagReason.PREREQUISITE_FAILED -> FlagReason.PREREQUISITE_FAILED.value
            FlagReason.ERROR -> FlagReason.ERROR.value
        }

        return PrecomputedFlag(
            variationType = variationType,
            variationValue = variationValue,
            doLog = true, // Default to true - tests can override if needed
            allocationKey = forge.anAlphabeticalString(),
            variationKey = forge.anAlphabeticalString(),
            extraLogging = forge.aNullable {
                JSONObject().apply {
                    repeat(forge.anInt(0, 5)) {
                        put(forge.anAlphabeticalString(), forge.anAlphabeticalString())
                    }
                }
            } ?: JSONObject(),
            reason = reason
        )
    }
}
