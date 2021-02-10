/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net.info

import com.datadog.android.core.internal.domain.Deserializer
import com.datadog.android.core.internal.utils.sdkLogger
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal class NetworkInfoDeserializer : Deserializer<NetworkInfo> {

    override fun deserialize(model: String): NetworkInfo? {
        return try {
            val jsonObject = JsonParser.parseString(model).asJsonObject
            val connectivity = jsonObject.get(NetworkInfoSerializer.CONNECTIVITY)?.let {
                NetworkInfo.Connectivity.valueOf(it.asString)
            } ?: NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED
            val carrierId = jsonObject.get(NetworkInfoSerializer.CARRIER_ID).asInt
            val upKbs = jsonObject.get(NetworkInfoSerializer.UP_KBPS).asInt
            val downKbs = jsonObject.get(NetworkInfoSerializer.DOWN_KBPS).asInt
            val strength = jsonObject.get(NetworkInfoSerializer.STRENGTH).asInt
            val carrierName = jsonObject.get(NetworkInfoSerializer.CARRIER_NAME)?.asString
            val cellularTechnology =
                jsonObject.get(NetworkInfoSerializer.CELLULAR_TECHNOLOGY)?.asString

            NetworkInfo(
                connectivity,
                carrierName,
                carrierId,
                upKbs,
                downKbs,
                strength,
                cellularTechnology
            )
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
