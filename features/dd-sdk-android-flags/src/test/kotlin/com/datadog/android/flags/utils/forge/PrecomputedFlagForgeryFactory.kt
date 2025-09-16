/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.utils.forge

import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlagConstants
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.json.JSONObject

internal class PrecomputedFlagForgeryFactory : ForgeryFactory<PrecomputedFlag> {
    override fun getForgery(forge: Forge): PrecomputedFlag {
        val variationType = forge.anElementFrom(PrecomputedFlagConstants.VariationType.ALL_VALUES)
        val variationValue = when (variationType) {
            PrecomputedFlagConstants.VariationType.BOOLEAN -> forge.aBool().toString()
            PrecomputedFlagConstants.VariationType.STRING -> forge.anAlphabeticalString()
            PrecomputedFlagConstants.VariationType.INTEGER -> forge.anInt().toString()
            PrecomputedFlagConstants.VariationType.DOUBLE -> forge.aDouble().toString()
            PrecomputedFlagConstants.VariationType.JSON -> JSONObject().put(
                "key",
                forge.anAlphabeticalString()
            ).toString()
            else -> forge.anAlphabeticalString()
        }

        return PrecomputedFlag(
            variationType = variationType,
            variationValue = variationValue,
            doLog = forge.aBool(),
            allocationKey = forge.anAlphabeticalString(),
            variationKey = forge.anAlphabeticalString(),
            extraLogging = forge.aNullable {
                JSONObject().apply {
                    repeat(forge.anInt(0, 5)) {
                        put(forge.anAlphabeticalString(), forge.anAlphabeticalString())
                    }
                }
            } ?: JSONObject(),
            reason = forge.anElementFrom(PrecomputedFlagConstants.Reason.ALL_VALUES)
        )
    }
}
