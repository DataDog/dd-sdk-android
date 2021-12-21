/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.json.JSONArray
import org.json.JSONObject

class OrgJSONObjectForgeryFactory : ForgeryFactory<JSONObject> {

    override fun getForgery(forge: Forge): JSONObject {
        return JSONObject().apply {
            val fieldCount = forge.aTinyInt()
            for (i in 0..fieldCount) {
                put(
                    forge.anAlphabeticalString(),
                    forge.anElementFrom(
                        forge.anInt(),
                        forge.aLong(),
                        forge.anAlphaNumericalString(),
                        forge.aDouble(),
                        forge.getForgery<JSONArray>()
                    )
                )
            }
        }
    }
}
