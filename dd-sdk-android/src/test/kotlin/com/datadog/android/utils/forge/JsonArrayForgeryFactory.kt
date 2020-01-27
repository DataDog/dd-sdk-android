/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.google.gson.JsonArray
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class JsonArrayForgeryFactory : ForgeryFactory<JsonArray> {

    override fun getForgery(forge: Forge): JsonArray {
        return forge.anElementFrom(
            JsonArray(),
            forge.aStringArray(),
            forge.anIntArray(),
            forge.aFloatArray()
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

    private fun Forge.aFloatArray(): JsonArray {
        return JsonArray().apply {
            aList { anInt() }
                .forEach { add(it) }
        }
    }

    // endregion
}
