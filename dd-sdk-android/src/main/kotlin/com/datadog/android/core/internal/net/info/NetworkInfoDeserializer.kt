/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net.info

import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.utils.errorWithTelemetry
import com.google.gson.JsonParseException
import java.util.Locale

internal class NetworkInfoDeserializer(
    private val internalLogger: Logger
) : Deserializer<NetworkInfo> {

    override fun deserialize(model: String): NetworkInfo? {
        return try {
            NetworkInfo.fromJson(model)
        } catch (e: JsonParseException) {
            internalLogger.errorWithTelemetry(
                DESERIALIZE_ERROR_MESSAGE_FORMAT.format(
                    Locale.US,
                    model
                ),
                e
            )
            null
        }
    }

    companion object {
        const val DESERIALIZE_ERROR_MESSAGE_FORMAT =
            "Error while trying to deserialize the serialized NetworkInfo: %s"
    }
}
