/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.context

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.Long
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

/**
 * Holds information about the current network state.
 *
 * @property connectivity the current connectivity
 * @property carrierName information about the network carrier, or null
 * @property carrierId network carrier ID, or null
 * @property upKbps the upload speed in kilobytes per second
 * @property downKbps the download speed in kilobytes per second
 * @property strength the strength of the signal (the unit depends on the type of the signal)
 * @property cellularTechnology the type of cellular technology if known (e.g.: GPRS, LTE, 5G)
 */
data class NetworkInfo(
    val connectivity: Connectivity = Connectivity.NETWORK_NOT_CONNECTED,
    val carrierName: String? = null,
    val carrierId: Long? = null,
    val upKbps: Long? = null,
    val downKbps: Long? = null,
    val strength: Long? = null,
    val cellularTechnology: String? = null
) {
    internal fun toJson(): JsonElement {
        val json = JsonObject()
        json.add("connectivity", connectivity.toJson())
        carrierName?.let { carrierNameNonNull ->
            json.addProperty("carrier_name", carrierNameNonNull)
        }
        carrierId?.let { carrierIdNonNull ->
            json.addProperty("carrier_id", carrierIdNonNull)
        }
        upKbps?.let { upKbpsNonNull ->
            json.addProperty("up_kbps", upKbpsNonNull)
        }
        downKbps?.let { downKbpsNonNull ->
            json.addProperty("down_kbps", downKbpsNonNull)
        }
        strength?.let { strengthNonNull ->
            json.addProperty("strength", strengthNonNull)
        }
        cellularTechnology?.let { cellularTechnologyNonNull ->
            json.addProperty("cellular_technology", cellularTechnologyNonNull)
        }
        return json
    }

    internal companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        @Suppress("StringLiteralDuplication")
        fun fromJson(jsonString: String): NetworkInfo {
            try {
                // JsonParseException is declared in the method signature
                @Suppress("UnsafeThirdPartyFunctionCall")
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type NetworkInfo",
                    e
                )
            }
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        @Suppress("StringLiteralDuplication", "ThrowsCount")
        fun fromJsonObject(jsonObject: JsonObject): NetworkInfo {
            try {
                val connectivity = Connectivity.fromJson(jsonObject.get("connectivity").asString)
                val carrierName = jsonObject.get("carrier_name")?.asString
                val carrierId = jsonObject.get("carrier_id")?.asLong
                val upKbps = jsonObject.get("up_kbps")?.asLong
                val downKbps = jsonObject.get("down_kbps")?.asLong
                val strength = jsonObject.get("strength")?.asLong
                val cellularTechnology = jsonObject.get("cellular_technology")?.asString
                return NetworkInfo(
                    connectivity,
                    carrierName,
                    carrierId,
                    upKbps,
                    downKbps,
                    strength,
                    cellularTechnology
                )
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type NetworkInfo",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type NetworkInfo",
                    e
                )
            } catch (@Suppress("TooGenericExceptionCaught") e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type NetworkInfo",
                    e
                )
            }
        }
    }

    /**
     * The type of connectivity currently available.
     */
    enum class Connectivity(
        private val jsonValue: String
    ) {
        /**
         * The network is not connected.
         */
        NETWORK_NOT_CONNECTED("network_not_connected"),

        /**
         * The network is connected using a Ethernet connection.
         */
        NETWORK_ETHERNET("network_ethernet"),

        /**
         * The network is connected using a WiFi connection.
         */
        NETWORK_WIFI("network_wifi"),

        /**
         * The network is connected using a WiMax connection.
         */
        NETWORK_WIMAX("network_wimax"),

        /**
         * The network is connected using a Bluetooth connection.
         */
        NETWORK_BLUETOOTH("network_bluetooth"),

        /**
         * The network is connected using a 2G connection.
         */
        NETWORK_2G("network_2G"),

        /**
         * The network is connected using a 3G connection.
         */
        NETWORK_3G("network_3G"),

        /**
         * The network is connected using a 4G connection.
         */
        NETWORK_4G("network_4G"),

        /**
         * The network is connected using a 5G connection.
         */
        NETWORK_5G("network_5G"),

        /**
         * The network is connected using a cellular connection with a unknown technology.
         */
        NETWORK_MOBILE_OTHER("network_mobile_other"),

        /**
         * The network is connected using a cellular connection.
         */
        NETWORK_CELLULAR("network_cellular"),

        /**
         * The network is connected using an other connection type.
         */
        NETWORK_OTHER("network_other")
        ;

        internal fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        internal companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            fun fromJson(jsonString: String): Connectivity {
                try {
                    return values().first {
                        it.jsonValue == jsonString
                    }
                } catch (e: NoSuchElementException) {
                    throw JsonParseException(
                        "Unable to parse json into type NetworkInfo.Connectivity",
                        e
                    )
                }
            }
        }
    }
}
