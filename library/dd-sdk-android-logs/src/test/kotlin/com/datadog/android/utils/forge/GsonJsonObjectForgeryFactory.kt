/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

// TODO RUMM-2949 Share forgeries/test configurations between modules
class GsonJsonObjectForgeryFactory : ForgeryFactory<JsonObject> {

    override fun getForgery(forge: Forge): JsonObject {
        return JsonObject().apply {
            val fieldCount = forge.aTinyInt()
            repeat(fieldCount) {
                add(
                    forge.anAlphabeticalString(),
                    forge.anElementFrom(
                        forge.getForgery<JsonPrimitive>(),
                        forge.getForgery<JsonArray>()
                    )
                )
            }
        }
    }
}
