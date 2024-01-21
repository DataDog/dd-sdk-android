/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.api.storage.RawBatchEvent
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

internal class ResourceRequestBodyFactory {
    @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
    internal fun create(
        applicationId: String,
        resources: List<RawBatchEvent>
    ): RequestBody {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        addResourcesSection(builder, resources)
        addApplicationIdSection(builder, applicationId)
        return builder.build()
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
    private fun addResourcesSection(builder: MultipartBody.Builder, resources: List<RawBatchEvent>) {
        resources.forEach {
            val filename = it.metadata.toString()
            val data = it.data

            builder.addFormDataPart(
                name = NAME_IMAGE,
                filename,
                data.toRequestBody(CONTENT_TYPE_IMAGE)
            )
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
    private fun addApplicationIdSection(builder: MultipartBody.Builder, applicationId: String) {
        val data = JsonObject()
        data.addProperty(APPLICATION_ID_KEY, applicationId)
        data.addProperty(TYPE_KEY, TYPE_RESOURCE)
        val applicationIdSection = data.toString().toRequestBody(CONTENT_TYPE_APPLICATION)

        builder.addFormDataPart(
            name = NAME_RESOURCE,
            filename = FILENAME_BLOB,
            applicationIdSection
        )
    }

    companion object {
        @Suppress("UnsafeThirdPartyFunctionCall") // if malformed returns null
        internal val CONTENT_TYPE_IMAGE = "image/png".toMediaTypeOrNull()

        @Suppress("UnsafeThirdPartyFunctionCall") // if malformed returns null
        internal val CONTENT_TYPE_APPLICATION = "application/json".toMediaTypeOrNull()
        private const val APPLICATION_ID_KEY: String = "application_id"
        private const val TYPE_KEY: String = "type"
        private const val TYPE_RESOURCE: String = "resource"
        private const val NAME_IMAGE: String = "image"
        private const val NAME_RESOURCE: String = "resource"
        private const val FILENAME_BLOB: String = "blob"
    }
}
