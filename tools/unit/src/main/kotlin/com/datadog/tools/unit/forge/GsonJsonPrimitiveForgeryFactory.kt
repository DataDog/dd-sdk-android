/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.forge

import com.google.gson.JsonPrimitive
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

/**
 *  A [ForgeryFactory] generating a random [JsonPrimitive] instance.
 */
class GsonJsonPrimitiveForgeryFactory : ForgeryFactory<JsonPrimitive> {

    /** @inheritDoc */
    override fun getForgery(forge: Forge): JsonPrimitive {
        return forge.anElementFrom(
            JsonPrimitive(forge.aBool()),
            JsonPrimitive(forge.anInt()),
            JsonPrimitive(forge.aFloat()),
            JsonPrimitive(forge.aLong()),
            JsonPrimitive(forge.aDouble()),
            JsonPrimitive(forge.anAlphabeticalString()),
            JsonPrimitive(forge.anHexadecimalString()),
            JsonPrimitive(forge.aNumericalString()),
            JsonPrimitive("")
        )
    }
}
