/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.Deserializer
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal class RumEventMetaDeserializer(
    private val internalLogger: InternalLogger
) : Deserializer<ByteArray, Any> {
    override fun deserialize(model: ByteArray): Any? {
        if (model.isEmpty()) return null

        return try {
            RumEventMeta.fromJson(model.toJson())
        } catch (@Suppress("TooGenericExceptionCaught") e: NullPointerException) {
            logException(e)
            null
        } catch (e: IllegalStateException) {
            logException(e)
            null
        } catch (e: ClassCastException) {
            logException(e)
            null
        } catch (e: JsonParseException) {
            logException(e)
            null
        } catch (e: NumberFormatException) {
            logException(e)
            null
        }
    }

    private fun ByteArray.toJson(): JsonObject {
        @Suppress("UnsafeThirdPartyFunctionCall") // JsonParseException is handled in the caller
        return JsonParser.parseString(
            String(this, Charsets.UTF_8)
        ).asJsonObject
    }

    private fun logException(e: Exception) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            { DESERIALIZATION_ERROR },
            e
        )
    }

    companion object {
        const val DESERIALIZATION_ERROR = "Failed to deserialize RUM event meta"
    }
}
