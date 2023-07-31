/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.forge

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.json.JSONArray

/**
 *  A [ForgeryFactory] generating a random [JSONArray] instance.
 */
class OrgJSONArrayForgeryFactory : ForgeryFactory<JSONArray> {

    /** @inheritDoc */
    override fun getForgery(forge: Forge): JSONArray {
        return forge.anElementFrom(
            JSONArray(),
            forge.aStringArray(),
            forge.anIntArray(),
            forge.aDoubleArray(),
            forge.aBooleanArray()
        )
    }

    // region Internal

    private fun Forge.aStringArray(): JSONArray {
        return JSONArray().apply {
            aList { anAlphabeticalString() }
                .forEach { put(it) }
        }
    }

    private fun Forge.anIntArray(): JSONArray {
        return JSONArray().apply {
            aList { anInt() }
                .forEach { put(it) }
        }
    }

    private fun Forge.aDoubleArray(): JSONArray {
        return JSONArray().apply {
            aList { aDouble() }
                .forEach { put(it) }
        }
    }

    private fun Forge.aBooleanArray(): JSONArray {
        return JSONArray().apply {
            aList { aBool() }
                .forEach { put(it) }
        }
    }

    // endregion
}
