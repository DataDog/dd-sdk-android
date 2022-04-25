/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlin.jvm.Throws

internal data class EventMeta(val eventSize: Int) {

    val asBytes: ByteArray
        get() {
            return JsonObject()
                .apply {
                    addProperty(EVENT_SIZE_KEY, eventSize)
                }
                .toString()
                .toByteArray(Charsets.UTF_8)
        }

    companion object {

        private const val EVENT_SIZE_KEY = "ev_size"

        @Throws(JsonParseException::class)
        @Suppress("ThrowingInternalException", "TooGenericExceptionCaught")
        fun fromBytes(metaBytes: ByteArray): EventMeta {
            return try {
                @Suppress("UnsafeThirdPartyFunctionCall") // there is Throws annotation
                val json = JsonParser.parseString(String(metaBytes, Charsets.UTF_8))
                    .asJsonObject
                EventMeta(
                    eventSize = json.get(EVENT_SIZE_KEY).asInt
                )
            } catch (e: IllegalStateException) {
                throw JsonParseException(e)
            } catch (e: ClassCastException) {
                throw JsonParseException(e)
            } catch (e: NumberFormatException) {
                throw JsonParseException(e)
            } catch (e: NullPointerException) {
                throw JsonParseException(e)
            }
        }
    }
}
