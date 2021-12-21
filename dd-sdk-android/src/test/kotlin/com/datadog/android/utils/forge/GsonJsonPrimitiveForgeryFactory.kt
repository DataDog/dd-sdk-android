/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.google.gson.JsonPrimitive
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class GsonJsonPrimitiveForgeryFactory : ForgeryFactory<JsonPrimitive> {

    override fun getForgery(forge: Forge): JsonPrimitive {
        return forge.anElementFrom(
            JsonPrimitive(forge.aBool()),
            JsonPrimitive(forge.anInt()),
            // TODO RUMM-1531 put it back once proper JSON assertions are ready
            // JsonPrimitive(forge.aFloat()),
            JsonPrimitive(forge.aLong()),
            JsonPrimitive(forge.aDouble()),
            JsonPrimitive(forge.anAlphabeticalString()),
            JsonPrimitive(forge.anHexadecimalString()),
            JsonPrimitive(forge.aNumericalString()),
            JsonPrimitive("")
        )
    }
}
