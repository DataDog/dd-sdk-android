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

// TODO RUMM-2172 use meta.
//  For now it has nothing (fake property just because data class requires it)
@Suppress("UnusedPrivateMember")
internal data class EventMeta(private val fake: String = "") {

    val asBytes: ByteArray
        get() {
            return JsonObject()
                .toString()
                .toByteArray(Charsets.UTF_8)
        }

    companion object {

        @Throws(JsonParseException::class)
        @Suppress("ThrowingInternalException", "TooGenericExceptionCaught", "ThrowsCount")
        fun fromBytes(metaBytes: ByteArray): EventMeta {
            return try {
                // there is Throws annotation
                @Suppress("UnsafeThirdPartyFunctionCall", "UNUSED_VARIABLE")
                val json = JsonParser.parseString(String(metaBytes, Charsets.UTF_8))
                    .asJsonObject
                EventMeta()
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
