/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.rum.internal.generated.DdSdkAndroidRumLogger
import com.google.gson.JsonParseException

internal class RumEventMetaDeserializer(
    private val internalLogger: InternalLogger
) : Deserializer<ByteArray, RumEventMeta> {

    private val logger = DdSdkAndroidRumLogger(internalLogger)

    override fun deserialize(model: ByteArray): RumEventMeta? {
        if (model.isEmpty()) return null

        return try {
            RumEventMeta.fromJson(String(model, Charsets.UTF_8), internalLogger)
        } catch (e: JsonParseException) {
            logger.logRumEventMetaDeserializationError(throwable = e)
            null
        }
    }
}
