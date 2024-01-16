/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.recorder.SessionReplayResource
import com.datadog.android.sessionreplay.internal.recorder.SessionReplayResourceContext
import com.google.gson.JsonIOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

internal class ResourceRequestBodyFactory {
    fun create(
        resources: List<SessionReplayResource>,
        internalLogger: InternalLogger
    ): RequestBody {
        return buildResourceRequestBody(resources, internalLogger)
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
    private fun buildResourceRequestBody(
        resources: List<SessionReplayResource>,
        internalLogger: InternalLogger
    ): RequestBody {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
        resources.forEach {
            val filename = it.identifier
            val data = it.data

            builder.addFormDataPart(
                name = "image",
                filename,
                data.toRequestBody(CONTENT_TYPE_IMAGE.toMediaTypeOrNull())
            )
        }

        if (resources.isNotEmpty()) {
            val context = resources.first().context
            val data = convertToJson(context, internalLogger)
            val requestBody = data.toRequestBody(CONTENT_TYPE_APPLICATION.toMediaTypeOrNull())

            builder.addFormDataPart(
                name = "resource",
                filename = "blob",
                requestBody
            )
        }

        return builder.build()
    }

    @VisibleForTesting
    internal fun convertToJson(context: SessionReplayResourceContext, internalLogger: InternalLogger): String {
        return try {
            context.toJson()
        } catch (e: JsonIOException) {
            internalLogger.log(
                target = InternalLogger.Target.TELEMETRY,
                level = InternalLogger.Level.WARN,
                messageBuilder = { CONVERT_TO_JSON_ERROR_MESSAGE },
                throwable = e
            )
            ""
        }
    }

    companion object {
        internal const val CONTENT_TYPE_IMAGE = "image/png"
        internal const val CONTENT_TYPE_APPLICATION = "application/json"

        @VisibleForTesting
        internal const val CONVERT_TO_JSON_ERROR_MESSAGE = "Unable to convert context to json"
    }
}
