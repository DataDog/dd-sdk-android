/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net.info

import com.datadog.android.core.internal.domain.Deserializer
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.core.model.NetworkInfo
import com.google.gson.JsonParseException

internal class NetworkInfoDeserializer : Deserializer<NetworkInfo> {

    override fun deserialize(model: String): NetworkInfo? {
        return try {
            NetworkInfo.fromJson(model)
        } catch (e: JsonParseException) {
            sdkLogger.e(DESERIALIZE_ERROR_MESSAGE_FORMAT.format(model), e)
            null
        } catch (e: IllegalStateException) {
            sdkLogger.e(DESERIALIZE_ERROR_MESSAGE_FORMAT.format(model), e)
            null
        }
    }

    companion object {
        const val DESERIALIZE_ERROR_MESSAGE_FORMAT =
            "Error while trying to deserialize the serialized NetworkInfo: %s"
    }
}
