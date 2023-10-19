/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.google.gson.JsonObject
import java.util.Locale
import kotlin.jvm.Throws

internal sealed class RumEventMeta {

    abstract val type: String

    open fun toJson(): JsonObject {
        val model = JsonObject()

        model.addProperty(TYPE_KEY, type)

        return model
    }

    companion object {

        private const val UNKNOWN_RUM_EVENT_META_TYPE_ERROR = "Unknown RUM event meta type value [%s]"

        const val TYPE_KEY = "type"
        const val VIEW_TYPE_VALUE = "view"
        const val VIEW_ID_KEY = "viewId"
        const val DOCUMENT_VERSION_KEY = "documentVersion"

        @Throws(
            NullPointerException::class,
            ClassCastException::class,
            IllegalStateException::class,
            NumberFormatException::class
        )
        fun fromJson(model: JsonObject): RumEventMeta {
            return when (val type = model.get(TYPE_KEY).asString) {
                VIEW_TYPE_VALUE -> {
                    val viewId = model.get(VIEW_ID_KEY).asString
                    val docVersion = model.get(DOCUMENT_VERSION_KEY).asLong

                    View(viewId, docVersion)
                }

                else ->
                    @Suppress("ThrowingInternalException")
                    throw IllegalStateException(
                        UNKNOWN_RUM_EVENT_META_TYPE_ERROR.format(
                            Locale.US,
                            type
                        )
                    )
            }
        }
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
}
