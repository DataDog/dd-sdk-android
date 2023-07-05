/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.forge

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.json.JSONArray
import org.json.JSONObject

/**
 *  A [ForgeryFactory] generating a random [JSONObject] instance.
 */
class OrgJSONObjectForgeryFactory : ForgeryFactory<JSONObject> {

    /** @inheritDoc */
    override fun getForgery(forge: Forge): JSONObject {
        return JSONObject().apply {
            val fieldCount = forge.aTinyInt()
            repeat(fieldCount) {
                put(
                    forge.anAlphabeticalString(),
                    forge.anElementFrom(
                        forge.anInt(),
                        forge.aLong(),
                        forge.aBool(),
                        forge.anAlphaNumericalString(),
                        forge.aDouble(),
                        forge.getForgery<JSONArray>()
                    )
                )
            }
        }
    }
}
