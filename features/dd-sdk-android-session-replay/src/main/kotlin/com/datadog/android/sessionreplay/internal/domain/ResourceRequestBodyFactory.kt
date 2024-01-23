/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.internal.exception.InvalidPayloadFormatException
import com.datadog.android.sessionreplay.internal.utils.MiscUtils
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.jvm.Throws

internal class ResourceRequestBodyFactory {

    @Throws(InvalidPayloadFormatException::class)
    internal fun create(
        resources: List<RawBatchEvent>
    ): RequestBody {
        @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        addResourcesSection(builder, resources)
        addApplicationIdSection(builder, resources)
        @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
        return builder.build()
    }

    private fun getApplicationId(resources: List<RawBatchEvent>): String {
        val resourcesMetaData: List<JsonObject> = resources.mapNotNull {
            MiscUtils.safeDeserializeToJsonObject(it.metadata)
        }

        val uniqueApplicationIds = mutableSetOf<String>()
        resourcesMetaData.forEach {
            val applicationId = it.get(APPLICATION_ID_KEY)?.asString

            if (applicationId != null) {
                uniqueApplicationIds.add(applicationId)
            }
        }

        if (uniqueApplicationIds.size == 0) {
            @Suppress("ThrowingInternalException")
            throw InvalidPayloadFormatException(UNABLE_GET_APPLICATION_ID_ERROR)
        }

        if (uniqueApplicationIds.size > 1) {
            @Suppress("ThrowingInternalException")
            throw InvalidPayloadFormatException(MULTIPLE_APPLICATION_ID_ERROR)
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // collection is not empty at the point
        return uniqueApplicationIds.first()
    }

    private fun addResourcesSection(builder: MultipartBody.Builder, resources: List<RawBatchEvent>) {
        resources.forEach {
            val filename = getFilename(it)

            val data = it.data

            @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
            builder.addFormDataPart(
                name = NAME_IMAGE,
                filename,
                data.toRequestBody(CONTENT_TYPE_IMAGE)
            )
        }
    }

    private fun getFilename(rawBatchEvent: RawBatchEvent): String? {
        val metadataObject = MiscUtils.safeDeserializeToJsonObject(rawBatchEvent.metadata)
            ?: return null

        val filename = safeGetFilenameFromMetadata(metadataObject)

        if (filename == null) {
            @Suppress("ThrowingInternalException")
            throw InvalidPayloadFormatException(DESERIALIZE_METADATA_ERROR)
        } else {
            return filename
        }
    }

    private fun addApplicationIdSection(builder: MultipartBody.Builder, resources: List<RawBatchEvent>) {
        val applicationId = getApplicationId(resources)

        val data = JsonObject()
        data.addProperty(APPLICATION_ID_KEY, applicationId)
        data.addProperty(TYPE_KEY, TYPE_RESOURCE)
        @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
        val applicationIdSection = data.toString().toRequestBody(CONTENT_TYPE_APPLICATION)

        @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
        builder.addFormDataPart(
            name = NAME_RESOURCE,
            filename = FILENAME_BLOB,
            applicationIdSection
        )
    }

    @Suppress("SwallowedException")
    private fun safeGetFilenameFromMetadata(metadataObject: JsonObject): String? {
        return try {
            metadataObject.get(FILENAME_KEY)?.asString
        } catch (e: ClassCastException) {
            null
        } catch (e: IllegalStateException) {
            null
        }
    }

    companion object {
        @Suppress("UnsafeThirdPartyFunctionCall") // if malformed returns null
        internal val CONTENT_TYPE_IMAGE = "image/png".toMediaTypeOrNull()

        @Suppress("UnsafeThirdPartyFunctionCall") // if malformed returns null
        internal val CONTENT_TYPE_APPLICATION = "application/json".toMediaTypeOrNull()

        internal const val APPLICATION_ID_KEY = "application_id"
        internal const val FILENAME_KEY = "filename"
        internal const val TYPE_KEY = "type"
        internal const val TYPE_RESOURCE = "resource"
        internal const val NAME_IMAGE = "image"
        internal const val NAME_RESOURCE = "resource"
        internal const val FILENAME_BLOB = "blob"

        internal const val MULTIPLE_APPLICATION_ID_ERROR =
            "There were multiple applicationIds associated with the resources"
        internal const val UNABLE_GET_APPLICATION_ID_ERROR =
            "Unable to get the applicationId for the resources"
        internal const val DESERIALIZE_METADATA_ERROR =
            "Error deserializing resource metadata"
    }
}
