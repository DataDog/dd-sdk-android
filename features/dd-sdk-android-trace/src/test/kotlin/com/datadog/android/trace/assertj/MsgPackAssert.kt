/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.assertj

import com.datadog.tools.unit.assertj.JsonObjectAssert
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.msgpack.core.MessagePack

internal class MsgPackAssert private constructor(actual: JsonObject) :
    JsonObjectAssert(actual, lenientKeys = true) {

    companion object {
        fun assertThat(bytes: ByteArray): MsgPackAssert {
            val json = MessagePack.newDefaultUnpacker(bytes).use { it.unpackValue().toJson() }
            return MsgPackAssert(JsonParser.parseString(json).asJsonObject)
        }
    }
}
