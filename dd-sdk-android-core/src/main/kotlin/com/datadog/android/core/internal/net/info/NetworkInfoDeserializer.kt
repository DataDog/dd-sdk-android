/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net.info

import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.context.NetworkInfo
import com.google.gson.JsonParseException
import java.util.Locale

internal class NetworkInfoDeserializer(
    private val internalLogger: InternalLogger
) : Deserializer<String, NetworkInfo> {

    override fun deserialize(model: String): NetworkInfo? {
        return try {
            NetworkInfo.fromJson(model)
        } catch (e: JsonParseException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
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
            "Error while trying to deserialize the NetworkInfo: %s"
    }
}
