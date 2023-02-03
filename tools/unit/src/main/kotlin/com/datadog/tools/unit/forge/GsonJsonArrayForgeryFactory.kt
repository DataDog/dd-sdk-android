/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.forge

import com.google.gson.JsonArray
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

/**
 *  A [ForgeryFactory] generating a random [JsonArray] instance.
 */
class GsonJsonArrayForgeryFactory : ForgeryFactory<JsonArray> {

    /** @inheritDoc */
    override fun getForgery(forge: Forge): JsonArray {
        return forge.anElementFrom(
            JsonArray(),
            forge.aStringArray(),
            forge.anIntArray(),
            forge.aDoubleArray(),
            forge.aBooleanArray()
        )
    }

    // region Internal

    private fun Forge.aStringArray(): JsonArray {
        return JsonArray().apply {
            aList { anAlphabeticalString() }
                .forEach { add(it) }
        }
    }

    private fun Forge.anIntArray(): JsonArray {
        return JsonArray().apply {
            aList { anInt() }
                .forEach { add(it) }
        }
    }

    private fun Forge.aDoubleArray(): JsonArray {
        return JsonArray().apply {
            aList { aDouble() }
                .forEach { add(it) }
        }
    }

    private fun Forge.aBooleanArray(): JsonArray {
        return JsonArray().apply {
            aList { aBool() }
                .forEach { add(it) }
        }
    }

    // endregion
}
