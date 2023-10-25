/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.api.InternalLogger
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.util.Locale
import kotlin.jvm.Throws

internal sealed class RumEventMeta {

    abstract val type: String

    open fun toJson(): JsonObject {
        val model = JsonObject()

        model.addProperty(TYPE_KEY, type)

        return model
    }

    data class View(
        val viewId: String,
        val documentVersion: Long
    ) : RumEventMeta() {

        override val type: String = VIEW_TYPE_VALUE

        override fun toJson(): JsonObject {
            val model = super.toJson()

            model.addProperty(VIEW_ID_KEY, viewId)
            model.addProperty(DOCUMENT_VERSION_KEY, documentVersion)

            return model
        }
    }

    companion object {

        private const val UNKNOWN_RUM_EVENT_META_TYPE_ERROR = "Unknown RUM event meta type value [%s]"
        private const val UNABLE_TO_PARSE_JSON_INTO_META = "Unable to parse json into RUM event meta"

        const val TYPE_KEY = "type"
        const val VIEW_TYPE_VALUE = "view"
        const val VIEW_ID_KEY = "viewId"
        const val DOCUMENT_VERSION_KEY = "documentVersion"

        @Suppress("ThrowsCount", "ThrowingInternalException")
        @Throws(JsonParseException::class)
        fun fromJson(jsonString: String, internalLogger: InternalLogger): RumEventMeta? {
            return try {
                @Suppress("UnsafeThirdPartyFunctionCall") // JsonParseException is handled by the caller
                val model = JsonParser.parseString(jsonString).asJsonObject
                when (val type = model.get(TYPE_KEY).asString) {
                    VIEW_TYPE_VALUE -> {
                        val viewId = model.get(VIEW_ID_KEY).asString
                        val docVersion = model.get(DOCUMENT_VERSION_KEY).asLong

                        View(viewId, docVersion)
                    }

                    else -> {
                        internalLogger.log(
                            InternalLogger.Level.ERROR,
                            InternalLogger.Target.USER,
                            { UNKNOWN_RUM_EVENT_META_TYPE_ERROR.format(Locale.US, type) }
                        )
                        null
                    }
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: NullPointerException) {
                throw JsonParseException(UNABLE_TO_PARSE_JSON_INTO_META, e)
            } catch (e: ClassCastException) {
                throw JsonParseException(UNABLE_TO_PARSE_JSON_INTO_META, e)
            } catch (e: IllegalStateException) {
                throw JsonParseException(UNABLE_TO_PARSE_JSON_INTO_META, e)
            } catch (e: NumberFormatException) {
                throw JsonParseException(UNABLE_TO_PARSE_JSON_INTO_META, e)
            }
        }
    }
}
