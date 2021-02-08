/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net.info

import com.datadog.android.core.internal.domain.Serializer
import com.google.gson.JsonObject

internal class NetworkInfoSerializer : Serializer<NetworkInfo> {

    override fun serialize(model: NetworkInfo): String {
        val jsonObject = JsonObject()
        jsonObject.addProperty(CONNECTIVITY, model.connectivity.toString())
        jsonObject.addProperty(CARRIER_ID, model.carrierId)
        jsonObject.addProperty(UP_KBPS, model.upKbps)
        jsonObject.addProperty(DOWN_KBPS, model.downKbps)
        jsonObject.addProperty(STRENGTH, model.strength)
        if (!model.carrierName.isNullOrEmpty()) {
            jsonObject.addProperty(CARRIER_NAME, model.carrierName)
        }
        if (!model.cellularTechnology.isNullOrEmpty()) {
            jsonObject.addProperty(CELLULAR_TECHNOLOGY, model.cellularTechnology)
        }
        return jsonObject.toString()
    }

    companion object {
        internal const val CONNECTIVITY = "connectivity"
        internal const val CARRIER_NAME = "carrier_name"
        internal const val CARRIER_ID = "carrier_id"
        internal const val UP_KBPS = "up_kbps"
        internal const val DOWN_KBPS = "down_kbps"
        internal const val STRENGTH = "strength"
        internal const val CELLULAR_TECHNOLOGY = "cellular_technology"
    }
}
